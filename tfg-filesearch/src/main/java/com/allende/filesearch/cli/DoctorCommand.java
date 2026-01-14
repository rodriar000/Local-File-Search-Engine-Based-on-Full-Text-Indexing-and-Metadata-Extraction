package com.allende.filesearch.cli;

import com.allende.filesearch.elastic.ElasticsearchService;
import com.allende.filesearch.model.Config;
import com.allende.filesearch.utils.ConfigLoader;
import picocli.CommandLine.Command;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import co.elastic.clients.elasticsearch.indices.IndicesStatsResponse;
import java.io.File;
import java.util.concurrent.Callable;

@Command(name = "doctor", description = "Check system health and configuration", mixinStandardHelpOptions = true)
public class DoctorCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        System.out.println("Running System Doctor...");
        System.out.println("------------------------------------------------------------");

        Config config = ConfigLoader.load();
        boolean allOk = true;

        // 1. Elasticsearch Connection
        try (ElasticsearchService esService = new ElasticsearchService(config)) {
            // Cluster Health
            HealthResponse health = esService.getClient().cluster().health();
            String status = health.status().toString(); // Green, Yellow, Red
            boolean isGreen = "green".equalsIgnoreCase(status) || "yellow".equalsIgnoreCase(status);

            System.out.printf("[%-4s] Elasticsearch Cluster Status: %s%n", isGreen ? "OK" : "WARN", status);
            if (!isGreen) {
                System.out.println("       (Cluster is not Green)");
                allOk = false;
            }

            // Version check
            try {
                InfoResponse info = esService.getClient().info();
                System.out.println("[INFO] Elasticsearch Version: " + info.version().number());
                System.out.println("[INFO] Cluster Name: " + info.clusterName());
            } catch (Exception e) {
                System.out.println("[WARN] Could not retrieve ES version: " + e.getMessage());
            }

            // 2. Index Verification
            String indexName = config.getElasticsearch().getIndexName();
            boolean indexExists = esService.getIndexManager().indexExists(indexName);

            if (indexExists) {
                System.out.printf("[OK  ] Index '%s' exists.%n", indexName);
                // Get doc count
                try {
                    IndicesStatsResponse stats = esService.getClient().indices().stats(s -> s.index(indexName));

                    // Note: 'primaries' gives stats for primary shards only
                    long docCount = stats.indices().get(indexName).primaries().docs().count();
                    long storeSize = stats.indices().get(indexName).primaries().store().sizeInBytes();

                    System.out.printf("[INFO] Document Count: %d%n", docCount);
                    System.out.printf("[INFO] Store Size: %s%n", formatSize(storeSize));
                } catch (Exception e) {
                    System.out.println("[WARN] Could not retrieve document count: " + e.getMessage());
                }
            } else {
                System.out.printf("[FAIL] Index '%s' does NOT exist.%n", indexName);
                System.out.println("       Run 'java -jar filesearch.jar index <path>' to create it.");
                allOk = false;
            }

        } catch (Exception e) {
            System.out.println("[FAIL] Elasticsearch Connection: " + e.getMessage());
            allOk = false;
        }

        // 3. Output Permissions (Check ./out)
        File outDir = new File("./out");
        if (!outDir.exists())
            outDir.mkdirs();
        if (outDir.canWrite()) {
            System.out.println("[OK  ] Output directory writeable: " + outDir.getAbsolutePath());
        } else {
            System.out.println("[FAIL] Output directory NOT writeable: " + outDir.getAbsolutePath());
            allOk = false;
        }

        System.out.println("------------------------------------------------------------");
        if (allOk) {
            System.out.println("System is healthy.");
            return 0;
        } else {
            System.out.println("System has issues. Please resolve above failures.");
            return 1;
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
