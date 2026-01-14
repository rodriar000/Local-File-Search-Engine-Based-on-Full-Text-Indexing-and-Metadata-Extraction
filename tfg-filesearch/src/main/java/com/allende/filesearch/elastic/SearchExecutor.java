package com.allende.filesearch.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch.indices.IndexState;
import com.allende.filesearch.model.Config;
import com.allende.filesearch.model.Document;
import com.allende.filesearch.model.IndexStats;
import com.allende.filesearch.model.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes search queries against Elasticsearch.
 * Refactored for advanced search capabilities: Highlighting, Fuzziness,
 * Multi-match.
 */
public class SearchExecutor {
    private static final Logger logger = LoggerFactory.getLogger(SearchExecutor.class);
    private final ElasticsearchClient client;
    private final Config config;

    public SearchExecutor(ElasticsearchClient client, Config config) {
        this.client = client;
        this.config = config;
    }

    /**
     * Execute a search query.
     */
    @SuppressWarnings("rawtypes")
    public SearchResult search(String queryString, int size) throws IOException {
        long startTime = System.currentTimeMillis();
        String indexName = config.getElasticsearch().getIndexName();

        // Build advanced multi_match query
        Query query = Query.of(q -> q
                .multiMatch(mm -> mm
                        .query(queryString)
                        .fields("content^3", "title^2", "filename^2", "author")
                        .fuzziness("AUTO")
                        .operator(co.elastic.clients.elasticsearch._types.query_dsl.Operator.And)
                        .type(co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType.BestFields)));

        // Configure highlighting
        Highlight highlight = Highlight.of(h -> h
                .fields("content", HighlightField.of(hf -> hf
                        .preTags("<em>")
                        .postTags("</em>")
                        .numberOfFragments(3)
                        .fragmentSize(150))));

        SearchRequest request = SearchRequest.of(s -> s
                .index(indexName)
                .query(query)
                .highlight(highlight)
                .size(size));

        SearchResponse<Map> response = client.search(request, Map.class);

        // Convert hits to SearchResult
        List<SearchResult.DocumentHit> hits = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) hit.source();
            Document doc = convertMapToDocument(source);
            Double score = hit.score();
            SearchResult.DocumentHit documentHit = new SearchResult.DocumentHit(doc, score != null ? score : 0.0);

            // Extract highlights
            if (hit.highlight() != null && hit.highlight().containsKey("content")) {
                documentHit.setHighlights(hit.highlight().get("content"));

                // Also update document content preview with the first highlight if available
                if (!documentHit.getHighlights().isEmpty()) {
                    doc.setContent(documentHit.getHighlights().get(0));
                }
            }

