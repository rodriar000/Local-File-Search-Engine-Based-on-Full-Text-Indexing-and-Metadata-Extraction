package com.allende.filesearch.model;

import java.time.Instant;

public class SearchHistoryPoint {
    private Instant timestamp;
    private double avgTimeMs;

    public SearchHistoryPoint() {
    }

    public SearchHistoryPoint(Instant timestamp, double avgTimeMs) {
        this.timestamp = timestamp;
        this.avgTimeMs = avgTimeMs;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public double getAvgTimeMs() {
        return avgTimeMs;
    }

    public void setAvgTimeMs(double avgTimeMs) {
        this.avgTimeMs = avgTimeMs;
    }
}
