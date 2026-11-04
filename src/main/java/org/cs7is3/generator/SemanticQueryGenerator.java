package org.cs7is3.generator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.util.BytesRef;
import org.cs7is3.schemas.TopicSchema;

public class SemanticQueryGenerator implements QueryGeneratorInterface {
    // Enhanced parameters for semantic search
    private static final int FEEDBACK_DOCS = 20;
    private static final int EXPANSION_TERMS = 60;
    private static final int MIN_TERM_LEN = 3;
    private static final double MAX_DF_RATIO = 0.22;
    private static final double MIN_DF_RATIO = 0.00008;

    // Multi-stage query weights
    private static final float BASE_QUERY_WEIGHT = 0.50f;
    private static final float EXPANSION_WEIGHT = 0.30f;
    private static final float PROXIMITY_WEIGHT = 0.20f;

    // Field boosting
    private static final float TITLE_BOOST = 4.0f;
    private static final float DESC_BOOST = 2.5f;
    private static final float NARR_BOOST = 1.0f;

    // Proximity settings
    private static final int PHRASE_SLOP = 5;

    private static String preprocessText(String content) {
        if (content == null) return "";
        String pre = content.replaceAll("[^a-zA-Z0-9\\s.,?!-]", " ");
        pre = pre.replaceAll("\\s+", " ").trim();
        return pre.toLowerCase();
    }

    private static List<String> extractKeyEntities(String title, String desc) {
        List<String> entities = new ArrayList<>();

        // Combine title and description
        String combined = (title != null ? title : "") + " " + (desc != null ? desc : "");
        combined = combined.toLowerCase();

        // Extract capitalized terms (likely proper nouns) - before lowercasing
        String original = (title != null ? title : "") + " " + (desc != null ? desc : "");
        String[] words = original.split("\\s+");
        for (String word : words) {
            // Capitalized word that's not at sentence start
            if (word.length() > 2 && Character.isUpperCase(word.charAt(0))) {
                String lower = word.toLowerCase().replaceAll("[^a-z]", "");
                if (lower.length() > 2) {
                    entities.add(lower);
                }
            }
        }

        // Multi-word entities (country names, etc.)
        if (combined.contains("germany")) entities.add("germany");
        if (combined.contains("german")) entities.add("german");
        if (combined.contains("ireland")) entities.add("ireland");
        if (combined.contains("irish")) entities.add("irish");
        if (combined.contains("pan am")) { entities.add("pan"); entities.add("am"); }
        if (combined.contains("lockerbie")) entities.add("lockerbie");

        return entities.stream().distinct().collect(Collectors.toList());
    }

    private static Query buildMultiFieldQuery(QueryParser queryParser,
                                               String title,
                                               String desc,
                                               String narrative) throws ParseException {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        boolean hasClause = false;

        // Extract and boost key entities strongly (but not required)
        List<String> keyEntities = extractKeyEntities(title, desc);
        if (!keyEntities.isEmpty()) {
            for (String entity : keyEntities) {
                Query entityQuery = new TermQuery(new Term("contents", entity));
                builder.add(new BoostQuery(entityQuery, 3.0f), BooleanClause.Occur.SHOULD);
                hasClause = true;
            }
        }

        // Title - most important
        if (title != null && !title.isBlank()) {
            String cleaned = preprocessText(title);
            if (!cleaned.isEmpty()) {
                Query titleQuery = createFieldQuery(queryParser, cleaned);
                builder.add(new BoostQuery(titleQuery, TITLE_BOOST), BooleanClause.Occur.SHOULD);
                hasClause = true;
            }
        }

        // Description
        if (desc != null && !desc.isBlank()) {
            String cleaned = preprocessText(desc);
            if (!cleaned.isEmpty()) {
                Query descQuery = createFieldQuery(queryParser, cleaned);
                builder.add(new BoostQuery(descQuery, DESC_BOOST), BooleanClause.Occur.SHOULD);
                hasClause = true;
            }
        }

        // Narrative (filtered)
        if (narrative != null && !narrative.isBlank()) {
            String positiveNarr = extractPositiveNarrative(narrative);
            if (!positiveNarr.isEmpty()) {
                String cleaned = preprocessText(positiveNarr);
                if (!cleaned.isEmpty()) {
                    Query narrQuery = createFieldQuery(queryParser, cleaned);
                    builder.add(new BoostQuery(narrQuery, NARR_BOOST), BooleanClause.Occur.SHOULD);
                    hasClause = true;
                }
            }
        }

        if (!hasClause) {
            throw new ParseException("Empty topic text");
        }

        builder.setMinimumNumberShouldMatch(1);
        return builder.build();
    }

