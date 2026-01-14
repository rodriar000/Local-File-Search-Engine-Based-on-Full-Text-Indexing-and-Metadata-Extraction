package com.allende.filesearch.utils;

import com.allende.filesearch.model.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * Utility class to load configuration from YAML file.
 */
public class ConfigLoader {
    private static final String DEFAULT_CONFIG_FILE = "application.yml";
    private static final String LEGACY_CONFIG_FILE = "config.yaml";
    private static Config instance;

    public static synchronized Config load() {
        if (instance == null) {
            instance = loadConfig(DEFAULT_CONFIG_FILE);
            if (instance == null) {
                instance = loadConfig(LEGACY_CONFIG_FILE);
            }
            // If still null after trying both, return a default empty config
            if (instance == null) {
                instance = new Config();
            }
        }
        return instance;
    }

    public static synchronized Config load(String configPath) throws IOException {
        instance = loadConfigFromFile(configPath);
        return instance;
    }

    private static Config loadConfig(String resourceName) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {
            // Try loading from classpath
            InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream(resourceName);
            if (is != null) {
                try (is) { // Java 9+ try-with-resources on effectively final variable
                    return mapper.readValue(is, Config.class);
                }
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load " + resourceName + " from classpath: " + e.getMessage());
        }
        return null; // Return null if not found or error
    }

    private static Config loadConfigFromFile(String path) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(new java.io.File(path), Config.class);
    }

    public static void reload() {
        instance = null;
    }
}
