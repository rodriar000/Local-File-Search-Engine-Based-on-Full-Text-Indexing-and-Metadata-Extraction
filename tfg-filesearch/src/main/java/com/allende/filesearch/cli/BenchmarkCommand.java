package com.allende.filesearch.cli;

import com.allende.filesearch.index.DocumentIndex;
import com.allende.filesearch.index.SearchRequest;
import com.allende.filesearch.model.SearchResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "benchmark", description = "Run performance benchmarks", mixinStandardHelpOptions = true)
public class BenchmarkCommand implements Callable<Integer> {

    @Option(names = { "--queries" }, description = "Path to file containing queries (one per line)", required = true)
    private String queriesFile;

    @Option(names = { "--runs" }, description = "Number of runs per query", defaultValue = "5")
    private int runs;

    @Override
    public Integer call() throws Exception {
        File qFile = new File(queriesFile);
        if (!qFile.exists()) {
            System.err.println("Queries file not found: " + queriesFile);
            return 1;
        }

        System.out.println("Starting Benchmark...");
        System.out.println("Queries File: " + queriesFile);
        System.out.println("Runs per query: " + runs);

        List<String> queries = Files.readAllLines(Paths.get(queriesFile));
        if (queries.isEmpty()) {
            System.err.println("Queries file is empty.");
            return 1;
        }

        File outDir = new File("./out");
        if (!outDir.exists())
            outDir.mkdirs();
        String reportPath = "./out/benchmark_report_" + System.currentTimeMillis() + ".csv";

        try (DocumentIndex index = DependencyContainer.getInstance().openIndexReadOnly();
                FileWriter writer = new FileWriter(reportPath);
                CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                        .setHeader("Query", "Run", "TotalTimeMs", "SearchTookMs", "Hits").build())) {

            // Warmup (optional)
            System.out.println("Warming up...");
            index.search(SearchRequest.of("", 1));

            int totalQueries = 0;
            for (String query : queries) {
                if (query.trim().isEmpty())
                    continue;
                System.out.println("Benchmarking query: " + query);
                totalQueries++;

                for (int i = 0; i < runs; i++) {
                    long startTime = System.currentTimeMillis();
                    SearchResult result = index.search(SearchRequest.of(query, 50));
                    long endTime = System.currentTimeMillis();
                    long totalTime = endTime - startTime;

                    csvPrinter.printRecord(query, i + 1, totalTime, result.getTookMs(), result.getTotalHits());
                }
            }
            System.out.println("Executed " + (totalQueries * runs) + " search requests.");
        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }

        System.out.println("Benchmark complete. Report saved to: " + reportPath);
        return 0;
    }
}
