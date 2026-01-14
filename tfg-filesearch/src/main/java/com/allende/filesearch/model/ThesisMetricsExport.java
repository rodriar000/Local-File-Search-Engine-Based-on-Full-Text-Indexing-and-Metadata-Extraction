package com.allende.filesearch.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ThesisMetricsExport {
    private Instant generatedAt;
    private SystemSummary systemSummary;
    private IndexingAggregates indexingStats;
    private SearchAggregates searchStats;

    public ThesisMetricsExport() {
        this.generatedAt = Instant.now();
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public SystemSummary getSystemSummary() {
        return systemSummary;
    }

    public void setSystemSummary(SystemSummary systemSummary) {
        this.systemSummary = systemSummary;
    }

    public IndexingAggregates getIndexingStats() {
        return indexingStats;
    }

    public void setIndexingStats(IndexingAggregates indexingStats) {
        this.indexingStats = indexingStats;
    }

    public SearchAggregates getSearchStats() {
        return searchStats;
    }

    public void setSearchStats(SearchAggregates searchStats) {
        this.searchStats = searchStats;
    }

    // Nested Classes

    public static class SystemSummary {
        public int totalIndexRuns;
        public long totalSearches;
        public int currentTotalDocuments;
    }

    public static class IndexingAggregates {
        public double averageDurationMs;
        public long maxDurationMs;
        public long minDurationMs;
        public double averageDocsPerSecond;
        public double maxDocsPerSecond;
        public List<IndexingRun> runHistory;
    }

    public static class SearchAggregates {
        public double globalAverageLatencyMs;
        public double maxRecordedAverageLatencyMs;
        public double minRecordedAverageLatencyMs;
        public Map<String, Integer> topTerms;
        public List<SearchHistoryPoint> latencyEvolution;
    }
}
