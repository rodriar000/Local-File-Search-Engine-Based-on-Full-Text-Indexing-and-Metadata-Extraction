package com.allende.filesearch.cli;

import com.allende.filesearch.index.DocumentIndex;
import com.allende.filesearch.index.IndexLockedException;
import com.allende.filesearch.index.IndexSynchronizer.SyncReport;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Indexes a folder incrementally: new and changed files are extracted,
 * unchanged files are skipped and deleted files are removed from the index.
 */
@Command(name = "update-index", description = "Index a folder (only new and changed files are processed)", mixinStandardHelpOptions = true)
public class UpdateIndexCommand implements Callable<Integer> {

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
        System.out.println("Indexing " + folder.toAbsolutePath().normalize());
        System.out.println("Index:   " + container.getIndexDirectory());

        try (DocumentIndex index = container.openIndexForWriting()) {
            SyncReport report = IndexingRunner.run(index, folder, System.out);
            return report.cancelled() ? 1 : 0;
        } catch (IndexLockedException e) {
            System.err.println(e.getMessage());
            return 2;
        } catch (Exception e) {
            System.err.println("Indexing failed: " + e.getMessage());
            return 1;
        }
    }

    public void setPath(String path) {
        this.path = path;
    }
}
