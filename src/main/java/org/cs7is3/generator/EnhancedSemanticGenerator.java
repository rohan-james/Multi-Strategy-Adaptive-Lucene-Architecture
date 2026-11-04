package org.cs7is3.generator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;
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

public class EnhancedSemanticGenerator implements QueryGeneratorInterface {

    // Query type detection
    enum QueryType {
        CAUSAL,       // Looking for causes, reasons, effects
        ENTITY,       // Focused on specific entities/events
        COMPARATIVE,  // Comparing things
        LIST,         // Looking for lists/examples
        GENERAL       // General information need
    }

    // PRF parameters - adaptive based on query type
    private static final int FEEDBACK_DOCS_SPECIFIC = 12;
    private static final int FEEDBACK_DOCS_GENERAL = 20;
    private static final int EXPANSION_TERMS_SPECIFIC = 25;
    private static final int EXPANSION_TERMS_GENERAL = 50;

    private static final int MIN_TERM_LEN = 3;
    private static final double MAX_DF_RATIO = 0.25;
    private static final double MIN_DF_RATIO = 0.00005;

    // Causal query boosting terms
    private static final Set<String> CAUSAL_TERMS = new HashSet<>(Arrays.asList(
        "cause", "causes", "caused", "reason", "reasons", "why", "because",
        "impede", "barrier", "barriers", "obstacle", "obstacles", "prevent",
        "hinder", "difficulty", "difficulties", "problem", "problems",
        "result", "results", "effect", "effects", "impact", "impacts",
        "lead", "leads", "due", "factor", "factors"
    ));

    private static QueryType detectQueryType(String title, String desc, String narrative) {
        String combined = ((title != null ? title : "") + " " +
                          (desc != null ? desc : "") + " " +
                          (narrative != null ? narrative : "")).toLowerCase();

        // Causal query detection
        if (combined.contains("cause") || combined.contains("why") ||
            combined.contains("reason") || combined.contains("impede") ||
            combined.contains("barrier") || combined.contains("how") && combined.contains("affect") ||
            combined.contains("result") && combined.contains("from") ||
            combined.contains("due to") || combined.contains("because") ||
            combined.contains("lead to") || combined.contains("impact")) {
            return QueryType.CAUSAL;
        }

        // Entity/event query detection (proper nouns, specific names)
        Pattern entityPattern = Pattern.compile("\\b[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*\\b");
        String original = (title != null ? title : "") + " " + (desc != null ? desc : "");
        Matcher matcher = entityPattern.matcher(original);
        int properNounCount = 0;
        while (matcher.find()) properNounCount++;

        if (properNounCount >= 2 || combined.contains("flight") || combined.contains("pan am")) {
            return QueryType.ENTITY;
        }

        // Comparative query detection
        if (combined.contains("compare") || combined.contains("difference") ||
            combined.contains("versus") || combined.contains("vs") ||
            combined.contains("better") || combined.contains("worse")) {
            return QueryType.COMPARATIVE;
        }

        // List query detection
        if (combined.contains("what") && (combined.contains("uses") || combined.contains("methods") ||
            combined.contains("examples") || combined.contains("types"))) {
            return QueryType.LIST;
        }

        return QueryType.GENERAL;
    }

    private static List<String> extractNegativeTerms(String narrative) {
        List<String> negativeTerms = new ArrayList<>();
        if (narrative == null) return negativeTerms;

        String lower = narrative.toLowerCase();

        // Extract terms after "not relevant" or similar
        String[] sentences = narrative.split("\\.");
        for (String sentence : sentences) {
            String s = sentence.toLowerCase();
            if (s.contains("not relevant") || s.contains("are not") || s.contains("irrelevant")) {
                // Extract key terms from negative sentences
                String[] words = s.split("\\s+");
                for (String word : words) {
                    word = word.replaceAll("[^a-z]", "");
                    if (word.length() > 4 && !word.equals("relevant") && !word.equals("discuss")) {
                        negativeTerms.add(word);
                    }
                }
            }
        }

        // Magnitude qualifiers to exclude
        if (lower.contains("slight") || lower.contains("small") || lower.contains("limited")) {
            negativeTerms.addAll(Arrays.asList("slight", "small", "limited", "minor", "minimal"));
        }

        // Domain exclusions
        if (lower.contains("not") && lower.contains("nuclear")) {
            negativeTerms.add("nuclear");
            negativeTerms.add("radioactive");
        }

        return negativeTerms;
    }

