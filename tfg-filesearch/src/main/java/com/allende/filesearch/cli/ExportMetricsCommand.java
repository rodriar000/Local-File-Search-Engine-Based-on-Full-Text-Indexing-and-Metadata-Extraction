package com.allende.filesearch.cli;

import com.allende.filesearch.analytics.AnalyticsManager;
import com.allende.filesearch.model.ThesisMetricsExport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import picocli.CommandLine;

import java.io.File;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "export-metrics", description = "Exports aggregated metrics for thesis analysis (JSON)")
public class ExportMetricsCommand implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", description = "Output file path (e.g., metrics.json)", defaultValue = "thesis_metrics.json")
    private File outputFile;

    private final AnalyticsManager analyticsManager;
    private final ObjectMapper mapper;

    public ExportMetricsCommand() {
        // DependencyContainer handling to be robust against resolution issues
        AnalyticsManager am = null;
        try {
            am = DependencyContainer.getInstance().getAnalyticsManager();
        } catch (Exception e) {
            // Fallback or error if DI fails
            am = new AnalyticsManager();
        }
        this.analyticsManager = am;
        this.mapper = new ObjectMapper().enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public Integer call() throws Exception {
        System.out.println("Generating thesis metrics export...");

        ThesisMetricsExport export = analyticsManager.generateExport();

        mapper.writeValue(outputFile, export);

        System.out.println("✅ Export completed successfully.");
        System.out.println("File saved to: " + outputFile.getAbsolutePath());
        System.out.println("\nSummary:");
        System.out.println("- Total Index Runs: " + export.getSystemSummary().totalIndexRuns);
        System.out.println("- Total Searches: " + export.getSystemSummary().totalSearches);
        if (export.getIndexingStats() != null) {
            System.out.printf("- Avg Indexing Duration: %.2f ms%n", export.getIndexingStats().averageDurationMs);
            System.out.printf("- Avg Indexing Throughput: %.2f docs/s%n",
                    export.getIndexingStats().averageDocsPerSecond);
        }

        return 0;
    }
}
