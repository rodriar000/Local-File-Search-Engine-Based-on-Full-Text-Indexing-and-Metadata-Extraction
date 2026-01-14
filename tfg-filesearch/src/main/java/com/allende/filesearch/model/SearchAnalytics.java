package com.allende.filesearch.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Persisted search statistics model.
 */
public class SearchAnalytics {
    private long totalSearches = 0;
    private long totalSearchTimeMs = 0;
    private Map<String, Integer> termFrequency = new HashMap<>(); // Top terms
    private Map<String, Integer> resultDistribution = new HashMap<>(); // "0", "1-10", "11-50", "50+"
    private java.util.List<SearchHistoryPoint> history = new java.util.ArrayList<>();

    public SearchAnalytics() {
        resultDistribution.put("0", 0);
        resultDistribution.put("1-10", 0);
        resultDistribution.put("11-50", 0);
        resultDistribution.put("50+", 0);
    }

    public long getTotalSearches() {
        return totalSearches;
    }

    public void setTotalSearches(long totalSearches) {
        this.totalSearches = totalSearches;
    }

    public long getTotalSearchTimeMs() {
        return totalSearchTimeMs;
    }

    public void setTotalSearchTimeMs(long totalSearchTimeMs) {
        this.totalSearchTimeMs = totalSearchTimeMs;
    }

    public Map<String, Integer> getTermFrequency() {
        return termFrequency;
    }

    public void setTermFrequency(Map<String, Integer> termFrequency) {
        this.termFrequency = termFrequency;
    }

    public Map<String, Integer> getResultDistribution() {
        return resultDistribution;
    }

    public void setResultDistribution(Map<String, Integer> resultDistribution) {
        this.resultDistribution = resultDistribution;
    }

    public java.util.List<SearchHistoryPoint> getHistory() {
        return history;
    }

    public void setHistory(java.util.List<SearchHistoryPoint> history) {
        this.history = history;
    }
}
