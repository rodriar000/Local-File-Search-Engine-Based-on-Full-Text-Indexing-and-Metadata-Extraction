package com.allende.filesearch;

import com.allende.filesearch.cli.FileSearchCLI;
import com.allende.filesearch.index.AppPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real CLI end to end: index a folder, search it, change it, search again.
 * Everything happens in a temporary data folder; no external service is needed.
 */
class FileSearchIntegrationTest {

    @TempDir
    Path dataHome;

    @TempDir
    Path documents;

    private String previousHome;

    @BeforeEach
    void useTemporaryDataHome() {
        previousHome = System.getProperty(AppPaths.HOME_PROPERTY);
        System.setProperty(AppPaths.HOME_PROPERTY, dataHome.toString());
    }

    @AfterEach
    void restoreDataHome() {
        if (previousHome == null) {
            System.clearProperty(AppPaths.HOME_PROPERTY);
        } else {
            System.setProperty(AppPaths.HOME_PROPERTY, previousHome);
        }
    }

    @Test
    void indexesSearchesAndFollowsChanges() throws Exception {
        Path contract = documents.resolve("contrato.txt");
        Files.writeString(contract, "Contrato de arrendamiento firmado en Sevilla por ambas partes.");
        Files.writeString(documents.resolve("otro.txt"), "Escrito sin relación.");

        assertThat(run("update-index", documents.toString())).isZero();
        assertThat(Files.isDirectory(dataHome.resolve("index"))).isTrue();

        String output = capture("search", "arrendamientos", "-o", "json");
        assertThat(output).contains("contrato.txt").doesNotContain("otro.txt");

        Files.delete(contract);
        assertThat(run("update-index", documents.toString())).isZero();
        assertThat(capture("search", "arrendamiento", "-o", "json")).doesNotContain("contrato.txt");

        // Search terms must never be written to disk.
        Path stats = dataHome.resolve("search_stats.json");
        assertThat(stats).exists();
        assertThat(Files.readString(stats)).doesNotContain("arrendamiento");
    }

    private static int run(String... args) {
        return new CommandLine(new FileSearchCLI()).execute(args);
    }

    private static String capture(String... args) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            assertThat(run(args)).isZero();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
