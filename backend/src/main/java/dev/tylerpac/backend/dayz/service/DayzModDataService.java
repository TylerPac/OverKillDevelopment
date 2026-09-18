package dev.tylerpac.backend.dayz.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import dev.tylerpac.backend.dayz.dto.SaveModDataResponse;
import dev.tylerpac.backend.dayz.repo.ModDataRepository;
import dev.tylerpac.backend.dayz.repo.PlayerRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class DayzModDataService {

    private static final Pattern MOD_NAME = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_JSON_DEPTH = 32;

    private final ModDataRepository modDataRepository;
    private final PlayerRepository playerRepository;
    private final int maxDataBytes;

    public DayzModDataService(
        ModDataRepository modDataRepository,
        PlayerRepository playerRepository,
        @Value("${app.dayz.max-data-bytes:262144}") int maxDataBytes
    ) {
        this.modDataRepository = modDataRepository;
        this.playerRepository = playerRepository;
        this.maxDataBytes = maxDataBytes;
    }

    public SaveModDataResponse save(String steamId, String modName, JsonNode data) {
        long id = DayzPlayerService.parseSteamId(steamId);
        validateModName(modName);
        if (data == null || !data.isObject()) {
            throw new DayzApiException(HttpStatus.BAD_REQUEST, "data_must_be_object");
        }

        if (exceedsDepth(data, MAX_JSON_DEPTH)) {
            throw new DayzApiException(HttpStatus.BAD_REQUEST, "data_too_deep");
        }

        String json = MAPPER.writeValueAsString(data);
        if (json.getBytes(StandardCharsets.UTF_8).length > maxDataBytes) {
            throw new DayzApiException(HttpStatus.PAYLOAD_TOO_LARGE, "payload_too_large");
        }

        LocalDateTime now = DayzPlayerService.utcNow();
        int affected = modDataRepository.upsert(id, modName, json, now);
        if (affected == 0 && playerRepository.findBySteamId(id).isEmpty()) {
            throw new DayzApiException(HttpStatus.NOT_FOUND, "player_not_found");
        }

        return new SaveModDataResponse(true, steamId, modName, now.toInstant(ZoneOffset.UTC).toString());
    }

    /** Returns the stored JSON exactly as the database holds it. */
    public String load(String modName, String steamId) {
        long id = DayzPlayerService.parseSteamId(steamId);
        validateModName(modName);
        return modDataRepository.findData(id, modName)
            .orElseThrow(() -> new DayzApiException(HttpStatus.NOT_FOUND, "data_not_found"));
    }

    /** Iterative so a hostile deeply nested body cannot overflow the stack; MySQL itself rejects JSON deeper than 100. */
    static boolean exceedsDepth(JsonNode root, int maxDepth) {
        java.util.ArrayDeque<JsonNode> nodes = new java.util.ArrayDeque<>();
        java.util.ArrayDeque<Integer> depths = new java.util.ArrayDeque<>();
        nodes.push(root);
        depths.push(1);
        while (!nodes.isEmpty()) {
            JsonNode node = nodes.pop();
            int depth = depths.pop();
            if (depth > maxDepth) {
                return true;
            }
            for (JsonNode child : node) {
                if (child.isObject() || child.isArray()) {
                    nodes.push(child);
                    depths.push(depth + 1);
                }
            }
        }
        return false;
    }

    private static void validateModName(String modName) {
        if (modName == null || !MOD_NAME.matcher(modName).matches()) {
            throw new DayzApiException(HttpStatus.BAD_REQUEST, "invalid_mod_name");
        }
    }
}
