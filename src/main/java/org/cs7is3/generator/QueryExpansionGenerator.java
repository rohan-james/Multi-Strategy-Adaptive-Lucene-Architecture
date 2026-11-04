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

public class QueryExpansionGenerator implements QueryGeneratorInterface {
    // Best performing PRF parameters
    private static final int FEEDBACK_DOCS = 18;
    private static final int EXPANSION_TERMS = 42;
    private static final int MIN_TERM_LEN = 3;
    private static final double MAX_DF_RATIO = 0.27;
    private static final double MIN_DF_RATIO = 0.00003;
    private static final float ALPHA_BASE_QUERY = 0.47f;
    private static final float BETA_EXPANSION = 0.63f;

    // Multi-field boosting weights
    private static final float TITLE_FIELD_BOOST = 2.8f;
    private static final float DESC_FIELD_BOOST = 1.8f;
    private static final float NARR_FIELD_BOOST = 0.6f;

    private static String secondaryPreprocessing(String content) {
        if (content == null) {
            return "";
        }
        String pre = content.replaceAll("[^a-zA-Z0-9\\s.,?!]", " ");
        pre = pre.replaceAll("\\s+", " ").trim();
        return pre.toLowerCase();
    }

    //Calculate the boostitng  values based on query length.
    //Short queries need higher title emphasis, long queries may need more balance.
    private static class QueryBoosts {
        float titleBoost;
        float descBoost;
        float narrBoost;
        
        QueryBoosts(int queryTermCount) {
            if (queryTermCount <= 3) {
                // Short queries: title boosted
                titleBoost = 4.0f;
                descBoost = 1.8f;
                narrBoost = 0.6f;
            } else if (queryTermCount <= 8) {
                // Medium queries:original values
                titleBoost = 2.5f;
                descBoost = 1.5f;
                narrBoost = 0.5f;
            } else {
                // Long queries: reduce title, increase description and narrative
                titleBoost = 2.0f;
                descBoost = 1.8f;
                narrBoost = 0.8f;
            }
        }
    }
    
    //Count approximate terms in query text
    private static int countQueryTerms(String title, String desc, String narrative) {
        int count = 0;
        if (title != null && !title.isBlank()) {
            count += title.trim().split("\\s+").length;
        }
        if (desc != null && !desc.isBlank()) {
            count += desc.trim().split("\\s+").length;
        }
        if (narrative != null && !narrative.isBlank()) {
            String positiveNarr = extractPositiveNarrative(narrative);
            if (!positiveNarr.isEmpty()) {
                count += positiveNarr.trim().split("\\s+").length;
            }
        }
        return count;
    }

