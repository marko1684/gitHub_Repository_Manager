package org.example;

import okhttp3.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Base64;

public class GitHubAPIClient {
    private static final String BASE_URL = "https://api.github.com/";
    private static final MediaType JSON = MediaType.get("application/json");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OkHttpClient client;
    private final Config config;

    public GitHubAPIClient(Config config) {
        this.client = new OkHttpClient();
        this.config = config;
    }

    private Request.Builder authorizedRequestBuilder(String url) {
        return new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + config.getGitHubToken());
    }

    private String executeRequest(Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response: " + response.code() + ", " +
                        (response.body() != null ? response.body().string() : "No response body"));
            }
            return response.body() != null ? response.body().string() : null;
        }
    }

    public String[] listRepos() throws IOException {
        String url = BASE_URL + "users/" + config.getGitHubUsername() + "/repos";
        Request request = authorizedRequestBuilder(url).build();

        String reposJson = executeRequest(request);
        JsonNode reposNode = OBJECT_MAPPER.readTree(reposJson);

        String[] repos = new String[reposNode.size()];
        for (int i = 0; i < reposNode.size(); i++) {
            repos[i] = reposNode.get(i).get("name").asText();
        }
        return repos;
    }

    public String defaultBranch(String repo) throws IOException {
        String url = BASE_URL + "repos/" + config.getGitHubUsername() + "/" + repo;
        Request request = authorizedRequestBuilder(url).build();

        String repoJson = executeRequest(request);
        JsonNode repoNode = OBJECT_MAPPER.readTree(repoJson);
        return repoNode.get("default_branch").asText();
    }

    public boolean createPullReq(String repo, String branch) throws IOException {
        String url = BASE_URL + "repos/" + config.getGitHubUsername() + "/" + repo + "/pulls";
        ObjectNode pullRequest = OBJECT_MAPPER.createObjectNode()
                .put("title", "Add file Hello.txt")
                .put("head", branch)
                .put("base", "main")
                .put("body", "This Pull Request adds a file named Hello.txt");

        RequestBody body = RequestBody.create(OBJECT_MAPPER.writeValueAsString(pullRequest), JSON);
        Request request = authorizedRequestBuilder(url).post(body).build();

        try {
            executeRequest(request);
            return true;
        } catch (IOException e) {
            System.err.println("Error creating pull request: " + e.getMessage());
            return false;
        }
    }

    public String getLatestCommitSha(String repo, String branch) throws IOException {
        String url = BASE_URL + "repos/" + config.getGitHubUsername() + "/" + repo + "/commits/" + branch;
        Request request = authorizedRequestBuilder(url).build();

        String responseJson = executeRequest(request);
        JsonNode node = OBJECT_MAPPER.readTree(responseJson);
        return node.get("sha").asText();
    }

    public boolean createBranch(String repo, String newBranch, String baseSha) throws IOException {
        String url = BASE_URL + "repos/" + config.getGitHubUsername() + "/" + repo + "/git/refs";
        ObjectNode branchNode = OBJECT_MAPPER.createObjectNode()
                .put("ref", "refs/heads/" + newBranch)
                .put("sha", baseSha);

        RequestBody body = RequestBody.create(OBJECT_MAPPER.writeValueAsString(branchNode), JSON);
        Request request = authorizedRequestBuilder(url).post(body).build();

        try {
            executeRequest(request);
            return true;
        } catch (IOException e) {
            if (e.getMessage().contains("Reference already exists")) {
                return true;
            }
            System.err.println("Error creating branch: " + e.getMessage());
            return false;
        }
    }

    public String createFile(String repo, String branch, String path, String content, String message) throws IOException {
        if (doesFileExist(repo, branch, path)) {
            return "Already exists";
        }

        String url = BASE_URL + "repos/" + config.getGitHubUsername() + "/" + repo + "/contents/" + path;
        ObjectNode fileNode = OBJECT_MAPPER.createObjectNode()
                .put("message", message)
                .put("content", Base64.getEncoder().encodeToString(content.getBytes()))
                .put("branch", branch);

        RequestBody body = RequestBody.create(OBJECT_MAPPER.writeValueAsString(fileNode), JSON);
        Request request = authorizedRequestBuilder(url).put(body).build();

        try {
            executeRequest(request);
            return "success";
        } catch (IOException e) {
            System.err.println("Error creating file: " + e.getMessage());
            return null;
        }
    }

    public String getFileSha(String repo, String branch, String path) throws IOException {
        String url = BASE_URL + "repos/" + config.getGitHubUsername() + "/" + repo + "/contents/" + path + "?ref=" + branch;

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "token " + config.getGitHubToken())
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JsonNode node = new ObjectMapper().readTree(responseBody);
                return node.get("sha").asText(); // Retrieve the SHA
            } else if (response.code() == 404) {
                return null; // File does not exist
            } else {
                System.err.printf("Failed to retrieve SHA. Response code: %d, message: %s%n",
                        response.code(), response.body() != null ? response.body().string() : "No response body");
                throw new IOException("Unexpected error while retrieving SHA.");
            }
        }
    }

    public boolean updateFile(String repo, String branch, String path, String content, String message) throws IOException {
        String url = BASE_URL + "repos/" + config.getGitHubUsername() + "/" + repo + "/contents/" + path;

        String sha = getFileSha(repo, branch, path);
        if (sha == null) {
            System.err.println("File not found or unable to retrieve SHA for file: " + path);
            return false;
        }

        ObjectNode fileNode = OBJECT_MAPPER.createObjectNode()
                .put("message", message)
                .put("content", Base64.getEncoder().encodeToString(content.getBytes()))
                .put("sha", sha)
                .put("branch", branch);

        RequestBody body = RequestBody.create(OBJECT_MAPPER.writeValueAsString(fileNode), JSON);
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "token " + config.getGitHubToken())
                .put(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return true;
            } else {
                System.err.printf("Failed to update file. Response code: %d, message: %s%n",
                        response.code(), response.body() != null ? response.body().string() : "No response body");
                return false;
            }
        } catch (IOException e) {
            System.err.println("IOException occurred: " + e.getMessage());
            return false;
        }
    }


    public boolean doesFileExist(String repo, String branch, String path) throws IOException {
        String url = BASE_URL + "repos/" + config.getGitHubUsername() + "/" + repo + "/contents/" + path + "?ref=" + branch;
        Request request = authorizedRequestBuilder(url).build();

        try {
            executeRequest(request);
            return true;
        } catch (IOException e) {
            if (e.getMessage().contains("404")) {
                return false;
            }
            throw e;
        }
    }

    public boolean doesPullRequestExist(String repo, String branch) throws IOException {
        String url = BASE_URL + "repos/" + config.getGitHubUsername() + "/" + repo + "/pulls?state=open&head=" + config.getGitHubUsername() + ":" + branch;

        Request request = authorizedRequestBuilder(url).build();

        try {
            String responseJson = executeRequest(request);
            JsonNode pullsNode = OBJECT_MAPPER.readTree(responseJson);
            return !pullsNode.isEmpty();
        } catch (IOException e) {
            System.err.println("Error checking for existing pull requests: " + e.getMessage());
            throw e;
        }
    }

}
