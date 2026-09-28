package com.allende.filesearch.cli;

import com.allende.filesearch.analytics.AnalyticsManager;
import com.allende.filesearch.index.AppPaths;
import com.allende.filesearch.index.DocumentIndex;
import com.allende.filesearch.model.Config;
import com.allende.filesearch.tika.DocumentExtractor;
import com.allende.filesearch.utils.ConfigLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Shared services for CLI commands. The index itself is opened per command,
 * read-only for searches and writable for indexing, so searching keeps working
 * while another process updates the index.
 */
public class DependencyContainer {
    private static DependencyContainer instance;

    private final Config config;
    private final DocumentExtractor documentExtractor;
    private final AnalyticsManager analyticsManager;

    private DependencyContainer() {
        this.config = ConfigLoader.load();
        List<String> extensions = config.getIndexing().getExtensions();
        this.documentExtractor = new DocumentExtractor(
                extensions != null && !extensions.isEmpty() ? extensions : DocumentExtractor.DEFAULT_EXTENSIONS,
                config.getOcr());
        this.analyticsManager = new AnalyticsManager();
    }

    public static synchronized DependencyContainer getInstance() {
        if (instance == null) {
            instance = new DependencyContainer();
        }
        return instance;
    }

    public Config getConfig() {
        return config;
    }

    public DocumentExtractor getDocumentExtractor() {
        return documentExtractor;
    }

    public AnalyticsManager getAnalyticsManager() {
        return analyticsManager;
    }

    public Path getIndexDirectory() {
        return AppPaths.indexDirectory(config);
    }

    public DocumentIndex openIndexForWriting() throws IOException {
        return DocumentIndex.openForWriting(getIndexDirectory());
    }

    public DocumentIndex openIndexReadOnly() throws IOException {
        return DocumentIndex.openReadOnly(getIndexDirectory());
    }
}
