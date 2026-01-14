package com.allende.filesearch.watcher;

import com.allende.filesearch.cli.DependencyContainer;
import com.allende.filesearch.elastic.ElasticsearchService;
import com.allende.filesearch.model.Config;
import com.allende.filesearch.model.Document;
import com.allende.filesearch.tika.DocumentExtractor;
import com.allende.filesearch.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Watches a directory for file system changes and updates the Elasticsearch
 * index.
 * Uses a Producer-Consumer pattern to decouple file events from indexing.
 */
public class FileSystemWatcher implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(FileSystemWatcher.class);

    private final Path watchPath;
    private final Config config;
    private final WatchService watchService;
    private final ElasticsearchService esService;
    private final DocumentExtractor extractor;
    private final ScheduledExecutorService scheduler;
    private final Map<Path, Long> pendingChanges;
    private final BlockingQueue<Path> eventQueue;
    private final ExecutorService consumerExecutor;
    private volatile boolean running = false;

    public FileSystemWatcher(Path watchPath, Config config) throws IOException {
        this.watchPath = watchPath;
        this.config = config;
        this.watchService = FileSystems.getDefault().newWatchService();

        // Use DependencyContainer if available, otherwise create new (fallback)
        DependencyContainer container = DependencyContainer.getInstance();
        this.esService = container.getElasticsearchService();
        this.extractor = container.getDocumentExtractor();

        this.scheduler = Executors.newScheduledThreadPool(1);
        this.pendingChanges = new ConcurrentHashMap<>();

        // Producer-Consumer setup
        this.eventQueue = new LinkedBlockingQueue<>(10000); // Max 10k pending events
        this.consumerExecutor = Executors.newSingleThreadExecutor();

        // Register directory for watching
        registerRecursive(watchPath);
    }

    private void registerRecursive(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                dir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void start() {
        running = true;
        logger.info("File system watcher started for path: {}", watchPath);

        // Start consumer thread
        consumerExecutor.submit(this::processQueue);

        // Start debounce processor
        scheduler.scheduleAtFixedRate(
                this::processDebounced,
                1, 1, TimeUnit.SECONDS);

        // Watch for events
        while (running) {
            WatchKey key;
            try {
                key = watchService.poll(1, TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }
            } catch (InterruptedException e) {
                logger.info("Watcher interrupted");
                break;
            }

            Path dir = (Path) key.watchable();

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path filename = ev.context();
                Path fullPath = dir.resolve(filename);

                handleFileEvent(kind, fullPath);
            }

            boolean valid = key.reset();
            if (!valid) {
                break;
            }
        }
    }

    private void handleFileEvent(WatchEvent.Kind<?> kind, Path path) {
        logger.debug("File event: {} - {}", kind.name(), path);
        // Add to pending changes for debouncing
        pendingChanges.put(path, System.currentTimeMillis());
    }

    private void processDebounced() {
        long now = System.currentTimeMillis();
        int debounceMs = config.getWatch().getDebounceMs();

        pendingChanges.entrySet().removeIf(entry -> {
            Path path = entry.getKey();
            long timestamp = entry.getValue();

            if (now - timestamp > debounceMs) {
                // Move to processing queue instead of processing directly
                eventQueue.offer(path);
                return true; // Remove from pending
            }
            return false; // Keep in pending
        });
    }

    private void processQueue() {
        while (running) {
            try {
                Path path = eventQueue.poll(1, TimeUnit.SECONDS);
                if (path != null) {
                    processChange(path);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error processing queue item", e);
            }
        }
    }

    private void processChange(Path path) {
        if (!Files.isRegularFile(path)) {
            // Handle deletion
            if (!Files.exists(path)) {
                try {
                    esService.deleteDocument(path.toString());
                    logger.info("Deleted from index: {}", path);
                } catch (IOException e) {
                    logger.error("Failed to delete document: {}", path, e);
                }
            }
            return;
        }

        // Check if supported
        String extension = FileUtils.getExtension(path.getFileName().toString());
        if (!extractor.isSupported(extension)) {
            return;
        }

        // Index the document
        try {
            Document doc = extractor.extractDocument(path);
            esService.indexDocument(doc);
            logger.info("Indexed: {}", path);
            System.out.println("[" +
                    java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_TIME) +
                    "] Indexed: " + path.getFileName());
        } catch (IOException e) {
            logger.error("Failed to index document: {}", path, e);
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        scheduler.shutdownNow();
        consumerExecutor.shutdownNow();
        watchService.close();
        // Don't close esService here as it's managed by DependencyContainer now
        logger.info("File system watcher stopped");
    }
}
