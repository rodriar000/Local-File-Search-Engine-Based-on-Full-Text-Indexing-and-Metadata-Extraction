package com.allende.filesearch.cli;

import com.allende.filesearch.analytics.AnalyticsManager;
import com.allende.filesearch.model.Config;
import com.allende.filesearch.utils.ConfigLoader;
import com.allende.filesearch.elastic.ElasticsearchService;
import com.allende.filesearch.tika.DocumentExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class DependencyContainer {
    private static final Logger logger = LoggerFactory.getLogger(DependencyContainer.class);
    private static DependencyContainer instance;

    private final Config config;
    private final ElasticsearchService elasticsearchService;
    private final DocumentExtractor documentExtractor;
    private final AnalyticsManager analyticsManager;

    private DependencyContainer() throws IOException {
        logger.info("Initializing Dependency Container...");

        // 1. Load Configuration
        this.config = ConfigLoader.load();

        // 2. Initialize Services
        this.elasticsearchService = new ElasticsearchService(config);

        // Handle potential null indexing configuration
        java.util.List<String> extensions = (config.getIndexing() != null)
                ? config.getIndexing().getExtensions()
                : new java.util.ArrayList<>();

        this.documentExtractor = new DocumentExtractor(extensions);
        this.analyticsManager = new AnalyticsManager();

        logger.info("Dependency Container initialized successfully.");
    }

    public static synchronized DependencyContainer getInstance() throws IOException {
        if (instance == null) {
            instance = new DependencyContainer();
        }
        return instance;
    }

    public Config getConfig() {
        return config;
    }

    public ElasticsearchService getElasticsearchService() {
        return elasticsearchService;
    }

    public DocumentExtractor getDocumentExtractor() {
        return documentExtractor;
    }

    public AnalyticsManager getAnalyticsManager() {
        return analyticsManager;
    }

    public void close() {
        try {
            if (elasticsearchService != null) {
                elasticsearchService.close();
            }
        } catch (IOException e) {
            logger.error("Error closing resources", e);
        }
    }
}
