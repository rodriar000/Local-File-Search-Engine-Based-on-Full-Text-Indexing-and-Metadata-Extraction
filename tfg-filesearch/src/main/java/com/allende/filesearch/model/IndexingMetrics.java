package com.allende.filesearch.model;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Captures and calculates metrics for the indexing process.
 * Thread-safe for parallel stream usage.
 */
public class IndexingMetrics {
    private final long startTime;
    private long endTime;
    private final AtomicInteger totalDocuments = new AtomicInteger(0);
    private final AtomicLong totalBytes = new AtomicLong(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);

    private final java.util.concurrent.ConcurrentHashMap<String, FileTypeStats> fileStats = new java.util.concurrent.ConcurrentHashMap<>();

    public IndexingMetrics() {
        this.startTime = System.currentTimeMillis();
    }

    public void stop() {
        this.endTime = System.currentTimeMillis();
    }

    public void incrementDocuments(int count) {
        totalDocuments.addAndGet(count);
    }

    public void addBytes(long bytes) {
        totalBytes.addAndGet(bytes);
    }

    public void incrementErrors() {
        errorCount.incrementAndGet();
    }

    public void recordFileProcessing(String extension, long durationMs) {
        String key = extension.toLowerCase();
        fileStats.computeIfAbsent(key, k -> new FileTypeStats()).record(durationMs);
    }

    // --- Calculated Metrics ---

    public long getDurationMs() {
        long end = (endTime > 0) ? endTime : System.currentTimeMillis();
        return Math.max(1, end - startTime);
    }

    public double getDurationSeconds() {
        return getDurationMs() / 1000.0;
    }

    public int getTotalDocuments() {
        return totalDocuments.get();
    }

    public long getTotalBytes() {
        return totalBytes.get();
    }

    public int getErrorCount() {
        return errorCount.get();
    }

    public double getDocumentsPerSecond() {
        return getTotalDocuments() / getDurationSeconds();
    }

    public double getAverageTimePerDocumentMs() {
        int docs = getTotalDocuments();
        if (docs == 0)
            return 0.0;
        return (double) getDurationMs() / docs;
    }

    public double getMegabytesPerSecond() {
        double mb = getTotalBytes() / (1024.0 * 1024.0);
        return mb / getDurationSeconds();
    }

    public String getFormattedReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== Indexing Metrics ===\n");
        sb.append(String.format("Total Time:        %.2f s\n", getDurationSeconds()));
        sb.append(String.format("Documents:         %d\n", getTotalDocuments()));
        sb.append(String.format("Data Processed:    %.2f MB\n", getTotalBytes() / (1024.0 * 1024.0)));
        sb.append(String.format("Errors:            %d\n", getErrorCount()));
        sb.append("------------------------\n");
        sb.append(String.format("Throughput:        %.2f docs/s\n", getDocumentsPerSecond()));
        sb.append(String.format("Speed:             %.2f MB/s\n", getMegabytesPerSecond()));
        sb.append(String.format("Avg Time/Doc:      %.2f ms\n", getAverageTimePerDocumentMs()));
        sb.append("------------------------\n");
        sb.append("File Type Breakdown:\n");

        // Sort by count descending
        fileStats.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().count.get(), e1.getValue().count.get()))
                .forEach(e -> {
                    FileTypeStats s = e.getValue();
                    int count = s.count.get();
                    long time = s.totalTime.get();
                    double avg = count > 0 ? (double) time / count : 0;
                    double percent = getTotalDocuments() > 0 ? (100.0 * count / getTotalDocuments()) : 0;

                    sb.append(String.format("  .%-4s: %5d docs (%5.1f%%) | Total: %6d ms | Avg: %5.1f ms/doc\n",
                            e.getKey(), count, percent, time, avg));
                });

        sb.append("========================\n");
        return sb.toString();
    }

    /**
     * Internal container for thread-safe aggregation of file type statistics.
     * Uses atomic primitives to ensure data integrity during concurrent indexing
     * operations.
     */
    private static class FileTypeStats {
        final AtomicInteger count = new AtomicInteger(0);
        final AtomicLong totalTime = new AtomicLong(0);

        void record(long duration) {
            count.incrementAndGet();
            totalTime.addAndGet(duration);
        }
    }
}
