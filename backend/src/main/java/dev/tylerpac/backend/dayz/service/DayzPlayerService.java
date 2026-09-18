package dev.tylerpac.backend.dayz.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import dev.tylerpac.backend.dayz.model.Player;
import dev.tylerpac.backend.dayz.repo.PlayerRepository;

@Service
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class DayzPlayerService {

    private static final int MAX_NAME_LENGTH = 128;

    private final PlayerRepository playerRepository;

    public DayzPlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public Player register(String steamId, String playerName) {
        long id = parseSteamId(steamId);
        String name = playerName == null ? "" : playerName.trim();
        if (name.isEmpty()) {
            throw new DayzApiException(HttpStatus.BAD_REQUEST, "player_name_required");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }

        playerRepository.upsert(id, name, utcNow());
        return playerRepository.findBySteamId(id)
            .orElseThrow(() -> new DayzApiException(HttpStatus.INTERNAL_SERVER_ERROR, "player_upsert_failed"));
    }

    public Player get(String steamId) {
        return playerRepository.findBySteamId(parseSteamId(steamId))
            .orElseThrow(() -> new DayzApiException(HttpStatus.NOT_FOUND, "player_not_found"));
    }

    static long parseSteamId(String steamId) {
        if (steamId == null || !steamId.matches("\\d{17}")) {
            throw new DayzApiException(HttpStatus.BAD_REQUEST, "invalid_steam_id");
        }
        return Long.parseLong(steamId);
    }

    static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }
}
