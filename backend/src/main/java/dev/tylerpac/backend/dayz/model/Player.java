package dev.tylerpac.backend.dayz.model;

import java.time.LocalDateTime;

public record Player(long steamId, String playerName, LocalDateTime firstSeen, LocalDateTime lastSeen) {
}
