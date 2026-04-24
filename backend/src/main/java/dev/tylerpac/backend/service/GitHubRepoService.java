package dev.tylerpac.backend.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

@Service
public class GitHubRepoService {

    private static final Logger log = LoggerFactory.getLogger(GitHubRepoService.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";

    private final String botToken;
    private final Environment env;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GitHubRepoService(
        @org.springframework.beans.factory.annotation.Value("${app.github.bot-token:}") String botToken,
        Environment env,
        ObjectMapper objectMapper
    ) {
        this.botToken = botToken;
        this.env = env;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * Grants the given GitHub username read ("pull") access to all repositories
     * mapped to the given product ID. Supports comma-separated list of owner/repo values.
     * No-ops silently if any configuration is missing.
     */
    public void grantRepoAccess(String githubUsername, String productId) {
        if (githubUsername == null || githubUsername.isBlank()) {
            return;
        }
        if (botToken == null || botToken.isBlank()) {
            log.warn("app.github.bot-token not configured — skipping GitHub repo access grant for user '{}' product '{}'", githubUsername, productId);
            return;
        }

        String repoProperty = "app.shop.github-repo." + productId;
        String ownerRepoList = env.getProperty(repoProperty, "");
        if (ownerRepoList.isBlank()) {
            log.debug("No GitHub repo configured for product '{}' (property '{}') — skipping repo access grant", productId, repoProperty);
            return;
        }

        String[] repos = ownerRepoList.split(",");
        for (String repoStr : repos) {
            repoStr = repoStr.trim();
            if (repoStr.isEmpty()) continue;
            String[] parts = repoStr.split("/", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                log.warn("Invalid GitHub repo format '{}' for property '{}' — expected 'owner/repo'", repoStr, repoProperty);
                continue;
            }
            String owner = parts[0];
            String repo = parts[1];
            addCollaborator(owner, repo, githubUsername);
        }
    }

    private void addCollaborator(String owner, String repo, String username) {
        String url = GITHUB_API_BASE + "/repos/" + owner + "/" + repo + "/collaborators/" + username;
        String body;
        try {
            body = objectMapper.writeValueAsString(java.util.Map.of("permission", "pull"));
        } catch (tools.jackson.core.JacksonException ex) {
            log.error("Failed to serialize GitHub collaborator request body", ex);
            return;
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer " + botToken)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            switch (status) {
                case 201 -> log.info("Granted GitHub repo access: {}/{} -> {}", owner, repo, username);
                case 204 -> log.info("GitHub repo access already present: {}/{} -> {}", owner, repo, username);
                default -> log.warn("GitHub add-collaborator returned status {} for {}/{} -> {}: {}", status, owner, repo, username, response.body());
            }
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Failed to add GitHub collaborator {}/{} -> {}", owner, repo, username, ex);
        }
    }
}
