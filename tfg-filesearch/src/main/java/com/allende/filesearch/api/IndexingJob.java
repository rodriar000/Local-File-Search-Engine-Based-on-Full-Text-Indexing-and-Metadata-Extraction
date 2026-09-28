package com.allende.filesearch.api;

import com.allende.filesearch.analytics.AnalyticsManager;
import com.allende.filesearch.index.IndexSynchronizer;
import com.allende.filesearch.index.IndexSynchronizer.SyncReport;
import com.allende.filesearch.model.IndexingMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs one folder sync at a time in the background and exposes its progress.
 */
final class IndexingJob implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(IndexingJob.class);

    /** Snapshot of the current or last run, serialised as the status endpoint's body. */
    record Status(
            boolean running,
            String folder,
            int processed,
            int total,
            Instant startedAt,
            Instant finishedAt,
            SyncReport lastReport,
            String error) {
    }

    private final IndexSynchronizer synchronizer;
    private final AnalyticsManager analytics;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "indexing");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean cancel = new AtomicBoolean();
    private final AtomicInteger processed = new AtomicInteger();
    private final AtomicInteger total = new AtomicInteger();
    private boolean running;
    private String folder;
    private Instant startedAt;
    private Instant finishedAt;
    private SyncReport lastReport;
    private String error;

    IndexingJob(IndexSynchronizer synchronizer, AnalyticsManager analytics) {
        this.synchronizer = synchronizer;
        this.analytics = analytics;
    }

    /**
     * Starts syncing {@code root}; entries outside it are removed afterwards so
     * the index always reflects the one configured folder.
     *
     * @return false when a run is already in progress
     */
    synchronized boolean start(Path root) {
        if (running) {
            return false;
        }
        running = true;
        folder = root.toString();
        startedAt = Instant.now();
        finishedAt = null;
        error = null;
        cancel.set(false);
        processed.set(0);
        total.set(0);
        executor.submit(() -> run(root));
        return true;
    }

    void cancel() {
        cancel.set(true);
    }

    synchronized Status status() {
        return new Status(running, folder, processed.get(), total.get(), startedAt, finishedAt, lastReport, error);
    }

    private void run(Path root) {
        SyncReport report = null;
        String failure = null;
        try {
            IndexingMetrics metrics = new IndexingMetrics();
            report = synchronizer.sync(root, (done, of) -> {
                processed.set(done);
                total.set(of);
            }, cancel, metrics);
            metrics.stop();
            if (!report.cancelled()) {
                int removed = synchronizer.removeOutside(root);
                if (removed > 0) {
                    report = withExtraDeletions(report, removed);
                }
            }
            try {
                analytics.recordIndexingStats(metrics);
            } catch (RuntimeException e) {
                logger.debug("Could not record indexing stats: {}", e.getMessage());
            }
        } catch (Exception | LinkageError e) {
            failure = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            logger.error("Indexing failed: {}", e.getClass().getSimpleName());
        } finally {
            synchronized (this) {
                running = false;
                finishedAt = Instant.now();
                if (report != null) {
                    lastReport = report;
                }
                error = failure;
            }
        }
    }

    private static SyncReport withExtraDeletions(SyncReport r, int removed) {
        return new SyncReport(r.root(), r.scanned(), r.added(), r.updated(), r.unchanged(), r.deleted() + removed,
                r.skippedTooLarge(), r.withoutText(), r.failed(), r.failures(), r.durationMs(), r.cancelled());
    }

    @Override
    public void close() {
        cancel.set(true);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                logger.warn("Indexing did not stop in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
