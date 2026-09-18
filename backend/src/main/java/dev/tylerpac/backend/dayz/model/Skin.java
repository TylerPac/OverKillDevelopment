package dev.tylerpac.backend.dayz.model;

/** textures/materials are raw JSON arrays exactly as stored. */
public record Skin(long id, String skinKey, String weaponType, String displayName, String textures, String materials) {
}
