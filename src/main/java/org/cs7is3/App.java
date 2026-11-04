package org.cs7is3;

import java.io.IOException;
import java.nio.file.Paths;

import org.cs7is3.Indexer;
import org.cs7is3.searcher.DocumentSearcher;

// TODO: Implement your main application class
// This class should handle command-line arguments and coordinate between Indexer and Searcher
// 
// Required command-line interface:
//   java -jar your-jar.jar index --docs "Assignment Two" --index index
//   java -jar your-jar.jar search --index index --topics topics --output runs/student.run --numDocs 1000
// ̰
// The GitHub Actions workflow expects:
// 1. Maven build to succeed (mvn clean package)
// 2. Index command to create an index from the dataset
// 3. Search command to produce a TREC-format run file with exactly 1000 results per topic
// 4. Output format: "topic_id Q0 docno rank score run_tag"

public class App {
    public static void main(String[] args) {
        // TODO: Implement your main method
        // Parse command-line arguments and delegate to Indexer/Searcher
        String cmd = args[0];
        if (cmd.equals("index")) {
            String documentPath = null;
            String indexSavePath = null;

            for (int i = 1; i < args.length; i++) {
                String currentElement = args[i];
                if (currentElement.equals("--docs")) {
                    documentPath = args[++i];
                } else if (currentElement.equals("--index")) {
                    indexSavePath = args[++i];
                }
            }
            Indexer indexer = new Indexer();
            try {
                indexer.buildIndex(Paths.get(documentPath), Paths.get(indexSavePath));
            } catch (IOException e) {
                System.out.println("Failed to run indexer: " + e.getMessage());
                e.printStackTrace();
            }
        } else if (cmd.equals("search")) {
            String indexPath = null;
            String topicsPath = null;
            String outputPath = null;
            int documentCount = 0;

            for (int i = 1; i < args.length; i++) {
                String currentElement = args[i];
                if (currentElement.equals("--index")) {
                    indexPath = args[++i];
                } else if (currentElement.equals("--topics")) {
                    topicsPath = args[++i];
                } else if (currentElement.equals("--output")) {
                    outputPath = args[++i];
                } else if (currentElement.equals("--numDocs")) {
                    documentCount = Integer.parseInt(args[++i]);
                }
            }
            Searcher searcher = new Searcher();
            try {
                searcher.searchTopics(
                        Paths.get(indexPath),
                        Paths.get(topicsPath),
                        Paths.get(outputPath),
                        documentCount);
            } catch (IOException e) {
                System.out.println("Failed to run searcher: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
