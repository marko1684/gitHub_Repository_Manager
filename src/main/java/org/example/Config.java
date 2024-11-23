package org.example;

public class Config {
    private String gitHubToken;
    private String gitHubUsername;

    public Config() {}

    public Config(String gitHubToken, String gitHubUsername) {
        this.gitHubToken = gitHubToken;
        this.gitHubUsername = gitHubUsername;
    }

    public String getGitHubToken() {
        return gitHubToken;
    }

    public String getGitHubUsername() {
        return gitHubUsername;
    }
}
