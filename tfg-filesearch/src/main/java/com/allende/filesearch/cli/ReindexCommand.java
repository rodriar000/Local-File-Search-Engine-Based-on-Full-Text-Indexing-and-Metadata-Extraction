package com.allende.filesearch.cli;

import com.allende.filesearch.elastic.ElasticsearchService;
import com.allende.filesearch.model.Config;
import com.allende.filesearch.utils.ConfigLoader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

/**
 * Command to reindex all documents (recreate index from scratch).
 */
@Command(name = "reindex", description = "Reindex all documents (delete and recreate index)", mixinStandardHelpOptions = true)
public class ReindexCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Directory path to reindex")
    private String path;

    @Override
    public Integer call() throws Exception {
        System.out.println("Starting reindex operation...");
        System.out.println("WARNING: This will delete the existing index and recreate it.");

        Config config = ConfigLoader.load();
        String indexName = config.getElasticsearch().getIndexName();

        try (ElasticsearchService esService = new ElasticsearchService(config)) {
            // Delete existing index if it exists
            if (esService.getIndexManager().indexExists(indexName)) {
                System.out.println("Deleting existing index '" + indexName + "'...");
                esService.getIndexManager().deleteIndex(indexName);
            }

            // Create new index
            System.out.println("Creating new index '" + indexName + "'...");
            esService.getIndexManager().createIndex(indexName);

            // Now run update-index using setter methods
            System.out.println("\nStarting indexing...");
            UpdateIndexCommand updateCmd = new UpdateIndexCommand();
            updateCmd.setPath(this.path);
            updateCmd.setRecursive(true);
            updateCmd.setCreateIfMissing(false); // We just created it

            return updateCmd.call();

        } catch (Exception e) {
            System.err.println("✗ Reindex failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}
