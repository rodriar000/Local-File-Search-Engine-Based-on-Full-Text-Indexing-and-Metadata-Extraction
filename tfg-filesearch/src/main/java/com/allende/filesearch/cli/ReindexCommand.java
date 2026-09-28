package com.allende.filesearch.cli;

import com.allende.filesearch.index.DocumentIndex;
import com.allende.filesearch.index.IndexLockedException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Rebuilds the index from scratch, e.g. after changing extraction settings.
 */
@Command(name = "reindex", description = "Clear the index and index a folder from scratch", mixinStandardHelpOptions = true)
public class ReindexCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Folder to index")
    private String path;

    @Override
    public Integer call() {
        Path folder = Paths.get(path);
        if (!Files.isDirectory(folder)) {
            System.err.println("Folder not found: " + path);
            return 1;
        }

        DependencyContainer container = DependencyContainer.getInstance();
        try (DocumentIndex index = container.openIndexForWriting()) {
            System.out.println("Clearing the index at " + container.getIndexDirectory());
            index.deleteAll();
            index.commit();
            return IndexingRunner.run(index, folder, System.out).cancelled() ? 1 : 0;
        } catch (IndexLockedException e) {
            System.err.println(e.getMessage());
            return 2;
        } catch (Exception e) {
            System.err.println("Reindex failed: " + e.getMessage());
            return 1;
        }
    }
}
