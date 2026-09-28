package com.allende.filesearch.cli;

import com.allende.filesearch.model.SearchAnalytics;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * Command to view search analytics.
 */
@Command(name = "stats", description = "View search history statistics", mixinStandardHelpOptions = true)
public class StatsCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        SearchAnalytics stats = DependencyContainer.getInstance().getAnalyticsManager().getStats();

        System.out.println("\n=== Search History Analytics ===");
        System.out.println("Total Searches:     " + stats.getTotalSearches());

        double avgTime = stats.getTotalSearches() > 0
                ? (double) stats.getTotalSearchTimeMs() / stats.getTotalSearches()
                : 0;
        System.out.println(String.format("Avg Search Time:    %.2f ms", avgTime));

        System.out.println("\n--- Result Distribution ---");
        stats.getResultDistribution()
                .forEach((bucket, count) -> System.out.println(String.format("  %-10s : %d", bucket, count)));

        System.out.println("==============================\n");
        return 0;
    }
}
