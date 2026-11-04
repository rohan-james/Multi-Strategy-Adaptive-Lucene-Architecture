package org.cs7is3.indexer;

import org.apache.lucene.analysis.Analyzer;
import org.cs7is3.analyser.Analyser;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;
import java.util.concurrent.*;
import java.util.stream.*;
import java.util.List;
import java.util.ArrayList;

public class DocumentIndexer {

    private static final String DOCS_PATH = "./Assignment Two";
    private static final String INDEX_PATH = "index";
    private static final AnalyzerType ANALYZER_TYPE = AnalyzerType.CUSTOM;

    private static final float TITLE_BOOST = 2.0f;
    private static final float HEADING_BOOST = 1.5f;
    private static final float TEXT_BOOST = 1.0f;

    enum AnalyzerType {
        ENGLISH,
        STANDARD,
        CUSTOM
    }

    // public static void main(String[] args) throws Exception {
    // System.out.println("Indexer Started");
    // System.out.println("Collection Path: " + DOCS_PATH);
    // System.out.println("Index Path: " + INDEX_PATH);
    // System.out.println("Analyzer Type: " + ANALYZER_TYPE);

    // DocumentIndexer indexer = new DocumentIndexer();
    // int docsIndexed = indexer.indexDocuments();

    // }

    public int indexDocuments(java.nio.file.Path docsPath, java.nio.file.Path indexPath) throws IOException {

        Analyzer analyzer = createAnalyzer();
        // Directory indexDir = FSDirectory.open(Paths.get(INDEX_PATH));
        Directory indexDir = FSDirectory.open(indexPath);
        IndexWriterConfig config = new IndexWriterConfig(analyzer);

        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        IndexWriter writer = new IndexWriter(indexDir, config);

        int docCount = indexDirectory(writer, docsPath);

        writer.close();
        indexDir.close();
        return docCount;

    }

    private Analyzer createAnalyzer() {
        switch (ANALYZER_TYPE) {
            case ENGLISH:
                return new EnglishAnalyzer();
            case STANDARD:
                return new StandardAnalyzer();
            case CUSTOM:
                return new Analyser();
            default:
                return new StandardAnalyzer();
        }
    }

