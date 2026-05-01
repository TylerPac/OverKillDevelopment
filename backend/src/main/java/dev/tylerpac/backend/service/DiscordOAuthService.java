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

import dev.tylerpac.backend.util.JsonUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class DiscordOAuthService {

    private static final String DISCORD_AUTHORIZE_URL = "https://discord.com/oauth2/authorize";
    private static final String DISCORD_TOKEN_URL = "https://discord.com/api/oauth2/token";
    private static final String DISCORD_ME_URL = "https://discord.com/api/users/@me";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DiscordOAuthService(
        @Value("${app.auth.discord.client-id}") String clientId,
        @Value("${app.auth.discord.client-secret}") String clientSecret,
        @Value("${app.auth.discord.redirect-uri}") String redirectUri,
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
        query.put("response_type", "code");
        query.put("scope", "identify");
        query.put("state", stateToken);
        query.put("prompt", "consent");
        return DISCORD_AUTHORIZE_URL + "?" + encodeForm(query);
    }

    public DiscordProfile fetchProfileFromAuthorizationCode(String code) {
        String accessToken = exchangeCodeForAccessToken(code);

        HttpRequest profileRequest = HttpRequest.newBuilder()
            .uri(URI.create(DISCORD_ME_URL))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer " + accessToken)
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(profileRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalArgumentException("discord_profile_request_failed");
            }

            JsonNode json = objectMapper.readTree(response.body());
            String id = JsonUtils.textOrNull(json.path("id"));
            if (id == null) id = "";
            String username = JsonUtils.textOrNull(json.path("username"));
            if (username == null) username = "";
            if (id.isBlank() || username.isBlank()) {
                throw new IllegalArgumentException("discord_profile_invalid");
            }
            return new DiscordProfile(id, username);
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("discord_profile_fetch_failed", ex);
        }
    }

    private String exchangeCodeForAccessToken(String code) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", redirectUri);

        HttpRequest tokenRequest = HttpRequest.newBuilder()
            .uri(URI.create(DISCORD_TOKEN_URL))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form)))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalArgumentException("discord_token_exchange_failed");
            }

            JsonNode json = objectMapper.readTree(response.body());
            String token = JsonUtils.textOrNull(json.path("access_token"));
            if (token == null) token = "";
            if (token.isBlank()) {
                throw new IllegalArgumentException("discord_access_token_missing");
            }
            return token;
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("discord_token_exchange_failed", ex);
        }
    }

    private String encodeForm(Map<String, String> values) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                builder.append('&');
            }
            first = false;
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            builder.append('=');
            builder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    public record DiscordProfile(String id, String username) {
    }
}
