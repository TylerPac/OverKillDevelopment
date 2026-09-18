package dev.tylerpac.backend.dayz.dto;

import java.time.ZoneOffset;

import dev.tylerpac.backend.dayz.model.Player;

public record PlayerResponse(String steamId, String playerName, String firstSeen, String lastSeen) {

    public static PlayerResponse from(Player p) {
        return new PlayerResponse(
            Long.toString(p.steamId()),
            p.playerName(),
            p.firstSeen().toInstant(ZoneOffset.UTC).toString(),
            p.lastSeen().toInstant(ZoneOffset.UTC).toString());
    }
}
