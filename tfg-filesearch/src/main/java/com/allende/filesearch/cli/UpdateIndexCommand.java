package com.allende.filesearch.cli;

import com.allende.filesearch.elastic.ElasticsearchService;
import com.allende.filesearch.model.Config;
import com.allende.filesearch.model.Document;
import com.allende.filesearch.model.IndexingMetrics;
import com.allende.filesearch.tika.DocumentExtractor;
import com.allende.filesearch.utils.FileUtils;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * Command to index documents from a directory.
 */
@Command(name = "update-index", description = "Index or update documents from a directory", mixinStandardHelpOptions = true)
public class UpdateIndexCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Directory path to index")
    private String path;

    @Option(names = { "-r", "--recursive" }, description = "Index recursively", defaultValue = "true")
    private boolean recursive;

    @Option(names = { "--create-if-missing" }, description = "Create index if it doesn't exist", defaultValue = "true")
    private boolean createIfMissing;

    @Override
    public Integer call() throws Exception {
        System.out.println("Starting document indexing...");
        System.out.println("Path: " + path);

        Path rootPath = Paths.get(path);
        if (!Files.exists(rootPath)) {
            System.err.println("✗ Path does not exist: " + path);
            return 1;
        }

        // Use Dependency Container
        DependencyContainer container = DependencyContainer.getInstance();
        Config config = container.getConfig();
        ElasticsearchService esService = container.getElasticsearchService();
        DocumentExtractor extractor = container.getDocumentExtractor();

        try {
            String indexName = config.getElasticsearch().getIndexName();

            // Create index if it doesn't exist
            if (createIfMissing && !esService.getIndexManager().indexExists(indexName)) {
                System.out.println("Creating index '" + indexName + "'...");
                esService.getIndexManager().createIndex(indexName);
            }

            int bulkSize = config.getElasticsearch().getBulkSize();

            // Use parallel stream for faster processing
            // We need a thread-safe way to accumulate documents for bulk indexing
            // For simplicity in this CLI command, we'll use a synchronized list or
            // process in chunks. A better approach for huge datasets is a producer-consumer
            // queue, but parallel stream is a good step up from sequential.

            // Collect all paths first (fast)
            List<Path> allFiles = new ArrayList<>();
            try (Stream<Path> paths = recursive ? Files.walk(rootPath) : Files.list(rootPath)) {
                paths.filter(Files::isRegularFile).forEach(allFiles::add);
            }

            System.out.println("Found " + allFiles.size() + " files. Processing...");

            // Process in parallel
            List<Document> buffer = java.util.Collections.synchronizedList(new ArrayList<>());
            IndexingMetrics metrics = new IndexingMetrics();

            allFiles.parallelStream().forEach(filePath -> {
                try {
                    String extension = FileUtils.getExtension(filePath.getFileName().toString());

                    if (!extractor.isSupported(extension)) {
                        return;
                    }

                    long fileSize = Files.size(filePath);
                    long sizeInMB = fileSize / (1024 * 1024);
                    if (sizeInMB > config.getIndexing().getMaxFileSizeMb()) {
                        return;
                    }

                    long docStart = System.currentTimeMillis();
                    Document doc = extractor.extractDocument(filePath);
                    long docTime = System.currentTimeMillis() - docStart;

                    metrics.addBytes(fileSize); // Track raw bytes processed
                    metrics.recordFileProcessing(extension, docTime);
                    buffer.add(doc);

                    // Flush buffer if full (needs synchronization)
                    synchronized (buffer) {
                        if (buffer.size() >= bulkSize) {
                            try {
                                int indexed = esService.bulkIndexDocuments(new ArrayList<>(buffer));
                                metrics.incrementDocuments(indexed);
                                System.out.println("Indexed " + metrics.getTotalDocuments() + " documents...");
                                buffer.clear();
                            } catch (IOException e) {
                                System.err.println("Bulk index error: " + e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    metrics.incrementErrors();
                    // Log but don't spam console for every error
                }
            });

            // Flush remaining
            synchronized (buffer) {
                if (!buffer.isEmpty()) {
                    int indexed = esService.bulkIndexDocuments(new ArrayList<>(buffer));
                    metrics.incrementDocuments(indexed);
                }
            }

            metrics.stop();

            // Record analytics
            try {
                container.getAnalyticsManager().recordIndexingStats(metrics);
            } catch (Exception e) {
                // Ignore analytics errors
            }

            System.out.println(metrics.getFormattedReport());

            return 0;
        } catch (Exception e) {
            System.err.println("✗ Indexing failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    // Public getters/setters for ReindexCommand to access these fields
    public void setPath(String path) {
        this.path = path;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public void setCreateIfMissing(boolean createIfMissing) {
        this.createIfMissing = createIfMissing;
    }

    public String getPath() {
        return path;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public boolean isCreateIfMissing() {
        return createIfMissing;
    }
}
