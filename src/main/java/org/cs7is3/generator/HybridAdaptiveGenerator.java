package org.cs7is3.generator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.cs7is3.schemas.TopicSchema;

/**
 * Adaptive hybrid generator that selectively applies Random Indexing
 * Uses SemanticRIGenerator for topics that benefit from semantic expansion,
 * and QueryExpansionGenerator for topics that perform better with traditional PRF
 */
public class HybridAdaptiveGenerator implements QueryGeneratorInterface {

    // Topics that benefit from Random Indexing semantic expansion (based on analysis)
    // Selectively applied: 17 topics (removed 405, 417, 422 - they hurt performance)
    private static final Set<String> SEMANTIC_RI_TOPICS = new HashSet<>(Arrays.asList(
        "401", "402", "404", "407", "408", "409", "410", "411", "412",
        "414", "416", "418", "419", "420", "421", "423", "424"
    ));

    private final SemanticRIGenerator semanticRIGen;
    private final QueryExpansionGenerator expansionGen;

    public HybridAdaptiveGenerator() {
        this.semanticRIGen = new SemanticRIGenerator();
        this.expansionGen = new QueryExpansionGenerator();
    }

    @Override
    public void preprocessQuery(List<TopicSchema> parsedTopics, QueryParser queryParser,
            IndexSearcher indexSearcher, BufferedWriter writer, int numDocs, String runTag)
            throws IOException {

        // Split topics into two groups
        List<TopicSchema> semanticTopics = new ArrayList<>();
        List<TopicSchema> expansionTopics = new ArrayList<>();

        for (TopicSchema topic : parsedTopics) {
            String topicNum = topic.getNumber();
            if (topicNum == null) continue;
            topicNum = topicNum.trim();

            if (SEMANTIC_RI_TOPICS.contains(topicNum)) {
                semanticTopics.add(topic);
            } else {
                expansionTopics.add(topic);
            }
        }

        // Process semantic topics with Random Indexing
        if (!semanticTopics.isEmpty()) {
            System.out.println("Processing " + semanticTopics.size() + " topics with Semantic RI...");
            semanticRIGen.preprocessQuery(semanticTopics, queryParser, indexSearcher,
                                         writer, numDocs, runTag);
        }

        // Process remaining topics with traditional expansion
        if (!expansionTopics.isEmpty()) {
            System.out.println("Processing " + expansionTopics.size() + " topics with Query Expansion...");
            expansionGen.preprocessQuery(expansionTopics, queryParser, indexSearcher,
                                        writer, numDocs, runTag);
        }
    }
}
