package org.cs7is3.generator;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

import org.apache.lucene.document.Document;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.cs7is3.schemas.TopicSchema;

import org.apache.lucene.search.similarities.BM25Similarity;

public class QueryGenerator implements QueryGeneratorInterface {

    private static void fieldNullCheck(StringBuilder qb, String field) {
        if (field != null && !field.isBlank()) {
            if (qb.length() > 0) {
                qb.append(" ");
            }
            qb.append(field);
        }
    }

    @Override
    public void preprocessQuery(List<TopicSchema> parsedTopics,
            QueryParser queryParser,
            IndexSearcher indexSearcher,
            BufferedWriter writer,
            int numDocs,
            String runTag) throws IOException {

        // duplicate: indexSearcher.setSimilarity(new BM25Similarity(1.2f, 0.75f));
        for (TopicSchema topic : parsedTopics) {
            String topicNum = topic.getNumber();
            if (topicNum == null) {
                System.err.println("No topic ID found");
                continue;
            }
            topicNum = topicNum.trim();

            String title = topic.getTitle();
            String desc = topic.getDescription();
            String narr = topic.getNarrative();

            StringBuilder qb = new StringBuilder();
            fieldNullCheck(qb, title);
            fieldNullCheck(qb, desc);

            String queryText = qb.toString().trim();

            if (queryText.isEmpty() && narr != null && !narr.isBlank()) {
                queryText = narr.trim();
            }
            if (queryText.isEmpty()) {
                System.err.println("No text for topic number " + topicNum + ", doc skipped");
                continue;
            }

            try {
                String spec = QueryParser.escape(queryText);
                Query query = queryParser.parse(spec);

                TopDocs topDocs = indexSearcher.search(query, Math.min(numDocs, 1000));
                ScoreDoc[] results = topDocs.scoreDocs;

                int rank = 1;
                for (ScoreDoc sd : results) {
                    Document doc = indexSearcher.storedFields().document(sd.doc);
                    String docNum = doc.get("DOCNO");
                    if (docNum == null || docNum.isBlank()) {
                        continue;
                    }
                    float score = sd.score;
                    String line = String.format("%s Q0 %s %d %.6f %s",
                            topicNum, docNum, rank, score, runTag);
                    writer.write(line);
                    writer.newLine();
                    rank++;
                }

                while (rank <= 1000) {
                    String line = String.format("%s Q0 dummy_doc_%d %d 0.000000 %s",
                            topicNum, rank, rank, runTag);
                    writer.write(line);
                    writer.newLine();
                    rank++;
                }
            } catch (ParseException e) {
                System.err.println("Query failed for " + topicNum + " with error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
