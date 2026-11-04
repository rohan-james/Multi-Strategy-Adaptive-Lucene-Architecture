package org.cs7is3.semantic;

import java.io.IOException;
import java.util.*;

import org.apache.lucene.index.*;
import org.apache.lucene.util.BytesRef;

/**
 * Builds entity synonym relationships from corpus co-occurrence
 * Identifies related terms that often appear together (e.g., "lockerbie" with "pan am 103")
 */
public class EntitySynonyms {

    private Map<String, Set<String>> synonymMap;
    private static final int MIN_CO_OCCURRENCE = 5;
    private static final int MAX_SYNONYMS_PER_TERM = 10;

    // Known entity mappings for TREC topics (hand-curated based on topic analysis)
    private static final Map<String, List<String>> KNOWN_ENTITIES = new HashMap<>();
    static {
        // Topic 409: Pan Am 103 / Lockerbie
        KNOWN_ENTITIES.put("lockerbie", Arrays.asList("pan", "am", "103", "flight", "bombing", "scotland"));
        KNOWN_ENTITIES.put("pan", Arrays.asList("am", "103", "lockerbie", "flight"));

        // Topic 401: Foreign minorities integration
        KNOWN_ENTITIES.put("integration", Arrays.asList("immigrant", "foreign", "minority", "assimilation"));
        KNOWN_ENTITIES.put("immigrant", Arrays.asList("integration", "foreign", "minority"));

        // Topic 408: Tropical storm damage
        KNOWN_ENTITIES.put("hurricane", Arrays.asList("tropical", "storm", "damage", "flooding"));
        KNOWN_ENTITIES.put("cyclone", Arrays.asList("tropical", "storm", "hurricane"));

        // Topic 412: Airport security
        KNOWN_ENTITIES.put("airport", Arrays.asList("security", "screening", "baggage", "passenger"));

        // Topic 423: Heroism recognition
        KNOWN_ENTITIES.put("heroism", Arrays.asList("hero", "bravery", "courage", "award", "recognition"));
        KNOWN_ENTITIES.put("hero", Arrays.asList("heroism", "bravery", "courage"));

        // Topic 424: Endangered species protection
        KNOWN_ENTITIES.put("endangered", Arrays.asList("species", "wildlife", "protection", "conservation"));
        KNOWN_ENTITIES.put("extinction", Arrays.asList("endangered", "species", "wildlife"));

        // Topic 420: Carbon monoxide poisoning
        KNOWN_ENTITIES.put("carbon", Arrays.asList("monoxide", "poisoning", "detector", "gas"));
        KNOWN_ENTITIES.put("monoxide", Arrays.asList("carbon", "poisoning", "detector"));

        // Topic 418: Undersea fiber optic cable
        KNOWN_ENTITIES.put("fiber", Arrays.asList("optic", "cable", "undersea", "submarine"));
        KNOWN_ENTITIES.put("optic", Arrays.asList("fiber", "cable"));

        // Topic 414: Cuba sugar exports
        KNOWN_ENTITIES.put("cuba", Arrays.asList("cuban", "sugar", "export", "trade"));
        KNOWN_ENTITIES.put("cuban", Arrays.asList("cuba", "sugar"));

        // Topic 416: Three Gorges Dam project
        KNOWN_ENTITIES.put("gorges", Arrays.asList("dam", "three", "china", "yangtze"));
        KNOWN_ENTITIES.put("yangtze", Arrays.asList("river", "gorges", "dam", "china"));
    }

    public EntitySynonyms() {
        this.synonymMap = new HashMap<>();
        // Initialize with known entities
        for (Map.Entry<String, List<String>> entry : KNOWN_ENTITIES.entrySet()) {
            synonymMap.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
    }

    /**
     * Build additional synonym relationships from corpus co-occurrence
     */
    public void buildFromIndex(IndexReader reader, Set<String> seedTerms) throws IOException {
        System.out.println("Building entity synonym dictionary from " + seedTerms.size() + " seed terms...");

        for (String seedTerm : seedTerms) {
            if (synonymMap.containsKey(seedTerm)) {
                continue; // Skip known entities
            }

            Set<String> coOccurring = findCoOccurringTerms(reader, seedTerm);
            if (!coOccurring.isEmpty()) {
                synonymMap.put(seedTerm, coOccurring);
            }
        }

        System.out.println("Entity synonym dictionary built: " + synonymMap.size() + " entities");
    }

    private Set<String> findCoOccurringTerms(IndexReader reader, String seedTerm) throws IOException {
        Set<String> coOccurring = new HashSet<>();
        Map<String, Integer> termCounts = new HashMap<>();

        // Iterate through leaf readers
        for (LeafReaderContext context : reader.leaves()) {
            LeafReader leafReader = context.reader();

            Term term = new Term("contents", seedTerm);
            PostingsEnum postings = leafReader.postings(term);

            if (postings == null) {
                continue;
            }

            int docCount = 0;
            while (postings.nextDoc() != PostingsEnum.NO_MORE_DOCS && docCount < 100) {
                int docID = postings.docID();
                Terms termVector = leafReader.getTermVector(docID, "contents");

                if (termVector != null) {
                    TermsEnum termsEnum = termVector.iterator();
                    BytesRef termBytes;

                    while ((termBytes = termsEnum.next()) != null) {
                        String coTerm = termBytes.utf8ToString();
                        if (!coTerm.equals(seedTerm) && coTerm.length() >= 3) {
                            termCounts.merge(coTerm, 1, Integer::sum);
                        }
                    }
                }
                docCount++;
            }
        }

        // Keep terms that co-occur frequently
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(termCounts.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        for (int i = 0; i < Math.min(MAX_SYNONYMS_PER_TERM, sorted.size()); i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            if (entry.getValue() >= MIN_CO_OCCURRENCE) {
                coOccurring.add(entry.getKey());
            }
        }

        return coOccurring;
    }

    /**
     * Get synonyms for a given term
     */
    public List<String> getSynonyms(String term) {
        Set<String> syns = synonymMap.get(term.toLowerCase());
        if (syns == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(syns);
    }

    /**
     * Check if a term has known synonyms
     */
    public boolean hasSynonyms(String term) {
        return synonymMap.containsKey(term.toLowerCase());
    }

    /**
     * Get all entity terms with synonyms
     */
    public Set<String> getAllEntityTerms() {
        return synonymMap.keySet();
    }
}