    private int indexDirectory(IndexWriter writer, Path path) throws IOException {

        int count = 0;
        if (Files.isDirectory(path)) {
            String dirName = path.getFileName().toString().toLowerCase();

            if (dirName.equals("dtds")) {
                System.out.println("Skipping DTDs directory: " + path.toString());
                return 0;

            }

            // Collect all files first for parallel processing
            List<Path> filesToIndex = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        // Recursively process subdirectories
                        count += indexDirectory(writer, entry);
                    } else {
                        String filename = entry.getFileName().toString().toLowerCase();
                        if (!shouldSkipFile(filename)) {
                            filesToIndex.add(entry);
                        } else {
                            System.out.println("Skipping file: " + filename);
                        }
                    }
                }
            }

            // Process files in parallel
            if (!filesToIndex.isEmpty()) {
                ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
                List<Future<Integer>> futures = new ArrayList<>();

                for (Path file : filesToIndex) {
                    futures.add(executor.submit(() -> {
                        try {
                            System.out.println("Indexing file: " + file.getFileName());
                            return indexFile(writer, file);
                        } catch (IOException e) {
                            System.err.println("Error indexing file " + file + ": " + e.getMessage());
                            return 0;
                        }
                    }));
                }

                // Wait for all tasks to complete
                for (Future<Integer> future : futures) {
                    try {
                        count += future.get();
                    } catch (InterruptedException | ExecutionException e) {
                        System.err.println("Error processing file: " + e.getMessage());
                    }
                }

                executor.shutdown();
                try {
                    executor.awaitTermination(1, TimeUnit.HOURS);
                } catch (InterruptedException e) {
                    System.err.println("Indexing interrupted: " + e.getMessage());
                }
            }
        } else {
            String filename = path.getFileName().toString().toLowerCase();

            if (shouldSkipFile(filename)) {
                System.out.println("Skipping file: " + filename);
                return 0;
            }

            System.out.println("Indexing file: " + filename);
            count = indexFile(writer, path);

        }

        return count;
    }

    private boolean shouldSkipFile(String filename) {
        if (filename.startsWith("read")) {
            return true;
        }
        return false;
    }

    private synchronized int indexFile(IndexWriter writer, Path filePath) throws IOException {
        String content = new String(Files.readAllBytes(filePath));
        Pattern docPattern = Pattern.compile("<DOC>(.*?)</DOC>", Pattern.DOTALL);
        Matcher docMatcher = docPattern.matcher(content);

        int count = 0;
        List<Document> batch = new ArrayList<>();

        while (docMatcher.find()) {
            String docContent = docMatcher.group(1);
            Document doc = parseDocument(docContent);
            if (doc != null) {
                batch.add(doc);
                count++;
            }
        }

        // Write all documents from this file at once (thread-safe)
        if (!batch.isEmpty()) {
            writer.addDocuments(batch);
            System.out.println("Total documents indexed from file " + filePath.getFileName() + ": " + count);
        }

        return count;
    }

    private Document parseDocument(String docContent) {
        Document doc = new Document();

        String docno = extractTag(docContent, "DOCNO");
        if (docno == null || docno.isEmpty()) {
            System.err.println("Warning: Document without DOCNO found");
            return null;
        }
        doc.add(new StringField("DOCNO", docno, Field.Store.YES));

        String title = extractTag(docContent, "HEADLINE");
        if (title == null) {
            title = extractTag(docContent, "TITLE");
        }
        if (title == null) {
            title = extractTag(docContent, "TI");
        }
        if (title == null) {
            title = extractTag(docContent, "HEAD");
        }

        if (title != null && !title.isEmpty()) {

            title = cleanText(title);
            FieldType titleType = createTextField(TITLE_BOOST);
            doc.add(new Field("title", title, titleType));
        }

        String text = extractTag(docContent, "TEXT");
        if (text != null && !text.isEmpty()) {
            text = cleanText(text);

            FieldType textType = createTextField(TEXT_BOOST);
            doc.add(new Field("text", text, textType));
        }

        String date = extractTag(docContent, "DATE");
        if (date == null) {
            date = extractTag(docContent, "DATE1");
        }
        if (date != null && !date.isEmpty()) {
            date = cleanText(date);
            doc.add(new StringField("date", date, Field.Store.YES));
        }

        String section = extractTag(docContent, "SECTION");
        if (section != null && !section.isEmpty()) {
            section = cleanText(section);
            doc.add(new TextField("section", section, Field.Store.YES));
        }

        String byline = extractTag(docContent, "BYLINE");
        if (byline == null) {
            byline = extractTag(docContent, "AU");
        }
        if (byline != null && !byline.isEmpty()) {
            byline = cleanText(byline);
            doc.add(new TextField("author", byline, Field.Store.YES));
        }

        // Extract H3/TI combination (FBIS documents)
        String h3Title = extractTag(docContent, "H3");
        if (h3Title != null) {
            String ti = extractTag(h3Title, "TI");
            if (ti != null && !ti.isEmpty()) {
                ti = cleanText(ti);
                if (title == null || title.isEmpty()) {
                    title = ti; // Use as title if no HEADLINE
                }
            }
        }

        // Extract HEADER content (contains useful context)
        String header = extractTag(docContent, "HEADER");
        if (header != null && !header.isEmpty()) {
            header = cleanText(header);
            doc.add(new TextField("header", header, Field.Store.NO));
        }

        // Build weighted combined content field for searching
        // Title gets repeated for implicit boost in BM25
        StringBuilder combined = new StringBuilder();

        // Title is most important - repeat 3x for implicit boost
        if (title != null) {
            combined.append(title).append(" ");
            combined.append(title).append(" ");
            combined.append(title).append(" ");
        }
        // Header provides context
        if (header != null)
            combined.append(header).append(" ");
        // Main text content
        if (text != null)
            combined.append(text).append(" ");

        if (combined.length() > 0) {
            FieldType contentsType = createTextField(TEXT_BOOST);
            doc.add(new Field("contents", combined.toString(), contentsType));
        }

        return doc;

    }

    private String cleanText(String text) {
        if (text == null)
            return null;

        text = text.replaceAll("<[^>]+>", " ");

        text = text.replaceAll("\\s+", " ");

        text = text.trim();

        return text;
    }

    private FieldType createTextField(float boost) {
        FieldType type = new FieldType();

        type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
        type.setTokenized(true);
        type.setStored(false);

        type.setStoreTermVectors(true);
        type.setStoreTermVectorPositions(true);
        type.setStoreTermVectorOffsets(true);

        type.freeze();

        return type;
    }

    private String extractTag(String content, String tagName) {
        Pattern pattern = Pattern.compile(
                "<" + tagName + ">(.*?)</" + tagName + ">",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            String extracted = matcher.group(1).trim();
            return extracted.isEmpty() ? null : extracted;
        }

        return null;
    }

}
