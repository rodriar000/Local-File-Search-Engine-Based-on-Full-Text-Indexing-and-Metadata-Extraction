package com.allende.filesearch.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.*;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.allende.filesearch.model.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Manages Elasticsearch index creation and mappings.
 */
public class IndexManager {
    private static final Logger logger = LoggerFactory.getLogger(IndexManager.class);
    private final ElasticsearchClient client;
    private final Config config;

    public IndexManager(ElasticsearchClient client, Config config) {
        this.client = client;
        this.config = config;
    }

    /**
     * Create index with proper mappings for document fields.
     */
    public void createIndex(String indexName) throws IOException {
        // Check if index already exists
        boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
        if (exists) {
            logger.info("Index '{}' already exists", indexName);
            return;
        }

        logger.info("Creating index '{}'", indexName);

        // Define mappings
        TypeMapping mapping = TypeMapping.of(m -> m
                .properties(Map.ofEntries(
                        // Path and filename
                        Map.entry("path", Property.of(p -> p.keyword(KeywordProperty.of(k -> k)))),
                        Map.entry("filename", Property.of(p -> p.text(TextProperty.of(t -> t
                                .fields("keyword", Property.of(f -> f.keyword(KeywordProperty.of(k -> k)))))))),
                        Map.entry("extension", Property.of(p -> p.keyword(KeywordProperty.of(k -> k)))),

                        // File metadata
                        Map.entry("size", Property.of(p -> p.long_(LongNumberProperty.of(l -> l)))),
                        Map.entry("created_at", Property.of(p -> p.date(DateProperty.of(d -> d)))),
                        Map.entry("modified_at", Property.of(p -> p.date(DateProperty.of(d -> d)))),
                        Map.entry("checksum_sha256", Property.of(p -> p.keyword(KeywordProperty.of(k -> k)))),

                        // Content and analysis
                        Map.entry("content", Property.of(p -> p.text(TextProperty.of(t -> t
                                .analyzer("standard"))))),
                        Map.entry("language", Property.of(p -> p.keyword(KeywordProperty.of(k -> k)))),

                        // Document metadata
                        Map.entry("author", Property.of(p -> p.text(TextProperty.of(t -> t
                                .fields("keyword", Property.of(f -> f.keyword(KeywordProperty.of(k -> k)))))))),
                        Map.entry("title", Property.of(p -> p.text(TextProperty.of(t -> t
                                .fields("keyword", Property.of(f -> f.keyword(KeywordProperty.of(k -> k)))))))),

                        // Indexing metadata
                        Map.entry("last_indexed_at", Property.of(p -> p.date(DateProperty.of(d -> d)))),
                        Map.entry("tags", Property.of(p -> p.keyword(KeywordProperty.of(k -> k)))))));

        // Create index with settings
        CreateIndexRequest request = CreateIndexRequest.of(i -> i
                .index(indexName)
                .settings(s -> s
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                        .refreshInterval(t -> t.time(config.getElasticsearch().getRefreshInterval())))
                .mappings(mapping));

        client.indices().create(request);
        logger.info("Index '{}' created successfully", indexName);
    }

    /**
     * Delete index.
     */
    public void deleteIndex(String indexName) throws IOException {
        boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
        if (!exists) {
            logger.warn("Index '{}' does not exist, cannot delete", indexName);
            return;
        }

        client.indices().delete(d -> d.index(indexName));
        logger.info("Index '{}' deleted successfully", indexName);
    }

    /**
     * Check if index exists.
     */
    public boolean indexExists(String indexName) throws IOException {
        return client.indices().exists(ExistsRequest.of(e -> e.index(indexName))).value();
    }
}
