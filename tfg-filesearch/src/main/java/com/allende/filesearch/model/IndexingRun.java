package com.allende.filesearch.model;

import java.time.Instant;

public class IndexingRun {
    private Instant timestamp;
    private long durationMs;
    private int totalDocuments;
    private double docsPerSecond;
    private long totalBytes;

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public int getTotalDocuments() {
        return totalDocuments;
    }

    public void setTotalDocuments(int totalDocuments) {
        this.totalDocuments = totalDocuments;
    }

    public double getDocsPerSecond() {
        return docsPerSecond;
    }

    public void setDocsPerSecond(double docsPerSecond) {
        this.docsPerSecond = docsPerSecond;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }
}
