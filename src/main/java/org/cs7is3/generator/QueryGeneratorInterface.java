package org.cs7is3.generator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;

import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.cs7is3.schemas.TopicSchema;

public interface QueryGeneratorInterface {
    void preprocessQuery(List<TopicSchema> parsedTopics, QueryParser queryParser,
            IndexSearcher indexSearcher, BufferedWriter writer, int numDocs, String runTag) throws IOException;
}
