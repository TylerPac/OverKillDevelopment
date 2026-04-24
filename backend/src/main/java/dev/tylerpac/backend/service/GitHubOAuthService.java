package dev.tylerpac.backend.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GitHubOAuthService {

    private static final String GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_USER_URL = "https://api.github.com/user";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GitHubOAuthService(
        @Value("${app.auth.github.client-id}") String clientId,
        @Value("${app.auth.github.client-secret}") String clientSecret,
        @Value("${app.auth.github.redirect-uri}") String redirectUri,
        ObjectMapper objectMapper
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public String buildLinkUrl(String stateToken) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("client_id", clientId);
        query.put("redirect_uri", redirectUri);
        query.put("scope", "read:user");
        query.put("state", stateToken);
        return GITHUB_AUTHORIZE_URL + "?" + encodeQuery(query);
    }

    public GitHubProfile fetchProfileFromAuthorizationCode(String code) {
        String accessToken = exchangeCodeForAccessToken(code);

        HttpRequest profileRequest = HttpRequest.newBuilder()
            .uri(URI.create(GITHUB_USER_URL))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(profileRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalArgumentException("github_profile_request_failed");
            }

            JsonNode json = objectMapper.readTree(response.body());
            String id = json.path("id").asText("");
            String login = json.path("login").asText("");
            if (id.isBlank() || login.isBlank()) {
                throw new IllegalArgumentException("github_profile_invalid");
            }
            return new GitHubProfile(id, login);
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("github_profile_fetch_failed", ex);
        }
    }

    private String exchangeCodeForAccessToken(String code) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("code", code);
        form.put("redirect_uri", redirectUri);

        HttpRequest tokenRequest = HttpRequest.newBuilder()
            .uri(URI.create(GITHUB_TOKEN_URL))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(encodeQuery(form)))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalArgumentException("github_token_exchange_failed");
            }

            JsonNode json = objectMapper.readTree(response.body());
            String accessToken = json.path("access_token").asText("");
            if (accessToken.isBlank()) {
                throw new IllegalArgumentException("github_token_missing");
            }
            return accessToken;
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("github_token_exchange_failed", ex);
        }
    }

    private static String encodeQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    public record GitHubProfile(String id, String login) {}
}