            hits.add(documentHit);
        }

        long totalHits = 0;
        co.elastic.clients.elasticsearch.core.search.TotalHits total = response.hits().total();
        if (total != null) {
            totalHits = total.value();
        }

        SearchResult result = new SearchResult(
                totalHits,
                response.took(),
                hits);

        Double maxScore = response.hits().maxScore();
        result.setMaxScore(maxScore != null ? maxScore : 0.0);

        // Calculate and set metrics
        // Capture end time for full request latency measurement
        long endTime = System.currentTimeMillis();

        /*
         * Metric Definition: Total Execution Time vs. Query Time
         * -----------------------------------------------------
         * 'totalTimeMs' measures the end-to-end latency perceived by the application
         * layer,
         * inclusive of network overhead, object deserialization, and business logic
         * processing.
         * 
         * 'took' (from Elasticsearch) measures strictly the time spent by the search
         * engine
         * executing the query on the shard.
         * 
         * Analyzing the delta between these two metrics reveals the
         * "Application Overhead".
         */
        long totalTimeMs = endTime - startTime;
        result.setTotalTimeMs(totalTimeMs);

        // Calculate Throughput (Results per Second)
        // This metric indicates the efficiency of result retrieval and potential
        // bandwidth constraints.
        double rps = totalTimeMs > 0 ? (double) result.getHits().size() / (totalTimeMs / 1000.0) : 0;
        result.setResultsPerSecond(rps);

        logger.info("[Metrics] Search completed. Query Latency: {}ms | Total Latency: {}ms | Overhead: {}ms",
                response.took(), totalTimeMs, (totalTimeMs - response.took()));

        return result;
    }

    /**
     * Get index statistics.
     */
    public IndexStats getIndexStats() throws IOException {
        String indexName = config.getElasticsearch().getIndexName();

        IndexStats stats = new IndexStats();
        stats.setIndexName(indexName);

        // Get document count
        CountRequest countRequest = CountRequest.of(c -> c.index(indexName));
        long count = client.count(countRequest).count();
        stats.setDocumentCount(count);

        // Get index metadata
        try {
            GetIndexResponse indexResponse = client.indices().get(g -> g.index(indexName));
            IndexState indexState = indexResponse.get(indexName);

            if (indexState != null) {
                co.elastic.clients.elasticsearch.indices.IndexSettings settings = indexState.settings();
                if (settings != null) {
                    co.elastic.clients.elasticsearch.indices.IndexSettings index = settings.index();
                    if (index != null) {
                        // Safe access to nested string properties which might be null
                        String shards = index.numberOfShards();
                        String replicas = index.numberOfReplicas();

                        if (shards != null)
                            stats.setNumberOfShards(Integer.parseInt(shards));
                        if (replicas != null)
                            stats.setNumberOfReplicas(Integer.parseInt(replicas));
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Could not retrieve index metadata: {}", e.getMessage());
        }

        // Note: Index size would require cluster stats API
        // For now, we'll estimate based on document count
        stats.setIndexSizeBytes(count * 10240); // Rough estimate: 10KB per document
        stats.setHealth("green"); // Simplified

        return stats;
    }

    /**
     * Convert ES Map result to Document object.
     */
    private Document convertMapToDocument(Map<String, Object> map) {
        Document doc = new Document();

        if (map.get("path") != null)
            doc.setPath((String) map.get("path"));
        if (map.get("filename") != null)
            doc.setFilename((String) map.get("filename"));
        if (map.get("extension") != null)
            doc.setExtension((String) map.get("extension"));
        if (map.get("size") != null)
            doc.setSize(((Number) map.get("size")).longValue());

        if (map.get("created_at") != null) {
            doc.setCreatedAt(parseInstant((String) map.get("created_at")));
        }
        if (map.get("modified_at") != null) {
            doc.setModifiedAt(parseInstant((String) map.get("modified_at")));
        }
        if (map.get("checksum_sha256") != null) {
            doc.setChecksumSha256((String) map.get("checksum_sha256"));
        }
        if (map.get("content") != null) {
            String content = (String) map.get("content");
            // Truncate content for display (default fallback if no highlight)
            if (content.length() > 300) {
                content = content.substring(0, 300) + "...";
            }
            doc.setContent(content);
        }
        if (map.get("language") != null)
            doc.setLanguage((String) map.get("language"));
        if (map.get("author") != null)
            doc.setAuthor((String) map.get("author"));
        if (map.get("title") != null)
            doc.setTitle((String) map.get("title"));

        if (map.get("last_indexed_at") != null) {
            doc.setLastIndexedAt(parseInstant((String) map.get("last_indexed_at")));
        }

        return doc;
    }

    /**
     * Safely parse Instant from ES date string.
     * Handles both ISO-8601 with timezone and without.
     */
    private Instant parseInstant(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        try {
            // Try parsing as ISO-8601 with timezone
            return Instant.parse(dateString);
        } catch (Exception e) {
            try {
                // ES dates without timezone - append 'Z' for UTC
                if (!dateString.contains("Z") && !dateString.contains("+") && dateString.lastIndexOf("-") < 10) {
                    return Instant.parse(dateString + "Z");
                }
            } catch (Exception ex) {
                logger.warn("Could not parse date: {}", dateString);
            }
        }
        return null;
    }
}
