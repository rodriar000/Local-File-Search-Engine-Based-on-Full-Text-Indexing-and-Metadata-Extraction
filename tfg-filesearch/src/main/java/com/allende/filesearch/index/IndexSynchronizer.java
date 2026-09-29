package com.allende.filesearch.index;

import com.allende.filesearch.model.Config;
import com.allende.filesearch.model.Document;
import com.allende.filesearch.model.IndexingMetrics;
import com.allende.filesearch.tika.DocumentExtractor;
import com.allende.filesearch.tika.OcrSupport;
import com.allende.filesearch.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Brings the index in line with a folder: extracts new and changed files,
 * skips unchanged ones (same size and modification time) and removes files
 * that were deleted, renamed, excluded or are no longer supported.
 */
public final class IndexSynchronizer {
    private static final Logger logger = LoggerFactory.getLogger(IndexSynchronizer.class);
    /** Failures kept in the report; the counters still count all of them. */
    static final int MAX_REPORTED_FAILURES = 200;

    /** Receives progress while files are extracted. Called from worker threads. */
    public interface ProgressListener {
        void onProgress(int processed, int total);

        ProgressListener NONE = (processed, total) -> { };
    }

    public record FailedFile(String path, String reason) {
    }

    /**
     * Outcome of a sync.
     *
     * @param withoutText files indexed by name only because no text could be extracted
     *                    (typically scanned PDFs until OCR is available)
     */
    public record SyncReport(
            String root,
            int scanned,
            int added,
            int updated,
            int unchanged,
            int deleted,
            int skippedTooLarge,
            int withoutText,
            int failed,
            List<FailedFile> failures,
            long durationMs,
            boolean cancelled) {
    }

    private final DocumentIndex index;
    private final DocumentExtractor extractor;
    private final ExclusionRules exclusions;
    private final long maxFileSizeBytes;
    private final int threads;
    private final int commitEvery;

    /** Whether scanned documents can be read, for the support information. */
    public OcrSupport.Status ocrStatus() {
        return extractor.ocrStatus();
    }

    public IndexSynchronizer(DocumentIndex index, DocumentExtractor extractor, Config config) {
        this.index = index;
        this.extractor = extractor;
        this.exclusions = new ExclusionRules(config.getIndexing().getExcludePatterns());
        this.maxFileSizeBytes = config.getIndexing().getMaxFileSizeMb() * 1024L * 1024L;
        this.threads = Math.max(1, config.getIndexing().getThreads());
        this.commitEvery = Math.max(1, config.getIndex().getCommitEvery());
    }

    public SyncReport sync(Path folder, ProgressListener listener, AtomicBoolean cancel, IndexingMetrics metrics)
            throws IOException {
        long start = System.currentTimeMillis();
        Path root = folder.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new NoSuchFileException(root.toString(), null, "not a folder");
        }

        Map<String, FileState> indexed = index.fileStates(root);
        List<FailedFile> failures = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger failed = new AtomicInteger();
        Scan scan = scan(root, failures, failed);

        List<Path> toExtract = new ArrayList<>();
        int unchanged = 0;
        for (Map.Entry<Path, FileState> candidate : scan.files.entrySet()) {
            FileState known = indexed.get(candidate.getKey().toString());
            if (isUpToDate(known, candidate.getValue())) {
                unchanged++;
            } else {
                toExtract.add(candidate.getKey());
            }
        }

