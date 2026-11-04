package org.cs7is3.semantic;

import java.io.IOException;
import java.util.*;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

/**
 * Lightweight Random Indexing for semantic similarity
 * Creates low-dimensional semantic vectors without heavy computation
 */
public class RandomIndexing {

    private static final int VECTOR_DIMENSION = 300;
    private static final int CONTEXT_WINDOW = 5;
    private static final int NON_ZERO_COUNT = 10; // Sparse random vectors
    private static final Random random = new Random(42); // Reproducible

    // Term -> semantic vector
    private Map<String, float[]> termVectors;

    // Term -> random index vector (sparse)
    private Map<String, float[]> indexVectors;

    // Vocabulary
    private Set<String> vocabulary;

    public RandomIndexing() {
        this.termVectors = new HashMap<>();
        this.indexVectors = new HashMap<>();
        this.vocabulary = new HashSet<>();
    }

    /**
     * Build semantic vectors from the index
     */
    public void buildFromIndex(IndexReader reader, int maxTerms) throws IOException {
        System.out.println("Building Random Indexing vectors...");

        // Step 1: Build vocabulary from high-quality terms
        buildVocabulary(reader, maxTerms);

        // Step 2: Create random index vectors for each term
        createIndexVectors();

        // Step 3: Build context vectors by scanning documents
        buildContextVectors(reader);

        System.out.println("Random Indexing complete. Vocabulary size: " + vocabulary.size());
    }

    private void buildVocabulary(IndexReader reader, int maxTerms) throws IOException {
        System.out.println("Building vocabulary...");

        Map<String, Integer> termFreqs = new HashMap<>();
        int totalDocs = reader.numDocs();

        // Collect terms from all documents
        for (int docId = 0; docId < Math.min(totalDocs, 10000); docId++) {
            Terms terms = reader.getTermVector(docId, "contents");
            if (terms == null) continue;

            TermsEnum termsEnum = terms.iterator();
            BytesRef term;

            while ((term = termsEnum.next()) != null) {
                String termStr = term.utf8ToString();

                // Filter: length and basic quality
                if (termStr.length() >= 3 && termStr.length() <= 20 &&
                    termStr.matches("[a-z]+")) {
                    termFreqs.merge(termStr, 1, Integer::sum);
                }
            }

            if (docId % 1000 == 0) {
                System.out.println("Processed " + docId + " documents for vocabulary...");
            }
        }

        // Select top terms by frequency (but not too common)
        List<Map.Entry<String, Integer>> sortedTerms = new ArrayList<>(termFreqs.entrySet());
        sortedTerms.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        int selected = 0;
        for (Map.Entry<String, Integer> entry : sortedTerms) {
            // Skip very common terms (likely stop words)
            if (entry.getValue() > totalDocs * 0.3) continue;
            // Skip very rare terms
            if (entry.getValue() < 3) continue;

            vocabulary.add(entry.getKey());
            selected++;
            if (selected >= maxTerms) break;
        }

        System.out.println("Vocabulary built: " + vocabulary.size() + " terms");
    }

    private void createIndexVectors() {
        System.out.println("Creating random index vectors...");

        for (String term : vocabulary) {
            indexVectors.put(term, createSparseRandomVector());
        }

        System.out.println("Index vectors created: " + indexVectors.size());
    }

    private float[] createSparseRandomVector() {
        float[] vector = new float[VECTOR_DIMENSION];

        // Create sparse random vector with +1 and -1 values
        Set<Integer> positions = new HashSet<>();
        while (positions.size() < NON_ZERO_COUNT) {
            positions.add(random.nextInt(VECTOR_DIMENSION));
        }

        for (int pos : positions) {
            vector[pos] = random.nextBoolean() ? 1.0f : -1.0f;
        }

        return vector;
    }

