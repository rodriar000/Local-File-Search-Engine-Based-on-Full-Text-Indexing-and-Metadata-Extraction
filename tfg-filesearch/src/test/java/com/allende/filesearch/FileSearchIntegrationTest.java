package com.allende.filesearch;

import com.allende.filesearch.cli.FileSearchCLI;
import com.allende.filesearch.model.Config;
import com.allende.filesearch.utils.ConfigLoader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that runs the full CLI against a real (or mocked)
 * environment.
 * Requires a running Elasticsearch instance if not using Testcontainers.
 * 
 * To run this test:
 * 1. Start Elasticsearch on localhost:9200
 * 2. Run with -DrunIT=true
 */
@Tag("integration")
class FileSearchIntegrationTest {

    @Test
    void testFullIndexingAndSearchFlow() throws IOException {
        // Skip if not explicitly enabled to avoid breaking CI builds without ES
        if (!"true".equals(System.getProperty("runIT"))) {
            System.out.println("Skipping integration test. Run with -DrunIT=true to enable.");
            return;
        }

        // 1. Setup Config
        Config config = ConfigLoader.load();
        config.getElasticsearch().setIndexName("integration_test_index");

        // 2. Create Index
        int exitCodeCreate = new CommandLine(new FileSearchCLI())
                .execute("create-index", "--name", "integration_test_index");
        assertThat(exitCodeCreate).isEqualTo(0);

        // 3. Create dummy file
        Path tempDir = Files.createTempDirectory("filesearch_it");
        Path testFile = tempDir.resolve("test_doc.txt");
        Files.writeString(testFile, "This is a unique integration test content string.");

        // 4. Index Document
        int exitCodeIndex = new CommandLine(new FileSearchCLI())
                .execute("update-index", tempDir.toString());
        assertThat(exitCodeIndex).isEqualTo(0);

        // Wait for refresh
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
        }

        // 5. Search
        // We capture stdout to verify results (in a real test we'd use a custom
        // PrintStream)
        // For now, we just check exit code
        int exitCodeSearch = new CommandLine(new FileSearchCLI())
                .execute("search", "unique integration", "--index-name", "integration_test_index");
        assertThat(exitCodeSearch).isEqualTo(0);

        // Cleanup
        Files.deleteIfExists(testFile);
        Files.deleteIfExists(tempDir);
    }
}