    private static Query buildBaseQuery(QueryParser queryParser,
            String title,
            String desc,
            String narrative) throws ParseException {

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        boolean hasClause = false;
        
        // Calculate values based on query length
        int termCount = countQueryTerms(title, desc, narrative);
        QueryBoosts boosts = new QueryBoosts(termCount);

        // Title boost varies by query length
        if (title != null && !title.isBlank()) {
            String cleaned = secondaryPreprocessing(title);
            if (!cleaned.isEmpty()) {
                String escaped = QueryParser.escape(cleaned);
                Query titleQuery = queryParser.parse(escaped);
                builder.add(new BoostQuery(titleQuery, TITLE_FIELD_BOOST), BooleanClause.Occur.SHOULD);
                hasClause = true;
            }
        }

        if (desc != null && !desc.isBlank()) {
            String cleaned = secondaryPreprocessing(desc);
            if (!cleaned.isEmpty()) {
                String escaped = QueryParser.escape(cleaned);
                Query descQuery = queryParser.parse(escaped);
                builder.add(new BoostQuery(descQuery, DESC_FIELD_BOOST), BooleanClause.Occur.SHOULD);
                hasClause = true;
            }
        }

        // Extract useful terms from narrative (filter out "not relevant" phrases)
        if (narrative != null && !narrative.isBlank()) {
            String positiveNarr = extractPositiveNarrative(narrative);
            if (!positiveNarr.isEmpty()) {
                String cleaned = secondaryPreprocessing(positiveNarr);
                if (!cleaned.isEmpty()) {
                    String escaped = QueryParser.escape(cleaned);
                    Query narrQuery = queryParser.parse(escaped);
                    builder.add(new BoostQuery(narrQuery, NARR_FIELD_BOOST), BooleanClause.Occur.SHOULD);
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

    // Extract positive relevance signals from narrative, filtering out negative phrases
    private static String extractPositiveNarrative(String narrative) {
        if (narrative == null) return "";

        // Split on sentences
        String[] sentences = narrative.split("(?<=[.!?])\\s+");
        StringBuilder positive = new StringBuilder();

        for (String sentence : sentences) {
            String lower = sentence.toLowerCase();
            // Skip sentences with negative relevance indicators
            if (lower.contains("not relevant") ||
                lower.contains("non-relevant") ||
                lower.contains("irrelevant") ||
                lower.contains("not be relevant") ||
                lower.contains("are not relevant") ||
                lower.contains("is not relevant") ||
                lower.contains("documents that discuss") && lower.contains("are not")) {
                continue;
            }
            // Include sentences with positive relevance indicators or general content
            if (lower.contains("relevant") ||
                lower.contains("must") ||
                lower.contains("should") ||
                lower.contains("include") ||
                lower.contains("discuss") ||
                lower.contains("describe") ||
                lower.contains("focus") ||
                lower.contains("provide") ||
                lower.contains("identify") ||
                lower.contains("information") ||
                lower.contains("report") ||
                lower.contains("cite") ||
                lower.contains("mention") ||
                lower.contains("address")) {
                positive.append(sentence).append(" ");
            }
        }

        // If no sentences matched, include all non-negative sentences
        if (positive.length() == 0) {
            for (String sentence : sentences) {
                String lower = sentence.toLowerCase();
                if (!lower.contains("not relevant") &&
                    !lower.contains("non-relevant") &&
                    !lower.contains("irrelevant")) {
                    positive.append(sentence).append(" ");
                }
            }
        }
        return positive.toString().trim();
    }

    private static Set<String> collectBaseQueryTerms(Query baseQuery) {
        Set<String> termStrings = new HashSet<>();
        collectTermsRecursive(baseQuery, termStrings);
        return termStrings;
    }

    private static void collectTermsRecursive(Query q, Set<String> acc) {
        if (q instanceof BooleanQuery) {
            for (BooleanClause clause : ((BooleanQuery) q).clauses()) {
                collectTermsRecursive(clause.getQuery(), acc);
            }
        } else if (q instanceof BoostQuery) {
            collectTermsRecursive(((BoostQuery) q).getQuery(), acc);
        } else if (q instanceof TermQuery) {
            Term t = ((TermQuery) q).getTerm();
            acc.add(t.text());
        }
    }

    private Map<String, Double> createExpansionTerms(IndexReader indexReader,
            TopDocs feedbackDocs,
            Set<String> baseQueryTerms) throws IOException {

        Map<String, Double> termWeights = new HashMap<>();
        int totalDocs = indexReader.numDocs();

        for (ScoreDoc sd : feedbackDocs.scoreDocs) {

            Terms termVector = indexReader.getTermVector(sd.doc, "contents");
            if (termVector == null) {
                continue;
            }

            TermsEnum termsEnum = termVector.iterator();
            BytesRef termBytes;

            while ((termBytes = termsEnum.next()) != null) {
                String term = termBytes.utf8ToString();

                if (term.length() < MIN_TERM_LEN) {
                    continue;
                }

                if (baseQueryTerms.contains(term)) {
                    continue;
                }

                long tf = termsEnum.totalTermFreq();
                int df = indexReader.docFreq(new Term("contents", term));

                if (df <= 0 || tf <= 0) {
                    continue;
                }

                double dfRatio = df / (double) totalDocs;
                if (dfRatio > MAX_DF_RATIO || dfRatio < MIN_DF_RATIO) {
                    continue;
                }

                double tfWeight = Math.log1p(tf);
                double idfWeight = Math.log(1.0 + (totalDocs / (double) df));
                double docScoreWeight = 1.0 + sd.score;

                double weight = tfWeight * idfWeight * docScoreWeight;
                termWeights.merge(term, weight, Double::sum);
            }
        }

        return termWeights;
    }

    private static Query buildExpansionQuery(Query baseQuery,
            Map<String, Double> expansionMap,
            int numTerms) {

        if (expansionMap.isEmpty()) {
            return baseQuery;
        }

        BooleanQuery.Builder builder = new BooleanQuery.Builder();

        // Rocchio-style: original query with alpha weight
        BoostQuery boostedBase = new BoostQuery(baseQuery, ALPHA_BASE_QUERY);
        builder.add(boostedBase, BooleanClause.Occur.SHOULD);

        List<Map.Entry<String, Double>> topTerms = expansionMap.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(numTerms)
                .collect(Collectors.toList());

        if (topTerms.isEmpty()) {
            return baseQuery;
        }

        double maxWeight = topTerms.get(0).getValue();

        // Rocchio-style: expansion terms with beta weight, normalized
        for (Map.Entry<String, Double> entry : topTerms) {
            String term = entry.getKey();
            double weight = entry.getValue();

            Query termQuery = new TermQuery(new Term("contents", term));

            // Normalize weight and scale by beta
            double normalised = weight / maxWeight;
            float termBoost = (float) (BETA_EXPANSION * normalised);

            // Ensure minimum boost to prevent zero-weight terms
            termBoost = Math.max(termBoost, 0.05f);

            BoostQuery boostedTerm = new BoostQuery(termQuery, termBoost);
            builder.add(boostedTerm, BooleanClause.Occur.SHOULD);
        }

        builder.setMinimumNumberShouldMatch(1);

        return builder.build();
    }

    private static void writeResultsToFile(BufferedWriter writer,
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

            if (rank > 1000) {
                break;
            }
        }

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
                Query baseQuery = buildBaseQuery(queryParser, title, desc, narrative);
                Set<String> baseTerms = collectBaseQueryTerms(baseQuery);
                int feedbackDocs = Math.min(FEEDBACK_DOCS, maxDocs);
                TopDocs feedbackTopDocs = indexSearcher.search(baseQuery, feedbackDocs);
                Map<String, Double> expansionTerms = createExpansionTerms(indexReader, feedbackTopDocs, baseTerms);
                Query expandedQuery = buildExpansionQuery(baseQuery, expansionTerms, EXPANSION_TERMS);
                TopDocs finalResults = indexSearcher.search(expandedQuery, maxDocs);
                writeResultsToFile(writer, topicNum, finalResults, indexSearcher, runTag);

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
