package dev.tylerpac.backend.dayz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class GrantSkinRequest extends DayzAuthenticatedRequest {

    @NotBlank(message = "invalid_steam_id")
    @Pattern(regexp = "^\\d{17}$", message = "invalid_steam_id")
    private String steamId;

    @NotBlank(message = "invalid_skin_key")
    @Pattern(regexp = "^[A-Za-z0-9_.-]{1,64}$", message = "invalid_skin_key")
    private String skinKey;

    @NotBlank(message = "invalid_source")
    @Pattern(regexp = "^(admin|purchase|level_reward)$", message = "invalid_source")
    private String source;

    public String getSteamId() {
        return steamId;
    }

    public void setSteamId(String steamId) {
        this.steamId = steamId;
    }

    public String getSkinKey() {
        return skinKey;
    }

    public void setSkinKey(String skinKey) {
        this.skinKey = skinKey;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
