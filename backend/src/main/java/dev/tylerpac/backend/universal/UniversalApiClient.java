package dev.tylerpac.backend.universal;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;

// Calls this community's OverKill Universal API (MapLink and registered game servers). Server to server only: the
// browser never talks to it. The admin key authenticates this backend; the actor header names the signed-in admin
// for the Universal API's audit log. Blank base URL or key = admin pages answer 503 admin_not_configured.
@Component
public class UniversalApiClient {

    private static final String PLAYERS = "/internal/admin/players";
    private static final String MAPLINK = "/internal/admin/maplink/players";
    private static final String SERVERS = "/internal/admin/servers";
    private static final String INFO = "/internal/admin/info";
    private static final String ACTOR = "X-Actor-Steam64-Id";

    private final RestClient restClient;
    private final boolean configured;

    public UniversalApiClient(@Value("${app.universal-api.base-url:}") String baseUrl,
                              @Value("${app.universal-api.admin-key:}") String adminKey) {
        this.configured = !baseUrl.isBlank() && !adminKey.isBlank();
        // java.net.http, not HttpURLConnection: that one cannot send PATCH (server enable / disable).
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl.isBlank() ? "http://localhost" : baseUrl.replaceAll("/$", ""))
            .requestFactory(requestFactory)
            .defaultHeader("Authorization", "Bearer " + adminKey)
            .build();
    }

    /** Version and which features (MapLink, skins) the Universal API has on. */
    public JsonNode info() {
        return call(() -> restClient.get().uri(INFO).retrieve().body(JsonNode.class));
    }

    /** Name and Steam avatar for a player (profile lookup); the character itself comes from MapLink. */
    public JsonNode getPlayer(String steamId) {
        return call(() -> restClient.get().uri(PLAYERS + "/{steamId}", steamId).retrieve().body(JsonNode.class));
    }

    /** Where characters are now, most recently changed first; state blank = all (NONE / ALIVE / TRANSIT / DEAD). */
    public JsonNode listMapLinkPlayers(String state) {
        return call(() -> restClient.get()
            .uri(uri -> uri.path(MAPLINK).queryParam("state", state == null ? "" : state).build())
            .retrieve().body(JsonNode.class));
    }

    /** Where one player's character is and the characters MapLink saved for them (no payloads). */
    public JsonNode getMapLinkPlayer(String steamId) {
        return call(() -> restClient.get().uri(MAPLINK + "/{steamId}", steamId).retrieve().body(JsonNode.class));
    }

    /** One saved character as the exact JSON text the Universal API sent (re-writing it could change numbers). */
    public String getMapLinkState(String steamId, long stateId) {
        return call(() -> restClient.get().uri(MAPLINK + "/{steamId}/states/{stateId}", steamId, stateId)
            .retrieve().body(String.class));
    }

    /** Hands a saved character back to the player on serverId (live if online there, else at their next login). */
    public JsonNode restoreMapLinkState(String actor, String steamId, long stateId, String serverId) {
        Map<String, String> body = Map.of("serverId", serverId);
        return call(() -> restClient.post().uri(MAPLINK + "/{steamId}/states/{stateId}/restore", steamId, stateId)
            .header(ACTOR, actor).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class));
    }

    /** Every game server registered with this Universal API, with what it published to MapLink. */
    public JsonNode listServers() {
        return call(() -> restClient.get().uri(SERVERS).retrieve().body(JsonNode.class));
    }

    /** Body {"serverId", "apiKey"} (blank apiKey = generated). The response is the only time the key is returned. */
    public JsonNode registerServer(String actor, JsonNode body) {
        return call(() -> restClient.post().uri(SERVERS).header(ACTOR, actor)
            .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class));
    }

    /** Body {"enabled": false} locks the server out at once, whichever key it uses. */
    public JsonNode setServerEnabled(String actor, String serverId, JsonNode body) {
        return call(() -> restClient.patch().uri(SERVERS + "/{serverId}", serverId).header(ACTOR, actor)
            .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(JsonNode.class));
    }

    /** Removes a disabled server and what it published; 409 server_enabled while it is still enabled. */
    public void deleteServer(String actor, String serverId) {
        call(() -> restClient.delete().uri(SERVERS + "/{serverId}", serverId).header(ACTOR, actor)
            .retrieve().toBodilessEntity());
    }

    private <T> T call(Supplier<T> request) {
        if (!configured) {
            throw new UniversalApiException(HttpStatus.SERVICE_UNAVAILABLE, "admin_not_configured");
        }
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 401) {
                // Our key was rejected: a deployment problem, not the signed-in user's fault.
                throw new UniversalApiException(HttpStatus.BAD_GATEWAY, "universal_api_rejected_key");
            }
            if (status.is4xxClientError()) {
                // The Universal API answers with short plain-text codes (server_id_taken, invalid_api_key, ...).
                String code = e.getResponseBodyAsString().trim();
                throw new UniversalApiException(status, code.matches("[a-z_]{1,64}") ? code : "request_rejected");
            }
            throw new UniversalApiException(HttpStatus.BAD_GATEWAY, "universal_api_unavailable");
        } catch (RestClientException e) {
            throw new UniversalApiException(HttpStatus.BAD_GATEWAY, "universal_api_unavailable");
        }
    }

    /** Carries a status and a short error code back to the browser. */
    public static class UniversalApiException extends RuntimeException {

        private final HttpStatusCode status;

        public UniversalApiException(HttpStatusCode status, String code) {
            super(code);
            this.status = status;
        }

        public HttpStatusCode status() {
            return status;
        }

        public String code() {
            return getMessage();
        }
    }
}