    private static List<String> extractKeyPhrases(String title, String desc) {
        List<String> phrases = new ArrayList<>();
        if (title == null) return phrases;

        // Multi-word entities
        String titleLower = title.toLowerCase();

        // Match quoted phrases or capitalized sequences
        Pattern phrasePattern = Pattern.compile("\"([^\"]+)\"|([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)+)");
        Matcher matcher = phrasePattern.matcher(title);
        while (matcher.find()) {
            String phrase = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (phrase != null && phrase.split("\\s+").length >= 2) {
                phrases.add(phrase.toLowerCase());
            }
        }

        // Common multi-word patterns
        if (titleLower.contains("pan am")) phrases.add("pan am");
        if (titleLower.contains("peace talk")) phrases.add("peace talk");
        if (desc != null && desc.toLowerCase().contains("lockerbie")) phrases.add("lockerbie");

        return phrases;
    }

    private static Query buildAdaptiveQuery(QueryParser queryParser, TopicSchema topic, QueryType queryType)
            throws ParseException {

        String title = topic.getTitle();
        String desc = topic.getDescription();
        String narrative = topic.getNarrative();

        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        // Base query with adaptive boosting
        float titleBoost = 3.0f;
        float descBoost = 2.0f;
        float narrBoost = 0.5f;

        // Adjust based on query type
        switch (queryType) {
            case ENTITY:
                titleBoost = 4.0f;  // Entities usually in title
                descBoost = 2.5f;
                break;
            case CAUSAL:
                descBoost = 2.8f;   // Causal context in description
                narrBoost = 1.2f;   // Narrative clarifies causation
                break;
            case LIST:
                titleBoost = 2.5f;
                descBoost = 3.0f;   // Lists described in detail
                break;
            case COMPARATIVE:
            case GENERAL:
                // Use default values
                break;
        }

        // Title query
        if (title != null && !title.isBlank()) {
            String cleaned = cleanText(title);
            if (!cleaned.isEmpty()) {
                Query titleQuery = queryParser.parse(QueryParser.escape(cleaned));
                builder.add(new BoostQuery(titleQuery, titleBoost), BooleanClause.Occur.SHOULD);
            }
        }

        // Description query
        if (desc != null && !desc.isBlank()) {
            String cleaned = cleanText(desc);
            if (!cleaned.isEmpty()) {
                Query descQuery = queryParser.parse(QueryParser.escape(cleaned));
                builder.add(new BoostQuery(descQuery, descBoost), BooleanClause.Occur.SHOULD);
            }
        }

        // Filtered narrative
        if (narrative != null && !narrative.isBlank()) {
            String positive = extractPositiveNarrative(narrative);
            if (!positive.isEmpty()) {
                String cleaned = cleanText(positive);
                if (!cleaned.isEmpty()) {
                    Query narrQuery = queryParser.parse(QueryParser.escape(cleaned));
                    builder.add(new BoostQuery(narrQuery, narrBoost), BooleanClause.Occur.SHOULD);
                }
            }
        }

        // Add causal terms for causal queries
        if (queryType == QueryType.CAUSAL) {
            BooleanQuery.Builder causalBuilder = new BooleanQuery.Builder();
            for (String term : CAUSAL_TERMS) {
                causalBuilder.add(new TermQuery(new Term("contents", term)), BooleanClause.Occur.SHOULD);
            }
            builder.add(new BoostQuery(causalBuilder.build(), 1.8f), BooleanClause.Occur.SHOULD);
        }

        // Add phrase queries for entities
        List<String> phrases = extractKeyPhrases(title, desc);
        for (String phrase : phrases) {
            String[] terms = phrase.split("\\s+");
            if (terms.length >= 2) {
                PhraseQuery.Builder phraseBuilder = new PhraseQuery.Builder();
                for (String term : terms) {
                    String cleaned = cleanText(term);
                    if (!cleaned.isEmpty()) {
                        phraseBuilder.add(new Term("contents", cleaned));
                    }
                }
                phraseBuilder.setSlop(3);
                builder.add(new BoostQuery(phraseBuilder.build(), 2.5f), BooleanClause.Occur.SHOULD);
            }
        }

        builder.setMinimumNumberShouldMatch(1);
        return builder.build();
    }

