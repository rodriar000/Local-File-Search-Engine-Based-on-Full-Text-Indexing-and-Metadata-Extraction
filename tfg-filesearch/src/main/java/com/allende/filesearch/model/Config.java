package com.allende.filesearch.model;

import java.util.List;

/**
 * Configuration model loaded from YAML.
 */
public class Config {
    private ElasticsearchConfig elasticsearch;
    private IndexingConfig indexing;
    private SearchConfig search;
    private WatchConfig watch;
    private LoggingConfig logging;

    public Config() {
    }

    public ElasticsearchConfig getElasticsearch() {
        return elasticsearch;
    }

    public void setElasticsearch(ElasticsearchConfig elasticsearch) {
        this.elasticsearch = elasticsearch;
    }

    public IndexingConfig getIndexing() {
        return indexing;
    }

    public void setIndexing(IndexingConfig indexing) {
        this.indexing = indexing;
    }

    public SearchConfig getSearch() {
        return search;
    }

    public void setSearch(SearchConfig search) {
        this.search = search;
    }

    public WatchConfig getWatch() {
        return watch;
    }

    public void setWatch(WatchConfig watch) {
        this.watch = watch;
    }

    public LoggingConfig getLogging() {
        return logging;
    }

    public void setLogging(LoggingConfig logging) {
        this.logging = logging;
    }

    public static class ElasticsearchConfig {
        private String host = "localhost";
        private int port = 9200;
        private String scheme = "http";
        private String indexName = "filesearch";
        private int bulkSize = 100;
        private String refreshInterval = "5s";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getScheme() {
            return scheme;
        }

        public void setScheme(String scheme) {
            this.scheme = scheme;
        }

        public String getIndexName() {
            return indexName;
        }

        public void setIndexName(String indexName) {
            this.indexName = indexName;
        }

        public int getBulkSize() {
            return bulkSize;
        }

        public void setBulkSize(int bulkSize) {
            this.bulkSize = bulkSize;
        }

        public String getRefreshInterval() {
            return refreshInterval;
        }

        public void setRefreshInterval(String refreshInterval) {
            this.refreshInterval = refreshInterval;
        }
    }

    public static class IndexingConfig {
        private List<String> extensions;
        private int maxFileSizeMb = 100;
        private List<String> excludePatterns;
        private int threads = 4;

        public List<String> getExtensions() {
            return extensions;
        }

        public void setExtensions(List<String> extensions) {
            this.extensions = extensions;
        }

        public int getMaxFileSizeMb() {
            return maxFileSizeMb;
        }

        public void setMaxFileSizeMb(int maxFileSizeMb) {
            this.maxFileSizeMb = maxFileSizeMb;
        }

        public List<String> getExcludePatterns() {
            return excludePatterns;
        }

        public void setExcludePatterns(List<String> excludePatterns) {
            this.excludePatterns = excludePatterns;
        }

        public int getThreads() {
            return threads;
        }

        public void setThreads(int threads) {
            this.threads = threads;
        }
    }

    public static class SearchConfig {
        private int defaultSize = 10;
        private int maxSize = 100;
        private boolean highlight = true;

        public int getDefaultSize() {
            return defaultSize;
        }

        public void setDefaultSize(int defaultSize) {
            this.defaultSize = defaultSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public boolean isHighlight() {
            return highlight;
        }

        public void setHighlight(boolean highlight) {
            this.highlight = highlight;
        }
    }

    public static class WatchConfig {
        private boolean enabled = true;
        private int debounceMs = 1000;
        private int rescanIntervalMinutes = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getDebounceMs() {
            return debounceMs;
        }

        public void setDebounceMs(int debounceMs) {
            this.debounceMs = debounceMs;
        }

        public int getRescanIntervalMinutes() {
            return rescanIntervalMinutes;
        }

        public void setRescanIntervalMinutes(int rescanIntervalMinutes) {
            this.rescanIntervalMinutes = rescanIntervalMinutes;
        }
    }

    public static class LoggingConfig {
        private String level = "INFO";
        private String file = "logs/filesearch.log";

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }
    }
}
