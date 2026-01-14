package com.allende.filesearch.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Main CLI entry point for File Search Engine.
 */
@Command(name = "filesearch", mixinStandardHelpOptions = true, version = "1.0", description = "A powerful file search CLI using Elasticsearch and Apache Tika.", subcommands = {
        CreateIndexCommand.class,
        UpdateIndexCommand.class,
        SearchCommand.class,
        StatsCommand.class,
        ReindexCommand.class,
        WatchCommand.class,
        ExportMetricsCommand.class,
        DoctorCommand.class,
        BenchmarkCommand.class
})
public class FileSearchCLI implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new FileSearchCLI()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        // Show help when no subcommand is provided
        CommandLine.usage(this, System.out);
    }
}
