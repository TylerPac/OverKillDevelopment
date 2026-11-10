package dev.tylerpac.backend.security;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Asks the shared login service which roles a player holds on Overkill. Admins are managed by the platform owner
// at auth.tylerpac.dev/admin, and asking on every admin request means a removal applies straight away.
// Authenticates with Overkill's own sign-in client id and secret, inside the cluster.
@Component
public class LoginRolesClient {

    static final String CLIENT_ID = "overkill";
    private static final String ADMIN = "admin";

    private final RestClient restClient;
    private final boolean configured;

    public LoginRolesClient(@Value("${app.login.internal-url}") String internalUrl,
                            @Value("${app.login.client-secret}") String clientSecret) {
        this.configured = !internalUrl.isBlank() && !clientSecret.isBlank();
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(2))
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        String credentials = Base64.getEncoder()
            .encodeToString((CLIENT_ID + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        this.restClient = RestClient.builder()
            .baseUrl(internalUrl.isBlank() ? "http://localhost" : internalUrl.replaceAll("/$", ""))
            .requestFactory(requestFactory)
            .defaultHeader("Authorization", "Basic " + credentials)
            .build();
    }

    /** Whether the login service says this Steam64 id is an Overkill admin. */
    public boolean isAdmin(String steam64Id) {
        if (!configured) {
            throw new LoginUnavailableException();
        }
        try {
            RolesResponse response = restClient.get()
                .uri(uri -> uri.path("/internal/roles").queryParam("steamId", steam64Id).build())
                .retrieve()
                .body(RolesResponse.class);
            return response != null && response.roles() != null && response.roles().contains(ADMIN);
        } catch (RestClientException e) {
            throw new LoginUnavailableException();
        }
    }

    record RolesResponse(List<String> roles) {
    }

    /** The login service could not answer (down, unreachable, or it rejected this site's credentials). */
    public static class LoginUnavailableException extends RuntimeException {
        public LoginUnavailableException() {
            super("login_unavailable", null, false, false);
        }
    }
}