        AtomicInteger added = new AtomicInteger();
        AtomicInteger updated = new AtomicInteger();
        AtomicInteger withoutText = new AtomicInteger();
        AtomicInteger processed = new AtomicInteger();
        AtomicInteger sinceCommit = new AtomicInteger();
        int total = toExtract.size();
        listener.onProgress(0, total);

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(threads, Math.max(1, total)));
        try {
            for (Path file : toExtract) {
                pool.submit(() -> {
                    if (cancel.get()) {
                        return;
                    }
                    String key = file.toString();
                    try {
                        long started = System.currentTimeMillis();
                        Document doc = extractor.extractDocument(file);
                        index.upsert(doc);
                        if (doc.getExtractionError() != null) {
                            // Still indexed, so it can be found by name; reported so the user knows why.
                            recordFailure(failures, failed, key, doc.getExtractionError());
                        } else if (doc.getContent() == null || doc.getContent().isBlank()) {
                            withoutText.incrementAndGet();
                        }
                        (indexed.containsKey(key) ? updated : added).incrementAndGet();
                        if (metrics != null) {
                            metrics.addBytes(doc.getSize());
                            metrics.incrementDocuments(1);
                            metrics.recordFileProcessing(doc.getExtension() == null ? "" : doc.getExtension(),
                                    System.currentTimeMillis() - started);
                        }
                        if (sinceCommit.incrementAndGet() % commitEvery == 0) {
                            index.commit();
                        }
                    } catch (Exception | LinkageError e) {
                        recordFailure(failures, failed, key, e);
                        if (metrics != null) {
                            metrics.incrementErrors();
                        }
                    } finally {
                        listener.onProgress(processed.incrementAndGet(), total);
                    }
                });
            }
        } finally {
            pool.shutdown();
            awaitQuietly(pool);
        }

        if (!cancel.get()) {
            int refreshed = index.refreshEntities(root, cancel::get);
            if (refreshed > 0) {
                logger.info("Updated the personal data found in {} documents already indexed", refreshed);
            }
        }

        int deleted = 0;
        if (!cancel.get()) {
            Set<String> present = new HashSet<>();
            scan.files.keySet().forEach(path -> present.add(path.toString()));
            for (String path : indexed.keySet()) {
                // Keep entries under paths we could not read this time (e.g. a locked folder).
                if (!present.contains(path) && !scan.isUnderUnreadable(path)) {
                    index.delete(path);
                    deleted++;
                }
            }
        }
        index.commit();

        return new SyncReport(root.toString(), scan.files.size(), added.get(), updated.get(), unchanged, deleted,
                scan.tooLarge, withoutText.get(), failed.get(), List.copyOf(failures),
                System.currentTimeMillis() - start, cancel.get());
    }

    /**
     * Updates a single path after a file-system event: indexes it when it is a
     * supported file, removes it from the index otherwise.
     *
     * @return true when the index changed
     */
    public boolean syncFile(Path path) throws IOException {
        Path file = path.toAbsolutePath().normalize();
        BasicFileAttributes attrs = readAttributes(file);
        if (attrs == null || !attrs.isRegularFile() || !isCandidate(file, attrs)) {
            index.delete(file.toString());
            return true;
        }
        index.upsert(extractor.extractDocument(file));
        return true;
    }

    /**
     * Removes every indexed file outside {@code folder}, e.g. after the user
     * switched to a different documents folder.
     *
     * @return number of removed entries
     */
    public int removeOutside(Path folder) throws IOException {
        String prefix = folder.toAbsolutePath().normalize().toString();
        int removed = 0;
        for (String path : index.fileStates(null).keySet()) {
            if (!path.equals(prefix) && !path.startsWith(prefix + java.io.File.separator)) {
                index.delete(path);
                removed++;
            }
        }
        if (removed > 0) {
            index.commit();
        }
        return removed;
    }

    /**
     * An indexed entry can be kept when the file has not changed and it was
     * extracted by the current extractor, unless it has no text and OCR has
     * become available since (a scanned document that can now be read).
     */
    private boolean isUpToDate(FileState known, FileState current) {
        if (known == null || !known.sameFileAs(current.size(), current.modifiedAtMillis())) {
            return false;
        }
        if (known.extractorVersion() < DocumentExtractor.VERSION) {
            return false;
        }
        return known.hasText() || known.ocrAvailable() || !extractor.isOcrAvailable();
    }

    public ExclusionRules exclusions() {
        return exclusions;
    }

    private boolean isCandidate(Path file, BasicFileAttributes attrs) {
        return !exclusions.isExcludedFile(file)
                && extractor.isSupported(FileUtils.getExtension(file.getFileName().toString()))
                && attrs.size() <= maxFileSizeBytes;
    }

    private static final class Scan {
        final Map<Path, FileState> files = new java.util.LinkedHashMap<>();
        final Set<String> unreadable = new HashSet<>();
        int tooLarge;

        boolean isUnderUnreadable(String path) {
            for (String blocked : unreadable) {
                if (path.equals(blocked) || path.startsWith(blocked + java.io.File.separator)) {
                    return true;
                }
            }
            return false;
        }
    }

    private Scan scan(Path root, List<FailedFile> failures, AtomicInteger failed) throws IOException {
        Scan scan = new Scan();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return !dir.equals(root) && exclusions.isExcludedFolder(dir)
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!attrs.isRegularFile() || exclusions.isExcludedFile(file)
                        || !extractor.isSupported(FileUtils.getExtension(file.getFileName().toString()))) {
                    return FileVisitResult.CONTINUE;
                }
                if (attrs.size() > maxFileSizeBytes) {
                    scan.tooLarge++;
                    return FileVisitResult.CONTINUE;
                }
                scan.files.put(file, new FileState(attrs.size(), attrs.lastModifiedTime().toMillis(), 0, false, false));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                // Permission denied, file vanished while scanning, broken share...
                scan.unreadable.add(file.toString());
                recordFailure(failures, failed, file.toString(), e);
                return FileVisitResult.CONTINUE;
            }
        });
        return scan;
    }

    private static BasicFileAttributes readAttributes(Path file) {
        try {
            return Files.readAttributes(file, BasicFileAttributes.class);
        } catch (IOException e) {
            return null;
        }
    }

    private static void recordFailure(List<FailedFile> failures, AtomicInteger failed, String path, Throwable e) {
        logger.debug("Could not index {}", path, e);
        recordFailure(failures, failed, path, describe(e));
    }

    /** Why a file could not be indexed, in words a user can act on (the details go to the debug log). */
    static String describe(Throwable e) {
        if (e instanceof AccessDeniedException) {
            return "Sin permiso para leer el archivo";
        }
        if (e instanceof NoSuchFileException) {
            return "El archivo ya no existe";
        }
        if (e instanceof FileSystemException) {
            return "El archivo está en uso por otro programa o no se puede abrir";
        }
        if (e instanceof IOException) {
            return "Error al leer el archivo";
        }
        return DocumentExtractor.UNREADABLE;
    }

    private static void recordFailure(List<FailedFile> failures, AtomicInteger failed, String path, String reason) {
        failed.incrementAndGet();
        if (failures.size() < MAX_REPORTED_FAILURES) {
            failures.add(new FailedFile(path, reason));
        }
        // File names can identify clients: keep them out of INFO/WARN logs.
        logger.debug("Could not index {}: {}", path, reason);
    }

    private static void awaitQuietly(ExecutorService pool) {
        try {
            while (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
                logger.debug("Still extracting documents...");
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
