package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;

public class ConfigReader {
    private static final String DEFAULT_CONFIG_PATH = "config.json";

    public static Config readConfig() throws IOException {
        return readConfig(DEFAULT_CONFIG_PATH);
    }

    public static Config readConfig(String filePath) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        Config config = objectMapper.readValue(new File(filePath), Config.class);
        validateConfig(config);
        return config;
    }

    private static void validateConfig(Config config) {
        if (config.getGitHubToken() == null || config.getGitHubToken().isEmpty()) {
            throw new IllegalArgumentException("GitHub token is missing in the configuration.");
        }
        if (config.getGitHubUsername() == null || config.getGitHubUsername().isEmpty()) {
            throw new IllegalArgumentException("GitHub username is missing in the configuration.");
        }
    }
}
