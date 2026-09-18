package dev.tylerpac.backend.dayz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class RegisterPlayerRequest extends DayzAuthenticatedRequest {

    @NotBlank(message = "invalid_steam_id")
    @Pattern(regexp = "^\\d{17}$", message = "invalid_steam_id")
    private String steamId;

    @NotBlank(message = "player_name_required")
    private String playerName;

    public String getSteamId() {
        return steamId;
    }

    public void setSteamId(String steamId) {
        this.steamId = steamId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }
}
