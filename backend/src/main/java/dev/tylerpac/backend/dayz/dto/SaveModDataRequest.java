package dev.tylerpac.backend.dayz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import tools.jackson.databind.JsonNode;

public class SaveModDataRequest extends DayzAuthenticatedRequest {

    @NotBlank(message = "invalid_steam_id")
    @Pattern(regexp = "^\\d{17}$", message = "invalid_steam_id")
    private String steamId;

    @NotBlank(message = "invalid_mod_name")
    @Pattern(regexp = "^[A-Za-z0-9_.-]{1,64}$", message = "invalid_mod_name")
    private String modName;

    @NotNull(message = "data_required")
    private JsonNode data;

    public String getSteamId() {
        return steamId;
    }

    public void setSteamId(String steamId) {
        this.steamId = steamId;
    }

    public String getModName() {
        return modName;
    }

    public void setModName(String modName) {
        this.modName = modName;
    }

    public JsonNode getData() {
        return data;
    }

    public void setData(JsonNode data) {
        this.data = data;
    }
}
