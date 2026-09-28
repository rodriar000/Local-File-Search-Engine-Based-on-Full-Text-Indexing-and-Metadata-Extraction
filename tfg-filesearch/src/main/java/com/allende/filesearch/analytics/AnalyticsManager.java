package com.allende.filesearch.analytics;

import com.allende.filesearch.model.IndexingStats;
import com.allende.filesearch.model.SearchAnalytics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Manages search analytics persistence and updates.
 */
public class AnalyticsManager {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsManager.class);
    private static final String SEARCH_STATS_FILENAME = "search_stats.json";
    private static final String INDEXING_STATS_FILENAME = "indexing_stats.json";
    private final Path searchStatsPath;
    private final Path indexingStatsPath;
    private final ObjectMapper mapper;

    private SearchAnalytics analytics;
    private IndexingStats indexingStats;

    public AnalyticsManager() {
        Path baseDir = com.allende.filesearch.index.AppPaths.dataHome();
        this.searchStatsPath = baseDir.resolve(SEARCH_STATS_FILENAME);
        this.indexingStatsPath = baseDir.resolve(INDEXING_STATS_FILENAME);
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.mapper.registerModule(new JavaTimeModule());
        load();
    }

    public synchronized void recordSearch(String term, long executionTimeMs, long resultCount) {
        analytics.setTotalSearches(analytics.getTotalSearches() + 1);
        analytics.setTotalSearchTimeMs(analytics.getTotalSearchTimeMs() + executionTimeMs);

        // Search terms are never stored: they can name clients or matters.

        // Update distribution bucket
        String bucket = getBucket(resultCount);
        // Explicit lambda to avoid "needs unchecked conversion" warning with method
        // reference
        analytics.getResultDistribution().merge(bucket, 1, (Integer a, Integer b) -> Integer.sum(a, b));

        /*
         * Statistical Aggregation: Moving Average
         * ---------------------------------------
         * We compute the running average of search latency to track system performance
         * degradation over time.
         * This historical data (SearchHistoryPoint) is critical for regression testing
         * and capacity planning.
         */
        double avgTime = analytics.getTotalSearches() > 0
                ? (double) analytics.getTotalSearchTimeMs() / analytics.getTotalSearches()
                : 0;

        analytics.getHistory()
                .add(new com.allende.filesearch.model.SearchHistoryPoint(java.time.Instant.now(), avgTime));

        // Enforce sliding window of size 50 to maintain constant memory footprint
        if (analytics.getHistory().size() > 50) {
            analytics.getHistory().remove(0);
        }

        save();
    }

    /**
     * Persists indexing performance metrics for longitudinal analysis.
     * Records ingestion throughput (Docs/S) and volume to correlate performance
     * with dataset size changes.
     */
    public synchronized void recordIndexingStats(com.allende.filesearch.model.IndexingMetrics metrics) {
        if (indexingStats == null) {
            indexingStats = new com.allende.filesearch.model.IndexingStats();
        }

        com.allende.filesearch.model.IndexingRun run = new com.allende.filesearch.model.IndexingRun();
        run.setTimestamp(java.time.Instant.now());
        run.setDurationMs(metrics.getDurationMs());
        run.setTotalDocuments(metrics.getTotalDocuments());
        run.setDocsPerSecond(metrics.getDocumentsPerSecond());
        run.setTotalBytes(metrics.getTotalBytes());

        indexingStats.getHistory().add(run);
        if (indexingStats.getHistory().size() > 50) {
            indexingStats.getHistory().remove(0);
        }

        saveIndexingStats();
    }

    public SearchAnalytics getStats() {
        return analytics;
    }

    public com.allende.filesearch.model.IndexingStats getIndexingStats() {
        return indexingStats;
    }

    public synchronized com.allende.filesearch.model.ThesisMetricsExport generateExport() {
        com.allende.filesearch.model.ThesisMetricsExport export = new com.allende.filesearch.model.ThesisMetricsExport();

        // System Summary
        com.allende.filesearch.model.ThesisMetricsExport.SystemSummary summary = new com.allende.filesearch.model.ThesisMetricsExport.SystemSummary();
        summary.totalIndexRuns = indexingStats != null ? indexingStats.getHistory().size() : 0;
        summary.totalSearches = analytics.getTotalSearches();
        summary.currentTotalDocuments = indexingStats != null && indexingStats.getLatest() != null
                ? indexingStats.getLatest().getTotalDocuments()
                : 0;
        export.setSystemSummary(summary);

        // Indexing Aggregates
        if (indexingStats != null && !indexingStats.getHistory().isEmpty()) {
            com.allende.filesearch.model.ThesisMetricsExport.IndexingAggregates agg = new com.allende.filesearch.model.ThesisMetricsExport.IndexingAggregates();
            java.util.List<com.allende.filesearch.model.IndexingRun> history = indexingStats.getHistory();

            agg.runHistory = history;
            agg.averageDurationMs = history.stream().mapToLong(com.allende.filesearch.model.IndexingRun::getDurationMs)
                    .average().orElse(0);
            agg.maxDurationMs = history.stream().mapToLong(com.allende.filesearch.model.IndexingRun::getDurationMs)
                    .max().orElse(0);
            agg.minDurationMs = history.stream().mapToLong(com.allende.filesearch.model.IndexingRun::getDurationMs)
                    .min().orElse(0);

            agg.averageDocsPerSecond = history.stream()
                    .mapToDouble(com.allende.filesearch.model.IndexingRun::getDocsPerSecond).average().orElse(0);
            agg.maxDocsPerSecond = history.stream()
                    .mapToDouble(com.allende.filesearch.model.IndexingRun::getDocsPerSecond).max().orElse(0);

            export.setIndexingStats(agg);
        }

        // Search Aggregates
        com.allende.filesearch.model.ThesisMetricsExport.SearchAggregates sAgg = new com.allende.filesearch.model.ThesisMetricsExport.SearchAggregates();
        sAgg.topTerms = analytics.getTermFrequency();
        sAgg.latencyEvolution = analytics.getHistory();

        if (!analytics.getHistory().isEmpty()) {
            java.util.List<com.allende.filesearch.model.SearchHistoryPoint> history = analytics.getHistory();
            sAgg.globalAverageLatencyMs = history.stream()
                    .mapToDouble(com.allende.filesearch.model.SearchHistoryPoint::getAvgTimeMs).average().orElse(0);
            sAgg.maxRecordedAverageLatencyMs = history.stream()
                    .mapToDouble(com.allende.filesearch.model.SearchHistoryPoint::getAvgTimeMs).max().orElse(0);
            sAgg.minRecordedAverageLatencyMs = history.stream()
                    .mapToDouble(com.allende.filesearch.model.SearchHistoryPoint::getAvgTimeMs).min().orElse(0);
        }
        export.setSearchStats(sAgg);

        return export;
    }

    private String getBucket(long count) {
        if (count == 0)
            return "0";
        if (count <= 10)
            return "1-10";
        if (count <= 50)
            return "11-50";
        return "50+";
    }

    private void load() {
        loadSearchStats();
        loadIndexingStats();
    }

    private void loadSearchStats() {
        File file = searchStatsPath.toFile();
        if (file.exists()) {
            try {
                analytics = mapper.readValue(file, SearchAnalytics.class);
            } catch (IOException e) {
                logger.error("Failed to load search analytics, starting fresh", e);
                analytics = new SearchAnalytics();
            }
        } else {
            analytics = new SearchAnalytics();
        }
        if (analytics.getTermFrequency() != null && !analytics.getTermFrequency().isEmpty()) {
            // Older versions stored search terms; drop them.
            analytics.getTermFrequency().clear();
            save();
        }
    }

    private void loadIndexingStats() {
        File file = indexingStatsPath.toFile();
        if (file.exists()) {
            try {
                indexingStats = mapper.readValue(file, IndexingStats.class);
            } catch (IOException e) {
                logger.error("Failed to load indexing stats, starting fresh", e);
                indexingStats = new IndexingStats();
            }
        } else {
            indexingStats = new IndexingStats();
        }
    }

    private void save() {
        saveSearchStats();
    }

    private void saveSearchStats() {
        try {
            ensureParentDir(searchStatsPath);
            mapper.writeValue(searchStatsPath.toFile(), analytics);
        } catch (IOException e) {
            logger.error("Failed to save search analytics", e);
        }
    }

    private void saveIndexingStats() {
        try {
            ensureParentDir(indexingStatsPath);
            mapper.writeValue(indexingStatsPath.toFile(), indexingStats);
        } catch (IOException e) {
            logger.error("Failed to save indexing stats", e);
        }
    }

    private void ensureParentDir(Path path) {
        File parent = path.getParent().toFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }
    }
}
