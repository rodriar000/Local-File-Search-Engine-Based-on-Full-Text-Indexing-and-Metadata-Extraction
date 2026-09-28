package com.allende.filesearch.cli;

import com.allende.filesearch.api.ApiServer;
import com.allende.filesearch.index.DocumentIndex;
import com.allende.filesearch.index.IndexLockedException;
import com.allende.filesearch.index.IndexSynchronizer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Runs the local API for the desktop app. The app starts this process, passes
 * a fresh token in {@value #TOKEN_ENV}, reads the port from the ready line and
 * keeps stdin open: when the app goes away, stdin closes and the server stops.
 */
@Command(name = "serve", description = "Run the local API used by the desktop app (127.0.0.1 only, token required). "
        + "Stops when stdin is closed.", mixinStandardHelpOptions = true)
public class ServeCommand implements Callable<Integer> {
    public static final String TOKEN_ENV = "FILESEARCH_API_TOKEN";
    public static final String READY_LINE = "FILESEARCH_READY port=";

    @Option(names = "--port", description = "Port on 127.0.0.1; 0 picks a free one", defaultValue = "0")
    private int port;

    @Override
    public Integer call() {
        String token = System.getenv(TOKEN_ENV);
        if (token == null || token.length() < ApiServer.MIN_TOKEN_LENGTH) {
            System.err.println("Set " + TOKEN_ENV + " to a random value of at least "
                    + ApiServer.MIN_TOKEN_LENGTH + " characters.");
            return 2;
        }

        DependencyContainer container = DependencyContainer.getInstance();
        DocumentIndex index;
        try {
            index = container.openIndexForWriting();
        } catch (IndexLockedException e) {
            System.err.println(e.getMessage());
            return 3;
        } catch (IOException e) {
            System.err.println("Could not open the index: " + e.getMessage());
            return 1;
        }

        CountDownLatch stop = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        // On Ctrl+C or SIGTERM, let the index close cleanly before the JVM exits.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stop.countDown();
            try {
                closed.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        try (index) {
            IndexSynchronizer synchronizer = new IndexSynchronizer(
                    index, container.getDocumentExtractor(), container.getConfig());
            try (ApiServer server = new ApiServer(index, synchronizer, container.getAnalyticsManager(), token)) {
                int actualPort = server.start(port);
                startStdinWatch(stop);
                System.out.println(READY_LINE + actualPort);
                System.out.flush();
                stop.await();
            }
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (IOException e) {
            System.err.println("Local API failed: " + e.getMessage());
            return 1;
        } finally {
            closed.countDown();
        }
    }

    private static void startStdinWatch(CountDownLatch stop) {
        Thread watch = new Thread(() -> {
            try (InputStream in = System.in) {
                while (in.read() != -1) {
                    // Input is ignored; only the end of the stream matters.
                }
            } catch (IOException e) {
                // Treat a broken stdin like a closed one.
            }
            stop.countDown();
        }, "stdin-watch");
        watch.setDaemon(true);
        watch.start();
    }
}