    private static Query createFieldQuery(QueryParser parser, String text) throws ParseException {
        String escaped = QueryParser.escape(text);
        return parser.parse(escaped);
    }

    private static String extractPositiveNarrative(String narrative) {
        if (narrative == null) return "";

        String[] sentences = narrative.split("(?<=[.!?])\\s+");
        StringBuilder positive = new StringBuilder();

        for (String sentence : sentences) {
            String lower = sentence.toLowerCase();
            // Filter out negative relevance indicators
            if (lower.contains("not relevant") ||
                lower.contains("non-relevant") ||
                lower.contains("irrelevant") ||
                lower.contains("are not relevant")) {
                continue;
            }

            // Include sentences with positive indicators
            if (lower.contains("relevant") ||
                lower.contains("must") ||
                lower.contains("should") ||
                lower.contains("describe") ||
                lower.contains("identify") ||
                lower.contains("information about") ||
                lower.contains("report") ||
                lower.contains("discuss")) {
                positive.append(sentence).append(" ");
            }
        }

        if (positive.length() == 0) {
            for (String sentence : sentences) {
                String lower = sentence.toLowerCase();
                if (!lower.contains("not relevant")) {
                    positive.append(sentence).append(" ");
                }
            }
        }

        return positive.toString().trim();
    }

    // Build proximity query for key phrases
    private static Query buildProximityQuery(String title, String desc) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        boolean hasClause = false;

        // Extract key phrases from title
        if (title != null && !title.isBlank()) {
            List<String> titleTerms = extractTerms(title);
            if (titleTerms.size() >= 2) {
                // Create phrase query with slop for title (loose matching)
                PhraseQuery.Builder phraseBuilder = new PhraseQuery.Builder();
                for (String term : titleTerms) {
                    phraseBuilder.add(new Term("contents", term));
                }
                phraseBuilder.setSlop(PHRASE_SLOP);
                builder.add(new BoostQuery(phraseBuilder.build(), 2.5f), BooleanClause.Occur.SHOULD);
                hasClause = true;

                // Add tighter phrase query for exact proximity
                if (titleTerms.size() <= 4) {
                    PhraseQuery.Builder tightBuilder = new PhraseQuery.Builder();
                    for (String term : titleTerms) {
                        tightBuilder.add(new Term("contents", term));
                    }
                    tightBuilder.setSlop(2);
                    builder.add(new BoostQuery(tightBuilder.build(), 3.0f), BooleanClause.Occur.SHOULD);
                }
            }
        }

        // Extract key phrases from description
        if (desc != null && !desc.isBlank()) {
            List<String> descTerms = extractTerms(desc);
            if (descTerms.size() >= 2 && descTerms.size() <= 6) {
                PhraseQuery.Builder phraseBuilder = new PhraseQuery.Builder();
                for (String term : descTerms) {
                    phraseBuilder.add(new Term("contents", term));
                }
                phraseBuilder.setSlop(PHRASE_SLOP + 2);
                builder.add(new BoostQuery(phraseBuilder.build(), 1.2f), BooleanClause.Occur.SHOULD);
                hasClause = true;
            }
        }

