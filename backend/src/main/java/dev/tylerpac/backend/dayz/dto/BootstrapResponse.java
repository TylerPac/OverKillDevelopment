package dev.tylerpac.backend.dayz.dto;

import java.util.List;

/**
 * Each part's data is itself a JSON document carried as a string, so the DayZ side can hand it unchanged to the mod
 * that asked for it (Enforce Script cannot pick apart arbitrary nested JSON).
 */
public record BootstrapResponse(PlayerResponse player, List<Part> parts) {

    public record Part(String name, String data) {
    }
}