    private void buildContextVectors(IndexReader reader) throws IOException {
        System.out.println("Building context vectors...");

        int totalDocs = reader.numDocs();

        // Initialize context vectors
        for (String term : vocabulary) {
            termVectors.put(term, new float[VECTOR_DIMENSION]);
        }

        // Scan documents and accumulate context
        for (int docId = 0; docId < Math.min(totalDocs, 10000); docId++) {
            Terms terms = reader.getTermVector(docId, "contents");
            if (terms == null) continue;

            // Get document terms
            List<String> docTerms = new ArrayList<>();
            TermsEnum termsEnum = terms.iterator();
            BytesRef term;

            while ((term = termsEnum.next()) != null) {
                String termStr = term.utf8ToString();
                if (vocabulary.contains(termStr)) {
                    docTerms.add(termStr);
                }
            }

            // Build context for each term
            for (int i = 0; i < docTerms.size(); i++) {
                String centerTerm = docTerms.get(i);
                float[] centerVector = termVectors.get(centerTerm);

                // Add context from surrounding terms
                int windowStart = Math.max(0, i - CONTEXT_WINDOW);
                int windowEnd = Math.min(docTerms.size(), i + CONTEXT_WINDOW + 1);

                for (int j = windowStart; j < windowEnd; j++) {
                    if (j == i) continue; // Skip center term

                    String contextTerm = docTerms.get(j);
                    float[] indexVector = indexVectors.get(contextTerm);

                    if (indexVector != null) {
                        // Accumulate context
                        for (int k = 0; k < VECTOR_DIMENSION; k++) {
                            centerVector[k] += indexVector[k];
                        }
                    }
                }
            }

            if (docId % 1000 == 0) {
                System.out.println("Processed " + docId + " documents for context...");
            }
        }

        // Normalize vectors
        for (float[] vector : termVectors.values()) {
            normalize(vector);
        }

        System.out.println("Context vectors built and normalized");
    }

    private void normalize(float[] vector) {
        float norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);

        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }

    /**
     * Find semantically similar terms
     */
    public List<SimilarTerm> findSimilar(String term, int topN) {
        float[] queryVector = termVectors.get(term);
        if (queryVector == null) {
            return Collections.emptyList();
        }

        List<SimilarTerm> similar = new ArrayList<>();

        for (Map.Entry<String, float[]> entry : termVectors.entrySet()) {
            String candidateTerm = entry.getKey();
            if (candidateTerm.equals(term)) continue;

            float[] candidateVector = entry.getValue();
            float similarity = cosineSimilarity(queryVector, candidateVector);

            if (similarity > 0.3) { // Threshold
                similar.add(new SimilarTerm(candidateTerm, similarity));
            }
        }

        similar.sort((a, b) -> Float.compare(b.similarity, a.similarity));

        return similar.subList(0, Math.min(topN, similar.size()));
    }

    /**
     * Find similar terms for multiple query terms (averaged)
     */
    public List<SimilarTerm> findSimilarMulti(List<String> queryTerms, int topN) {
        // Average query vectors
        float[] avgVector = new float[VECTOR_DIMENSION];
        int count = 0;

        for (String term : queryTerms) {
            float[] vector = termVectors.get(term);
            if (vector != null) {
                for (int i = 0; i < VECTOR_DIMENSION; i++) {
                    avgVector[i] += vector[i];
                }
                count++;
            }
        }

        if (count == 0) return Collections.emptyList();

        // Normalize average
        for (int i = 0; i < VECTOR_DIMENSION; i++) {
            avgVector[i] /= count;
        }
        normalize(avgVector);

        // Find similar to averaged query
        List<SimilarTerm> similar = new ArrayList<>();
        Set<String> querySet = new HashSet<>(queryTerms);

        for (Map.Entry<String, float[]> entry : termVectors.entrySet()) {
            String candidateTerm = entry.getKey();
            if (querySet.contains(candidateTerm)) continue;

            float[] candidateVector = entry.getValue();
            float similarity = cosineSimilarity(avgVector, candidateVector);

            if (similarity > 0.25) {
                similar.add(new SimilarTerm(candidateTerm, similarity));
            }
        }

        similar.sort((a, b) -> Float.compare(b.similarity, a.similarity));

        return similar.subList(0, Math.min(topN, similar.size()));
    }

    private float cosineSimilarity(float[] v1, float[] v2) {
        float dot = 0;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
        }
        return dot; // Already normalized
    }

    public boolean hasVector(String term) {
        return termVectors.containsKey(term);
    }

    public static class SimilarTerm {
        public final String term;
        public final float similarity;

        public SimilarTerm(String term, float similarity) {
            this.term = term;
            this.similarity = similarity;
        }

        @Override
        public String toString() {
            return term + " (" + String.format("%.3f", similarity) + ")";
        }
    }
}
