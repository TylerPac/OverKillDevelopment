package dev.tylerpac.backend.downloads;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Talks to the shared download service inside the cluster. Overkill decides who may upload or download;
// the service only stores files and hands out short-lived links. Files are never streamed through here:
// browsers upload to and download from downloads.tylerpac.dev directly.
@Component
public class DownloadServiceClient {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient rest;
    private final boolean configured;

    public DownloadServiceClient(@Value("${app.downloads.internal-url:}") String internalUrl,
                                 @Value("${app.downloads.api-key:}") String apiKey) {
        this.configured = !internalUrl.isBlank() && !apiKey.isBlank();
        HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.rest = RestClient.builder()
            .baseUrl(internalUrl.isBlank() ? "http://localhost" : internalUrl.replaceAll("/$", ""))
            .requestFactory(factory)
            .defaultHeader("X-API-Key", apiKey)
            .build();
    }

    public boolean isConfigured() {
        return configured;
    }

    public JsonNode artifacts() {
        return call(() -> rest.get().uri("/api/artifacts").retrieve().body(JsonNode.class));
    }

    public JsonNode saveArtifact(String key, String displayName) {
        return call(() -> rest.put().uri("/api/artifacts/{key}", key).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("displayName", displayName)).retrieve().body(JsonNode.class));
    }

    public JsonNode startUpload(String key, Map<String, Object> request) {
        return call(() -> rest.post().uri("/api/artifacts/{key}/uploads", key).contentType(MediaType.APPLICATION_JSON)
            .body(request).retrieve().body(JsonNode.class));
    }

    public JsonNode upload(String uploadId) {
        return call(() -> rest.get().uri("/api/uploads/{id}", uploadId).retrieve().body(JsonNode.class));
    }

    public JsonNode setCurrent(String key, long versionId) {
        return call(() -> rest.post().uri("/api/artifacts/{key}/current", key).contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("versionId", versionId)).retrieve().body(JsonNode.class));
    }

    public JsonNode bundles() {
        return call(() -> rest.get().uri("/api/bundles").retrieve().body(JsonNode.class));
    }

    public JsonNode saveBundle(String key, Map<String, Object> request) {
        return call(() -> rest.put().uri("/api/bundles/{key}", key).contentType(MediaType.APPLICATION_JSON)
            .body(request).retrieve().body(JsonNode.class));
    }

    /** A signed link to the product's current bundle, valid for a few minutes. */
    public JsonNode bundleLink(String key) {
        return call(() -> rest.post().uri("/api/bundles/{key}/link", key).retrieve().body(JsonNode.class));
    }

    private JsonNode call(Supplier<JsonNode> request) {
        if (!configured) {
            throw new DownloadServiceException(503, "downloads_not_configured");
        }
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            throw new DownloadServiceException(e.getStatusCode().value(), errorCode(e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            throw new DownloadServiceException(503, "downloads_unavailable");
        }
    }

    private static String errorCode(String body) {
        try {
            String code = JSON.readTree(body).path("error").asString("");
            return code.matches("[a-z_]{1,64}") ? code : "downloads_error";
        } catch (RuntimeException e) {
            return "downloads_error";
        }
    }
}
