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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SteamAuthService {

    private static final String STEAM_OPENID_URL = "https://steamcommunity.com/openid/login";
    private static final Pattern STEAM_ID_PATTERN = Pattern.compile("^https://steamcommunity\\.com/openid/id/(\\d{17})$");

    private final String steamRealm;
    private final String steamReturnUrl;
    private final HttpClient httpClient;

    public SteamAuthService(
        @Value("${app.auth.steam.realm}") String steamRealm,
        @Value("${app.auth.steam.return-url}") String steamReturnUrl
    ) {
        this.steamRealm = steamRealm;
        this.steamReturnUrl = steamReturnUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public String buildLoginUrl() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("openid.ns", "http://specs.openid.net/auth/2.0");
        query.put("openid.mode", "checkid_setup");
        query.put("openid.identity", "http://specs.openid.net/auth/2.0/identifier_select");
        query.put("openid.claimed_id", "http://specs.openid.net/auth/2.0/identifier_select");
        query.put("openid.return_to", steamReturnUrl);
        query.put("openid.realm", steamRealm);
        return STEAM_OPENID_URL + "?" + encodeForm(query);
    }

    public String validateAndExtractSteam64Id(Map<String, String[]> requestParams) {
        String claimedId = firstParam(requestParams, "openid.claimed_id");
        if (!StringUtils.hasText(claimedId)) {
            throw new IllegalArgumentException("missing_claimed_id");
        }

        Map<String, String> verificationForm = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
            if (entry.getKey().startsWith("openid.")) {
                verificationForm.put(entry.getKey(), first(entry.getValue()));
            }
        }
        verificationForm.put("openid.mode", "check_authentication");

        String body = encodeForm(verificationForm);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(STEAM_OPENID_URL))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 || !response.body().contains("is_valid:true")) {
                throw new IllegalArgumentException("steam_assertion_invalid");
            }
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("steam_verification_failed", ex);
        }

        Matcher matcher = STEAM_ID_PATTERN.matcher(claimedId);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("steam_claimed_id_invalid");
        }

        return matcher.group(1);
    }

    private String firstParam(Map<String, String[]> params, String key) {
        return first(params.get(key));
    }

    private String first(String[] values) {
        return values != null && values.length > 0 ? values[0] : "";
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
}
