package com.allende.filesearch.cli;

import com.allende.filesearch.index.DocumentIndex;
import com.allende.filesearch.index.SearchRequest;
import com.allende.filesearch.model.SearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Command to search indexed documents.
 */
@Command(name = "search", description = "Search the index. Syntax: words (all required), \"exact phrase\", "
        + "\"phrase\"~5 (words near each other), -exclude, a | b (either), prefix*", mixinStandardHelpOptions = true)
public class SearchCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Search query")
    private String query;

    @Option(names = { "-n", "--size" }, description = "Number of results to return", defaultValue = "10")
    private int size;

    @Option(names = { "-o", "--output" }, description = "Output format: text or json", defaultValue = "text")
    private String outputFormat;

    @Option(names = { "--ext" }, split = ",", description = "Only these file types, e.g. --ext pdf,docx")
    private List<String> extensions;

    @Override
    public Integer call() {
        DependencyContainer container = DependencyContainer.getInstance();
        try (DocumentIndex index = container.openIndexReadOnly()) {
            SearchResult result = index.search(new SearchRequest(query, extensions, null, null, null, null, 0, size));

            if ("json".equalsIgnoreCase(outputFormat)) {
                printJsonOutput(result);
            } else {
                printTextOutput(result);
            }

            try {
                container.getAnalyticsManager().recordSearch(query, result.getTotalTimeMs(), result.getTotalHits());
            } catch (RuntimeException e) {
                // Analytics are informative only.
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Search failed: " + e.getMessage());
            return 1;
        }
    }

    private void printTextOutput(SearchResult result) {
        System.out.println("SEARCH RESULTS");
        System.out.println("------------------------------------------------------------");
        System.out.println("Search time:          " + result.getTotalTimeMs() + " ms");
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
                System.out.println("    " + preview.replace("\n", " ")
                        .replace(DocumentIndex.HIGHLIGHT_PRE, "[").replace(DocumentIndex.HIGHLIGHT_POST, "]"));
            }

            System.out.println("------------------------------------------------------------");
            System.out.println();
            rank++;
        }

        System.out.println("Found " + result.getTotalHits() + " matches.");
        System.out.println("------------------------------------------------------------\n");
    }

    private void printJsonOutput(SearchResult result) throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
