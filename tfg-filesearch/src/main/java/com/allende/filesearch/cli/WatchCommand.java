package com.allende.filesearch.cli;

import com.allende.filesearch.index.DocumentIndex;
import com.allende.filesearch.index.IndexLockedException;
import com.allende.filesearch.index.IndexSynchronizer;
import com.allende.filesearch.watcher.FileSystemWatcher;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Indexes a folder, then keeps the index up to date as files change.
 */
@Command(name = "watch", description = "Index a folder and keep the index updated as files change", mixinStandardHelpOptions = true)
public class WatchCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Folder to watch")
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
            IndexingRunner.run(index, folder, System.out);

            IndexSynchronizer synchronizer = new IndexSynchronizer(
                    index, container.getDocumentExtractor(), container.getConfig());
            try (FileSystemWatcher watcher = new FileSystemWatcher(folder, index, synchronizer, container.getConfig())) {
                System.out.println("\nWatching " + folder.toAbsolutePath().normalize() + " (Ctrl+C to stop)");
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        watcher.close();
                    } catch (Exception ignored) {
                        // Shutting down anyway.
                    }
                }));
                watcher.start();
            }
            return 0;
        } catch (IndexLockedException e) {
            System.err.println(e.getMessage());
            return 2;
        } catch (Exception e) {
            System.err.println("Watch failed: " + e.getMessage());
            return 1;
        }
    }
}
