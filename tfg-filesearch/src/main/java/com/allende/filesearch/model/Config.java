package com.allende.filesearch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Configuration model loaded from YAML.
 * Unknown keys (for example the old "elasticsearch" section) are ignored so
 * existing configuration files keep loading.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {
    private IndexConfig index = new IndexConfig();
    private IndexingConfig indexing = new IndexingConfig();
    private SearchConfig search = new SearchConfig();
    private WatchConfig watch = new WatchConfig();
    private LoggingConfig logging = new LoggingConfig();
    private OcrConfig ocr = new OcrConfig();

    public Config() {
    }

    public IndexConfig getIndex() {
        return index;
    }

    public void setIndex(IndexConfig index) {
        this.index = index;
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

    public OcrConfig getOcr() {
        return ocr;
    }

    public void setOcr(OcrConfig ocr) {
        this.ocr = ocr;
    }

    /** Text recognition for scanned documents, done by a local Tesseract installation. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OcrConfig {
        private boolean enabled = true;
        /** Tesseract language codes joined with "+", e.g. "spa" or "spa+eng". */
        private String language = "spa";
        /** Folder containing the tesseract executable; empty = look it up on the PATH. */
        private String tesseractPath;
        /** Longest time spent recognising one file. */
        private int timeoutSeconds = 300;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getTesseractPath() {
            return tesseractPath;
        }

        public void setTesseractPath(String tesseractPath) {
            this.tesseractPath = tesseractPath;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IndexConfig {
        /** Index location; null means the default under the application data folder. */
        private String directory;
        /** Documents indexed between intermediate commits during a sync. */
        private int commitEvery = 500;

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public int getCommitEvery() {
            return commitEvery;
        }

        public void setCommitEvery(int commitEvery) {
            this.commitEvery = commitEvery;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
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

    @JsonIgnoreProperties(ignoreUnknown = true)
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

    @JsonIgnoreProperties(ignoreUnknown = true)
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

    @JsonIgnoreProperties(ignoreUnknown = true)
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
