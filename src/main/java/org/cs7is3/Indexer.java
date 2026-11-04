package org.cs7is3;

import org.cs7is3.parser.TopicParser;

import java.io.IOException;

import org.cs7is3.indexer.DocumentIndexer;

// TODO: Implement your Lucene indexer
// This class should build a Lucene index from the document collection
//
// Requirements:
// 1. Parse documents from the "Assignment Two" dataset
// 2. Extract relevant fields (DOCNO, TITLE, TEXT, etc.)
// 3. Create a Lucene index with appropriate analyzers
// 4. Handle document parsing errors gracefully
//
// The GitHub Actions workflow will call:
//   indexer.buildIndex(Path docsPath, Path indexPath)

public class Indexer {
    public void buildIndex(java.nio.file.Path docsPath, java.nio.file.Path indexPath) throws java.io.IOException {
        DocumentIndexer indexer = new DocumentIndexer();
        indexer.indexDocuments(docsPath, indexPath);

        System.out.println(docsPath);
        System.out.println(indexPath);

    }
}
