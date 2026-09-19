package dev.tylerpac.backend.dayz.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One call per player connect: registers the player and returns the data each mod asked for, so mods do not each
 * make their own request. "include" names the parts wanted (currently "skins" and "skills").
 */
public class BootstrapRequest extends DayzAuthenticatedRequest {

    @NotBlank(message = "invalid_steam_id")
    @Pattern(regexp = "^\\d{17}$", message = "invalid_steam_id")
    private String steamId;

    @NotBlank(message = "player_name_required")
    private String playerName;

    @Size(max = 8, message = "too_many_parts")
    private List<String> include;

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

    public List<String> getInclude() {
        return include;
    }

    public void setInclude(List<String> include) {
        this.include = include;
    }
}
