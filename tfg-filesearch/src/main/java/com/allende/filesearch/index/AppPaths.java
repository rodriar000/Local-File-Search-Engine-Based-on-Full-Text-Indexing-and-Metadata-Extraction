package com.allende.filesearch.index;

import com.allende.filesearch.model.Config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Locations of the application's local data. Everything lives on this computer.
 */
public final class AppPaths {
    /** Overrides the data folder, e.g. for tests or a portable installation. */
    public static final String HOME_ENV = "FILESEARCH_HOME";
    /** Same as {@link #HOME_ENV}, as a JVM system property. Takes precedence. */
    public static final String HOME_PROPERTY = "filesearch.home";

    private AppPaths() {
    }

    public static Path dataHome() {
        String override = System.getProperty(HOME_PROPERTY, System.getenv(HOME_ENV));
        if (override != null && !override.isBlank()) {
            return Paths.get(override).toAbsolutePath();
        }
        return Paths.get(System.getProperty("user.home"), ".filesearch");
    }

    public static Path indexDirectory(Config config) {
        String configured = config.getIndex() != null ? config.getIndex().getDirectory() : null;
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured).toAbsolutePath();
        }
        return dataHome().resolve("index");
    }
}