        return hasClause ? builder.build() : null;
    }

    private static List<String> extractTerms(String text) {
        if (text == null) return Collections.emptyList();

        String cleaned = preprocessText(text);
        String[] words = cleaned.split("\\s+");

        // Filter to meaningful terms only
        List<String> terms = new ArrayList<>();
        Set<String> stopWords = new HashSet<>(Arrays.asList(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "was", "are", "be", "been"
        ));

        for (String word : words) {
            if (word.length() >= MIN_TERM_LEN && !stopWords.contains(word)) {
                terms.add(word);
            }
        }

        return terms;
    }

    private static Set<String> collectQueryTerms(Query query) {
        Set<String> terms = new HashSet<>();
        collectTermsRecursive(query, terms);
        return terms;
    }

    private static void collectTermsRecursive(Query q, Set<String> acc) {
        if (q instanceof BooleanQuery) {
            for (BooleanClause clause : ((BooleanQuery) q).clauses()) {
                collectTermsRecursive(clause.getQuery(), acc);
            }
        } else if (q instanceof BoostQuery) {
            collectTermsRecursive(((BoostQuery) q).getQuery(), acc);
        } else if (q instanceof TermQuery) {
            acc.add(((TermQuery) q).getTerm().text());
        } else if (q instanceof PhraseQuery) {
            for (Term t : ((PhraseQuery) q).getTerms()) {
                acc.add(t.text());
            }
        }
    }

    // Enhanced term expansion with semantic scoring
    private Map<String, Double> createSemanticExpansion(IndexReader indexReader,
                                                          TopDocs feedbackDocs,
                                                          Set<String> baseTerms) throws IOException {
        Map<String, Double> termWeights = new HashMap<>();
        Map<String, Set<String>> termCooccurrence = new HashMap<>();
        int totalDocs = indexReader.numDocs();

        // First pass: collect term co-occurrences
        for (ScoreDoc sd : feedbackDocs.scoreDocs) {
            Terms termVector = indexReader.getTermVector(sd.doc, "contents");
            if (termVector == null) continue;

            Set<String> docTerms = new HashSet<>();
            TermsEnum termsEnum = termVector.iterator();
            BytesRef termBytes;

            while ((termBytes = termsEnum.next()) != null) {
                String term = termBytes.utf8ToString();
                if (term.length() >= MIN_TERM_LEN && !baseTerms.contains(term)) {
                    docTerms.add(term);
                }
            }

            // Track co-occurrences with base terms
            for (String baseTerm : baseTerms) {
                termCooccurrence.putIfAbsent(baseTerm, new HashSet<>());
                termCooccurrence.get(baseTerm).addAll(docTerms);
            }
        }

        // Second pass: score terms based on co-occurrence and TF-IDF
        for (ScoreDoc sd : feedbackDocs.scoreDocs) {
            Terms termVector = indexReader.getTermVector(sd.doc, "contents");
            if (termVector == null) continue;

            TermsEnum termsEnum = termVector.iterator();
            BytesRef termBytes;

            while ((termBytes = termsEnum.next()) != null) {
                String term = termBytes.utf8ToString();

                if (term.length() < MIN_TERM_LEN || baseTerms.contains(term)) {
                    continue;
                }

                long tf = termsEnum.totalTermFreq();
                int df = indexReader.docFreq(new Term("contents", term));

                if (df <= 0 || tf <= 0) continue;

                double dfRatio = df / (double) totalDocs;
                if (dfRatio > MAX_DF_RATIO || dfRatio < MIN_DF_RATIO) {
                    continue;
                }

                // Calculate semantic score
                double tfWeight = Math.log1p(tf);
                double idfWeight = Math.log(1.0 + (totalDocs / (double) df));
                double docScore = 1.0 + sd.score;

                // Bonus for co-occurring with multiple base terms
                int cooccurrenceCount = 0;
                for (Set<String> cooccurs : termCooccurrence.values()) {
                    if (cooccurs.contains(term)) {
                        cooccurrenceCount++;
                    }
                }
                double semanticBonus = 1.0 + (0.2 * cooccurrenceCount);

                double weight = tfWeight * idfWeight * docScore * semanticBonus;
                termWeights.merge(term, weight, Double::sum);
            }
        }

        return termWeights;
    }

    private static Query buildFinalQuery(Query baseQuery,
                                          Query proximityQuery,
                                          Map<String, Double> expansionTerms,
                                          int numExpansionTerms) {
        BooleanQuery.Builder finalBuilder = new BooleanQuery.Builder();

        // Component 1: Base multi-field query
        finalBuilder.add(new BoostQuery(baseQuery, BASE_QUERY_WEIGHT), BooleanClause.Occur.SHOULD);

        // Component 2: Proximity query (if exists)
        if (proximityQuery != null) {
            finalBuilder.add(new BoostQuery(proximityQuery, PROXIMITY_WEIGHT), BooleanClause.Occur.SHOULD);
        }

        // Component 3: Expansion terms
        if (!expansionTerms.isEmpty()) {
            List<Map.Entry<String, Double>> topTerms = expansionTerms.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(numExpansionTerms)
                .collect(Collectors.toList());

            if (!topTerms.isEmpty()) {
                double maxWeight = topTerms.get(0).getValue();
                BooleanQuery.Builder expansionBuilder = new BooleanQuery.Builder();

                for (Map.Entry<String, Double> entry : topTerms) {
                    String term = entry.getKey();
                    double weight = entry.getValue();
                    double normalized = weight / maxWeight;
                    float termBoost = (float) (EXPANSION_WEIGHT * normalized);
                    termBoost = Math.max(termBoost, 0.05f);

                    Query termQuery = new TermQuery(new Term("contents", term));
                    expansionBuilder.add(new BoostQuery(termQuery, termBoost), BooleanClause.Occur.SHOULD);
                }

                finalBuilder.add(expansionBuilder.build(), BooleanClause.Occur.SHOULD);
            }
        }

        finalBuilder.setMinimumNumberShouldMatch(1);
        return finalBuilder.build();
    }

    private static void writeResults(BufferedWriter writer,
                                      String topicNum,
                                      TopDocs topDocs,
                                      IndexSearcher indexSearcher,
                                      String runTag) throws IOException {
        int rank = 1;
        for (ScoreDoc sd : topDocs.scoreDocs) {
            Document doc = indexSearcher.storedFields().document(sd.doc);
            String docNum = doc.get("DOCNO");
            if (docNum == null || docNum.isBlank()) {
                continue;
            }

            String line = String.format("%s Q0 %s %d %.6f %s",
                    topicNum, docNum, rank, sd.score, runTag);
            writer.write(line);
            writer.newLine();
            rank++;

            if (rank > 1000) break;
        }

        // Fill remaining ranks
        while (rank <= 1000) {
            String dummy = String.format("%s Q0 dummy_doc_%d %d 0.000000 %s",
                    topicNum, rank, rank, runTag);
            writer.write(dummy);
            writer.newLine();
            rank++;
        }
    }

    @Override
    public void preprocessQuery(List<TopicSchema> parsedTopics,
                                 QueryParser queryParser,
                                 IndexSearcher indexSearcher,
                                 BufferedWriter writer,
                                 int numDocs,
                                 String runTag) throws IOException {
        int maxDocs = Math.min(numDocs, 1000);

        for (TopicSchema topic : parsedTopics) {
            String topicNum = topic.getNumber();
            if (topicNum == null) {
                System.err.println("No topic ID found");
                continue;
            }
            topicNum = topicNum.trim();

            String title = topic.getTitle();
            String desc = topic.getDescription();
            String narrative = topic.getNarrative();

            try {
                IndexReader indexReader = indexSearcher.getIndexReader();

                // Build multi-component query
                Query baseQuery = buildMultiFieldQuery(queryParser, title, desc, narrative);
                Query proximityQuery = buildProximityQuery(title, desc);
                Set<String> baseTerms = collectQueryTerms(baseQuery);

                // Get feedback documents
                int feedbackSize = Math.min(FEEDBACK_DOCS, maxDocs);
                TopDocs feedbackDocs = indexSearcher.search(baseQuery, feedbackSize);

                // Create semantic expansion
                Map<String, Double> expansionTerms = createSemanticExpansion(
                    indexReader, feedbackDocs, baseTerms);

                // Build final query
                Query finalQuery = buildFinalQuery(
                    baseQuery, proximityQuery, expansionTerms, EXPANSION_TERMS);

                // Search and write results
                TopDocs finalResults = indexSearcher.search(finalQuery, maxDocs);
                writeResults(writer, topicNum, finalResults, indexSearcher, runTag);

            } catch (ParseException e) {
                System.err.println("Failed to parse topic " + topicNum + ": " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                System.err.println("Error processing topic " + topicNum);
                e.printStackTrace();
            }
        }
    }
}
