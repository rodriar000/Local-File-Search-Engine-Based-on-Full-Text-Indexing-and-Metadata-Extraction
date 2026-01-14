package com.allende.filesearch.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.allende.filesearch.model.Config;
import com.allende.filesearch.model.Document;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main Elasticsearch client for indexing and searching.
 */
public class ElasticsearchService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchService.class);
    private final ElasticsearchClient client;
    private final Config config;
    private final IndexManager indexManager;
    private final SearchExecutor searchExecutor;

    public ElasticsearchService(Config config) throws IOException {
        this(config, createClient(config));
    }

    public ElasticsearchService(Config config, ElasticsearchClient client) {
        this.config = config;
        this.client = client;
        this.indexManager = new IndexManager(client, config);
        this.searchExecutor = new SearchExecutor(client, config);

        logger.info("Elasticsearch service initialized");
    }

    private static ElasticsearchClient createClient(Config config) {
        // Create the low-level REST client
        RestClient restClient = RestClient.builder(
                new HttpHost(
                        config.getElasticsearch().getHost(),
                        config.getElasticsearch().getPort(),
                        config.getElasticsearch().getScheme()))
                .build();

        // Create the transport
        RestClientTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper());

        // Create the high-level client
        return new ElasticsearchClient(transport);
    }

    /**
     * Index a single document.
     */
    public void indexDocument(Document doc) throws IOException {
        String indexName = config.getElasticsearch().getIndexName();

        Map<String, Object> documentMap = convertDocumentToMap(doc);

        IndexRequest<Map<String, Object>> request = IndexRequest.of(i -> i
                .index(indexName)
                .id(doc.getPath()) // Use path as document ID
                .document(documentMap));

        client.index(request);
        logger.debug("Indexed document: {}", doc.getPath());
    }

    /**
     * Bulk index multiple documents.
     */
    public int bulkIndexDocuments(List<Document> documents) throws IOException {
        if (documents.isEmpty()) {
            return 0;
        }

        String indexName = config.getElasticsearch().getIndexName();
        BulkRequest.Builder builder = new BulkRequest.Builder();

        for (Document doc : documents) {
            Map<String, Object> documentMap = convertDocumentToMap(doc);
            builder.operations(op -> op
                    .index(idx -> idx
                            .index(indexName)
                            .id(doc.getPath())
                            .document(documentMap)));
        }

        BulkResponse response = client.bulk(builder.build());

        if (response.errors()) {
            int errorCount = 0;
            for (BulkResponseItem item : response.items()) {
                co.elastic.clients.elasticsearch._types.ErrorCause error = item.error();
                if (error != null) {
                    // Check if error and reason are present specifically
                    String reason = error.reason() != null ? error.reason() : "Unknown reason";
                    System.err.println("Error indexing document " + item.id() + ": " + reason);
                    errorCount++;
                }
            }
            logger.warn("Bulk indexing completed with {} errors out of {} documents",
                    errorCount, documents.size());
            return documents.size() - errorCount;
        }

        logger.info("Successfully bulk indexed {} documents", documents.size());
        return documents.size();
    }

    /**
     * Delete a document by path.
     */
    public void deleteDocument(String path) throws IOException {
        String indexName = config.getElasticsearch().getIndexName();
        client.delete(d -> d.index(indexName).id(path));
        logger.debug("Deleted document: {}", path);
    }

    /**
     * Convert Document object to Map for ES indexing.
     */
    private Map<String, Object> convertDocumentToMap(Document doc) {
        Map<String, Object> map = new HashMap<>();
        map.put("path", doc.getPath());
        map.put("filename", doc.getFilename());
        map.put("extension", doc.getExtension());
        map.put("size", doc.getSize());

        if (doc.getCreatedAt() != null) {
            map.put("created_at", doc.getCreatedAt().toString());
        }
        if (doc.getModifiedAt() != null) {
            map.put("modified_at", doc.getModifiedAt().toString());
        }
        if (doc.getChecksumSha256() != null) {
            map.put("checksum_sha256", doc.getChecksumSha256());
        }
        if (doc.getContent() != null) {
            map.put("content", doc.getContent());
        }
        if (doc.getLanguage() != null) {
            map.put("language", doc.getLanguage());
        }
        if (doc.getAuthor() != null) {
            map.put("author", doc.getAuthor());
        }
        if (doc.getTitle() != null) {
            map.put("title", doc.getTitle());
        }
        if (doc.getLastIndexedAt() != null) {
            map.put("last_indexed_at", doc.getLastIndexedAt().toString());
        }
        if (doc.getTags() != null) {
            map.put("tags", doc.getTags());
        }

        return map;
    }

    public IndexManager getIndexManager() {
        return indexManager;
    }

    public SearchExecutor getSearchExecutor() {
        return searchExecutor;
    }

    public ElasticsearchClient getClient() {
        return client;
    }

    /**
     * Close the client connection.
     */
    @Override
    public void close() throws IOException {
        if (client._transport() != null) {
            client._transport().close();
            logger.info("Elasticsearch client closed");
        }
    }
}
