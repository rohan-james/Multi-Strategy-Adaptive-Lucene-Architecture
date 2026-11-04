package org.cs7is3;

import java.util.List;

import org.cs7is3.generator.QueryExpansionGenerator;
import org.cs7is3.generator.QueryGenerator;
import org.cs7is3.generator.QueryGeneratorInterface;
import org.cs7is3.generator.SemanticQueryGenerator;
import org.cs7is3.generator.EnhancedSemanticGenerator;
import org.cs7is3.generator.HybridAdaptiveGenerator;
import org.cs7is3.parser.TopicParser;
import org.cs7is3.schemas.TopicSchema;
import org.cs7is3.searcher.DocumentSearcher;

// TODO: Implement your Lucene searcher
// This class should search topics against the index and produce TREC-format results
//
// Requirements:
// 1. Parse topics from the topics file
// 2. Generate queries from topic information (title, description, narrative)
// 3. Execute searches against the Lucene index
// 4. Write TREC-format results: "topic_id Q0 docno rank score run_tag"
// 5. Output exactly 1000 results per topic
//
// The GitHub Actions workflow will call:
//   searcher.searchTopics(Path indexPath, Path topicsPath, Path outputRun, int numDocs)

public class Searcher {
    public void searchTopics(java.nio.file.Path indexPath, java.nio.file.Path topicsPath,
            java.nio.file.Path outputRun, int numDocs) throws java.io.IOException {

        List<TopicSchema> parsedTopics = TopicParser.parseTopics(topicsPath.toString());
        DocumentSearcher docSearcher = new DocumentSearcher();
        QueryGeneratorInterface generator = createQueryGenerator("hybrid");

        docSearcher.searchTopics(indexPath, topicsPath, outputRun, numDocs, parsedTopics, generator);
    }

    private QueryGeneratorInterface createQueryGenerator(String generatorType) {
        switch (generatorType) {
            case "hybrid":
                return new HybridAdaptiveGenerator();
            case "enhanced":
                return new EnhancedSemanticGenerator();
            case "semantic":
                return new SemanticQueryGenerator();
            case "expansion":
                return new QueryExpansionGenerator();
            case "basic":
                return new QueryGenerator();
        }
        return null;
    }
}