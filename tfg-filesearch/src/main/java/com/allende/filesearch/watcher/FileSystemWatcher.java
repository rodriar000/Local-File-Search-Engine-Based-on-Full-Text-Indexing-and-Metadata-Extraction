package com.allende.filesearch.watcher;

import com.allende.filesearch.index.DocumentIndex;
import com.allende.filesearch.index.IndexSynchronizer;
import com.allende.filesearch.model.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps the index in sync with a folder while it runs: changed files are
 * re-indexed after a short quiet period (debounce), deleted files are removed,
 * and new sub-folders are watched too. If events are lost (OS overflow), the
 * whole folder is re-synced.
 */
public class FileSystemWatcher implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(FileSystemWatcher.class);

    private final Path watchPath;
    private final DocumentIndex index;
    private final IndexSynchronizer synchronizer;
    private final int debounceMs;
    private final WatchService watchService;
    private final ScheduledExecutorService scheduler;
    private final Map<Path, Long> pendingChanges = new ConcurrentHashMap<>();
    private final AtomicBoolean fullResyncNeeded = new AtomicBoolean(false);
    private volatile boolean running = false;

    public FileSystemWatcher(Path watchPath, DocumentIndex index, IndexSynchronizer synchronizer, Config config)
            throws IOException {
        this.watchPath = watchPath.toAbsolutePath().normalize();
        this.index = index;
        this.synchronizer = synchronizer;
        this.debounceMs = config.getWatch().getDebounceMs();
        this.watchService = FileSystems.getDefault().newWatchService();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        registerRecursive(this.watchPath);
    }

    private void registerRecursive(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(watchPath) && synchronizer.exclusions().isExcludedFolder(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                dir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Blocks, processing events until {@link #close()} is called or the thread is interrupted. */
    public void start() {
        running = true;
        logger.info("Watching {}", watchPath);
        scheduler.scheduleWithFixedDelay(this::processPending, debounceMs, Math.max(200, debounceMs / 2),
                TimeUnit.MILLISECONDS);

        while (running) {
            WatchKey key;
            try {
                key = watchService.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (java.nio.file.ClosedWatchServiceException e) {
                break;
            }
            if (key == null) {
                continue;
            }

            Path dir = (Path) key.watchable();
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    fullResyncNeeded.set(true);
                    continue;
                }
                Path changed = dir.resolve((Path) event.context());
                if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed)) {
                    try {
                        registerRecursive(changed);
                    } catch (IOException e) {
                        logger.warn("Could not watch a new folder: {}", e.getMessage());
                    }
                    // Files moved in together with the folder produce no events of their own.
                    fullResyncNeeded.set(true);
                }
                pendingChanges.put(changed, System.currentTimeMillis());
            }
            key.reset();
        }
    }

    private void processPending() {
        try {
            if (fullResyncNeeded.getAndSet(false)) {
                pendingChanges.clear();
                synchronizer.sync(watchPath, IndexSynchronizer.ProgressListener.NONE, new AtomicBoolean(false), null);
                return;
            }

            long now = System.currentTimeMillis();
            List<Path> ready = new ArrayList<>();
            pendingChanges.forEach((path, timestamp) -> {
                if (now - timestamp >= debounceMs) {
                    ready.add(path);
                }
            });
            if (ready.isEmpty()) {
                return;
            }
            for (Path path : ready) {
                pendingChanges.remove(path);
                if (Files.isDirectory(path)) {
                    continue;
                }
                try {
                    synchronizer.syncFile(path);
                } catch (IOException | RuntimeException e) {
                    logger.debug("Could not update {}: {}", path, e.getMessage());
                }
            }
            index.commit();
            logger.info("Updated {} changed file(s)", ready.size());
        } catch (Exception e) {
            logger.error("Watcher update failed: {}", e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        running = false;
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        watchService.close();
        logger.info("File system watcher stopped");
    }
}
