package com.allende.filesearch.cli;

import com.allende.filesearch.index.DocumentIndex;
import com.allende.filesearch.index.IndexSynchronizer;
import com.allende.filesearch.index.IndexSynchronizer.SyncReport;
import com.allende.filesearch.model.IndexingMetrics;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs a folder sync for the CLI and prints a readable report. */
final class IndexingRunner {
    private static final int FAILURES_SHOWN = 20;

    private IndexingRunner() {
    }

    static SyncReport run(DocumentIndex index, Path folder, PrintStream out) throws IOException {
        DependencyContainer container = DependencyContainer.getInstance();
        IndexSynchronizer synchronizer = new IndexSynchronizer(
                index, container.getDocumentExtractor(), container.getConfig());
        IndexingMetrics metrics = new IndexingMetrics();

        SyncReport report = synchronizer.sync(folder, new ConsoleProgress(out), new AtomicBoolean(false), metrics);
        metrics.stop();
        try {
            container.getAnalyticsManager().recordIndexingStats(metrics);
        } catch (RuntimeException e) {
            // Analytics are informative only; never fail an indexing run because of them.
        }
        print(report, out);
        return report;
    }

    static void print(SyncReport report, PrintStream out) {
        out.println();
        out.println("=== Indexing report ===");
        out.println("Folder:          " + report.root());
        out.println("Supported files: " + report.scanned());
        out.println("Added:           " + report.added());
        out.println("Updated:         " + report.updated());
        out.println("Unchanged:       " + report.unchanged());
        out.println("Removed:         " + report.deleted());
        if (report.skippedTooLarge() > 0) {
            out.println("Too large:       " + report.skippedTooLarge() + " (see indexing.maxFileSizeMb)");
        }
        if (report.withoutText() > 0) {
            out.println("Without text:    " + report.withoutText() + " (searchable by name only; scanned PDFs need OCR)");
        }
        out.println("Failed:          " + report.failed());
        out.printf("Time:            %.1f s%n", report.durationMs() / 1000.0);
        if (!report.failures().isEmpty()) {
            out.println();
            out.println("Could not index:");
            report.failures().stream().limit(FAILURES_SHOWN)
                    .forEach(f -> out.println("  " + f.path() + " - " + f.reason()));
            if (report.failed() > FAILURES_SHOWN) {
                out.println("  ... and " + (report.failed() - FAILURES_SHOWN) + " more");
            }
        }
        out.println("=======================");
    }

    /** Prints at most one progress line per second. */
    private static final class ConsoleProgress implements IndexSynchronizer.ProgressListener {
        private final PrintStream out;
        private volatile long lastPrinted;

        ConsoleProgress(PrintStream out) {
            this.out = out;
        }

        @Override
        public void onProgress(int processed, int total) {
            long now = System.currentTimeMillis();
            if (processed == total || now - lastPrinted >= 1000) {
                lastPrinted = now;
                out.println("Indexed " + processed + " / " + total + " new or changed files");
            }
        }
    }
}