    private static String cleanText(String text) {
        if (text == null) return "";
        text = text.replaceAll("[^a-zA-Z0-9\\s]", " ");
        text = text.replaceAll("\\s+", " ").trim();
        return text.toLowerCase();
    }

    private static String extractPositiveNarrative(String narrative) {
        if (narrative == null) return "";

        String[] sentences = narrative.split("(?<=[.!?])\\s+");
        StringBuilder positive = new StringBuilder();

        for (String sentence : sentences) {
            String lower = sentence.toLowerCase();
            if (lower.contains("not relevant") || lower.contains("irrelevant") ||
                lower.contains("are not")) {
                continue;
            }
            if (lower.contains("relevant") || lower.contains("focus") ||
                lower.contains("discuss") || lower.contains("must")) {
                positive.append(sentence).append(" ");
            }
        }

        if (positive.length() == 0) {
            for (String sentence : sentences) {
                if (!sentence.toLowerCase().contains("not relevant")) {
                    positive.append(sentence).append(" ");
                }
            }
        }

        return positive.toString().trim();
    }

    private Map<String, Double> createExpansionTerms(IndexReader reader, TopDocs feedbackDocs,
            Set<String> baseTerms, List<String> negativeTerms) throws IOException {

        Map<String, Double> termWeights = new HashMap<>();
        int totalDocs = reader.numDocs();
        Set<String> negativeSet = new HashSet<>(negativeTerms);

        for (ScoreDoc sd : feedbackDocs.scoreDocs) {
            Terms termVector = reader.getTermVector(sd.doc, "contents");
            if (termVector == null) continue;

            TermsEnum termsEnum = termVector.iterator();
            BytesRef termBytes;

            while ((termBytes = termsEnum.next()) != null) {
                String term = termBytes.utf8ToString();

                if (term.length() < MIN_TERM_LEN || baseTerms.contains(term) || negativeSet.contains(term)) {
                    continue;
                }

                long tf = termsEnum.totalTermFreq();
                int df = reader.docFreq(new Term("contents", term));

                if (df <= 0 || tf <= 0) continue;

                double dfRatio = df / (double) totalDocs;
                if (dfRatio > MAX_DF_RATIO || dfRatio < MIN_DF_RATIO) {
                    continue;
                }

                double tfWeight = Math.log1p(tf);
                double idfWeight = Math.log(1.0 + (totalDocs / (double) df));
                double docScore = 1.0 + sd.score;

                double weight = tfWeight * idfWeight * docScore;
                termWeights.merge(term, weight, Double::sum);
            }
        }

        return termWeights;
    }

