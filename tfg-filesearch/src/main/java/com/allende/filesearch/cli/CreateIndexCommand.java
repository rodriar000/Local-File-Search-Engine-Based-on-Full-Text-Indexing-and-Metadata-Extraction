package com.allende.filesearch.cli;

import com.allende.filesearch.elastic.ElasticsearchService;
import com.allende.filesearch.model.Config;
import com.allende.filesearch.utils.ConfigLoader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Command to create a new Elasticsearch index.
 */
@Command(name = "create-index", description = "Create a new Elasticsearch index with mappings", mixinStandardHelpOptions = true)
public class CreateIndexCommand implements Callable<Integer> {

    @Option(names = { "-n", "--name" }, description = "Index name (default: from config)")
    private String indexName;

    @Override
    public Integer call() throws Exception {
        System.out.println("Creating Elasticsearch index...");

        Config config = ConfigLoader.load();

        if (indexName != null) {
            config.getElasticsearch().setIndexName(indexName);
        }

        String finalIndexName = config.getElasticsearch().getIndexName();
        System.out.println("Index name: " + finalIndexName);

        try (ElasticsearchService esService = new ElasticsearchService(config)) {
            // Check if index already exists (idempotent operation)
            if (esService.getIndexManager().indexExists(finalIndexName)) {
                System.out.println("Index '" + finalIndexName + "' already exists. Skipping creation.");
                return 0;
            }

            esService.getIndexManager().createIndex(finalIndexName);
            System.out.println("Index '" + finalIndexName + "' created.");
            return 0;
        } catch (Exception e) {
            System.err.println("Failed to create index: " + e.getMessage());
            System.err.println("Ensure Elasticsearch is running at: " + config.getElasticsearch().getHost() + ":"
                    + config.getElasticsearch().getPort());
            e.printStackTrace();
            return 1;
        }
    }
}
