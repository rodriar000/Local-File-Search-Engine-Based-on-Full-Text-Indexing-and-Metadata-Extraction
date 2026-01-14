package com.allende.filesearch.utils;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects basic performance metrics.
 */
public class MetricsCollector {
    private final AtomicLong documentsIndexed = new AtomicLong(0);
    private final AtomicLong documentsSearched = new AtomicLong(0);
    private final AtomicLong indexingErrors = new AtomicLong(0);
    private final AtomicLong totalIndexingTimeMs = new AtomicLong(0);
    private final AtomicLong totalSearchTimeMs = new AtomicLong(0);

    public void recordDocumentIndexed() {
        documentsIndexed.incrementAndGet();
    }

    public void recordDocumentSearched() {
        documentsSearched.incrementAndGet();
    }

    public void recordIndexingError() {
        indexingErrors.incrementAndGet();
    }

    public void recordIndexingTime(long ms) {
        totalIndexingTimeMs.addAndGet(ms);
    }

    public void recordSearchTime(long ms) {
        totalSearchTimeMs.addAndGet(ms);
    }

    public long getDocumentsIndexed() {
        return documentsIndexed.get();
    }

    public long getDocumentsSearched() {
        return documentsSearched.get();
    }

    public long getIndexingErrors() {
        return indexingErrors.get();
    }

    public double getAverageIndexingTimeMs() {
        long total = totalIndexingTimeMs.get();
        long count = documentsIndexed.get();
        return count > 0 ? (double) total / count : 0.0;
    }

    public double getAverageSearchTimeMs() {
        long total = totalSearchTimeMs.get();
        long count = documentsSearched.get();
        return count > 0 ? (double) total / count : 0.0;
    }

    public void reset() {
        documentsIndexed.set(0);
        documentsSearched.set(0);
        indexingErrors.set(0);
        totalIndexingTimeMs.set(0);
        totalSearchTimeMs.set(0);
    }

    @Override
    public String toString() {
        return String.format(
                "Metrics [Indexed: %d, Searched: %d, Errors: %d, Avg Index Time: %.2fms, Avg Search Time: %.2fms]",
                documentsIndexed.get(), documentsSearched.get(), indexingErrors.get(),
                getAverageIndexingTimeMs(), getAverageSearchTimeMs());
    }
}
