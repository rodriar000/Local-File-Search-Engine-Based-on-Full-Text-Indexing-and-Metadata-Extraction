package com.allende.filesearch.cli;

import com.allende.filesearch.elastic.ElasticsearchService;
import com.allende.filesearch.model.Config;
import com.allende.filesearch.model.SearchResult;
import com.allende.filesearch.utils.ConfigLoader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

/**
 * Command to search indexed documents.
 */
@Command(name = "search", description = "Search for documents in the index", mixinStandardHelpOptions = true)
public class SearchCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Search query (use query_string syntax)")
    private String query;

    @Option(names = { "-n", "--size" }, description = "Number of results to return", defaultValue = "10")
    private int size;

    @Option(names = { "-o", "--output" }, description = "Output format: text or json", defaultValue = "text")
    private String outputFormat;

    @Option(names = { "--index-name" }, description = "Override index name")
    private String indexName;

    @Option(names = { "--ext" }, description = "Filter by file extension (e.g., pdf, txt)")
    private String extension;

    @Override
    public Integer call() throws Exception {
        Config config = ConfigLoader.load();

        // Override index name if provided
        if (indexName != null && !indexName.isEmpty()) {
            config.getElasticsearch().setIndexName(indexName);
        }

        // Append extension filter to query if provided
        String finalQuery = query;
        if (extension != null && !extension.isEmpty()) {
            finalQuery = String.format("(%s) AND extension:%s", query, extension);
        }

        try (ElasticsearchService esService = new ElasticsearchService(config)) {
            long startTime = System.currentTimeMillis();
            SearchResult result = esService.getSearchExecutor().search(finalQuery, size);
            long endTime = System.currentTimeMillis();

            if ("json".equalsIgnoreCase(outputFormat)) {
                printJsonOutput(result);
            } else {
                printTextOutput(result, endTime - startTime);
            }

            // Record Analytics
            try {
                DependencyContainer.getInstance().getAnalyticsManager()
                        .recordSearch(finalQuery, result.getTotalTimeMs(), result.getTotalHits());
            } catch (Exception e) {
                // Fail silently for analytics not to impact user experience
                // System.err.println("Analytics error: " + e.getMessage());
            }

            return 0;
        } catch (Exception e) {
            System.err.println("Search failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    private void printTextOutput(SearchResult result, long queryTimeMs) {
        System.out.println("SEARCH RESULTS");
        System.out.println("------------------------------------------------------------");
        System.out.println("Total Execution Time: " + result.getTotalTimeMs() + " ms");
        System.out.println("ES Query Time:        " + result.getTookMs() + " ms");
        System.out.println(
                "Throughput:           " + String.format("%.2f", result.getResultsPerSecond()) + " results/sec");
        System.out.println("Total hits:           " + result.getTotalHits());
        System.out.println("Showing top " + result.getHits().size() + " results");
        System.out.println("------------------------------------------------------------\n");

        if (result.getHits().isEmpty()) {
            System.out.println("No documents found matching your query.");
            return;
        }

        int rank = 1;
        for (SearchResult.DocumentHit hit : result.getHits()) {
            // Result header with rank and score
            System.out.println("[" + rank + "] Score: " + String.format("%.4f", hit.getScore()));

            // File path
            System.out.println("Path:   " + hit.getDocument().getPath());

            if (hit.getDocument().getTitle() != null) {
                System.out.println("Title:  " + hit.getDocument().getTitle());
            }
            if (hit.getDocument().getAuthor() != null) {
                System.out.println("Author: " + hit.getDocument().getAuthor());
            }

            System.out.println("Size:   " + formatSize(hit.getDocument().getSize()));

            if (hit.getDocument().getContent() != null && !hit.getDocument().getContent().isEmpty()) {
                String preview = hit.getDocument().getContent();
                if (preview.length() > 200) {
                    preview = preview.substring(0, 200) + "...";
                }
                System.out.println("Preview:");
                System.out.println("    " + preview.replace("\n", " "));
            }

            System.out.println("------------------------------------------------------------");
            System.out.println();
            rank++;
        }

        System.out.println("Found " + result.getTotalHits() + " matches.");
        System.out.println("------------------------------------------------------------\n");
    }

    private void printJsonOutput(SearchResult result) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        String json = mapper.writeValueAsString(result);
        System.out.println(json);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