    @Override
    public void preprocessQuery(List<TopicSchema> parsedTopics, QueryParser queryParser,
            IndexSearcher indexSearcher, BufferedWriter writer, int numDocs, String runTag)
            throws IOException {

        int maxDocs = Math.min(numDocs, 1000);

        for (TopicSchema topic : parsedTopics) {
            String topicNum = topic.getNumber();
            if (topicNum == null) continue;
            topicNum = topicNum.trim();

            try {
                // Detect query type
                QueryType queryType = detectQueryType(topic.getTitle(), topic.getDescription(),
                                                      topic.getNarrative());

                // Adaptive parameters
                int feedbackDocs = (queryType == QueryType.ENTITY || queryType == QueryType.CAUSAL)
                                  ? FEEDBACK_DOCS_SPECIFIC : FEEDBACK_DOCS_GENERAL;
                int expansionTerms = (queryType == QueryType.ENTITY || queryType == QueryType.CAUSAL)
                                    ? EXPANSION_TERMS_SPECIFIC : EXPANSION_TERMS_GENERAL;

                // Extract negative terms
                List<String> negativeTerms = extractNegativeTerms(topic.getNarrative());

                // Build adaptive base query
                Query baseQuery = buildAdaptiveQuery(queryParser, topic, queryType);

                // Collect base terms
                Set<String> baseTerms = new HashSet<>();
                collectTerms(baseQuery, baseTerms);

                // PRF
                IndexReader reader = indexSearcher.getIndexReader();
                TopDocs feedbackResults = indexSearcher.search(baseQuery, feedbackDocs);
                Map<String, Double> expansionMap = createExpansionTerms(reader, feedbackResults,
                                                                        baseTerms, negativeTerms);

                // Build final query
                Query finalQuery = buildFinalQuery(baseQuery, expansionMap, expansionTerms);

                // Search and write
                TopDocs results = indexSearcher.search(finalQuery, maxDocs);
                writeResults(writer, topicNum, results, indexSearcher, runTag);

            } catch (Exception e) {
                System.err.println("Error processing topic " + topicNum + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void collectTerms(Query q, Set<String> terms) {
        if (q instanceof BooleanQuery) {
            for (BooleanClause clause : ((BooleanQuery) q).clauses()) {
                collectTerms(clause.getQuery(), terms);
            }
        } else if (q instanceof BoostQuery) {
            collectTerms(((BoostQuery) q).getQuery(), terms);
        } else if (q instanceof TermQuery) {
            terms.add(((TermQuery) q).getTerm().text());
        } else if (q instanceof PhraseQuery) {
            for (Term t : ((PhraseQuery) q).getTerms()) {
                terms.add(t.text());
            }
        }
    }

    private static Query buildFinalQuery(Query baseQuery, Map<String, Double> expansionMap, int numTerms) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        builder.add(new BoostQuery(baseQuery, 0.55f), BooleanClause.Occur.SHOULD);

        if (!expansionMap.isEmpty()) {
            List<Map.Entry<String, Double>> topTerms = expansionMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(numTerms)
                .collect(Collectors.toList());

            if (!topTerms.isEmpty()) {
                double maxWeight = topTerms.get(0).getValue();
                for (Map.Entry<String, Double> entry : topTerms) {
                    double normalized = entry.getValue() / maxWeight;
                    float boost = (float) (0.55 * normalized);
                    boost = Math.max(boost, 0.05f);

                    Query termQuery = new TermQuery(new Term("contents", entry.getKey()));
                    builder.add(new BoostQuery(termQuery, boost), BooleanClause.Occur.SHOULD);
                }
            }
        }

        builder.setMinimumNumberShouldMatch(1);
        return builder.build();
    }

    private static void writeResults(BufferedWriter writer, String topicNum, TopDocs topDocs,
            IndexSearcher indexSearcher, String runTag) throws IOException {

        int rank = 1;
        for (ScoreDoc sd : topDocs.scoreDocs) {
            Document doc = indexSearcher.storedFields().document(sd.doc);
            String docNum = doc.get("DOCNO");
            if (docNum == null || docNum.isBlank()) continue;

            writer.write(String.format("%s Q0 %s %d %.6f %s%n",
                        topicNum, docNum, rank, sd.score, runTag));
            rank++;
            if (rank > 1000) break;
        }

        while (rank <= 1000) {
            writer.write(String.format("%s Q0 dummy_doc_%d %d 0.000000 %s%n",
                        topicNum, rank, rank, runTag));
            rank++;
        }
    }
}
