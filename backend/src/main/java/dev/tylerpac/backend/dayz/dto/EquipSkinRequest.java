package dev.tylerpac.backend.dayz.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class EquipSkinRequest extends DayzAuthenticatedRequest {

    @NotBlank(message = "invalid_steam_id")
    @Pattern(regexp = "^\\d{17}$", message = "invalid_steam_id")
    private String steamId;

    @NotBlank(message = "invalid_weapon_type")
    @Pattern(regexp = "^[A-Za-z0-9_.-]{1,64}$", message = "invalid_weapon_type")
    private String weaponType;

    /** 0 = back to the default look. */
    @Min(value = 0, message = "invalid_skin_id")
    private long skinId;

    public String getSteamId() {
        return steamId;
    }

    public void setSteamId(String steamId) {
        this.steamId = steamId;
    }

    public String getWeaponType() {
        return weaponType;
    }

    public void setWeaponType(String weaponType) {
        this.weaponType = weaponType;
    }

    public long getSkinId() {
        return skinId;
    }

    public void setSkinId(long skinId) {
        this.skinId = skinId;
    }
}
