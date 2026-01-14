package com.allende.filesearch.cli;

import com.allende.filesearch.watcher.FileSystemWatcher;
import com.allende.filesearch.model.Config;
import com.allende.filesearch.utils.ConfigLoader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Command to watch a directory for file changes and update the index
 * automatically.
 */
@Command(name = "watch", description = "Watch directory for changes and update index automatically", mixinStandardHelpOptions = true)
public class WatchCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Directory path to watch")
    private String path;

    @Override
    public Integer call() throws Exception {
        System.out.println("Starting file system watcher...");
        System.out.println("Watching: " + path);
        System.out.println("Press Ctrl+C to stop\n");

        Config config = ConfigLoader.load();

        try (FileSystemWatcher watcher = new FileSystemWatcher(Paths.get(path), config)) {
            watcher.start();

            // Keep running until interrupted
            Thread.currentThread().join();

            return 0;
        } catch (InterruptedException e) {
            System.out.println("\nWatcher stopped.");
            return 0;
        } catch (Exception e) {
            System.err.println("✗ Watch failed: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}
