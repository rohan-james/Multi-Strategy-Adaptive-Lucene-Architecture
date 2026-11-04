package org.cs7is3.searcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;

import java.util.List;

import org.cs7is3.schemas.TopicSchema;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.analysis.Analyzer;
import org.cs7is3.analyser.Analyser;
import org.cs7is3.generator.QueryGeneratorInterface;

public class DocumentSearcher {
    private static final String RUN_TAG = "BM25";

    public void searchTopics(java.nio.file.Path indexPath, java.nio.file.Path topicsPath,
            java.nio.file.Path outputRun, int numDocs, List<TopicSchema> parsedTopics,
            QueryGeneratorInterface generator) throws java.io.IOException {

        try (Directory dir = FSDirectory.open(indexPath);
                DirectoryReader reader = DirectoryReader.open(dir)) {
            IndexSearcher indexSearcher = new IndexSearcher(reader);
            // BM25 default parameters
            indexSearcher.setSimilarity(new BM25Similarity(1.2f, 0.75f));
            Analyzer analyzer = new Analyser();
            QueryParser queryParser = new QueryParser("contents", analyzer);

            Path parent = outputRun.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(outputRun, StandardCharsets.UTF_8)) {
                generator.preprocessQuery(parsedTopics, queryParser, indexSearcher, writer, numDocs, RUN_TAG);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
