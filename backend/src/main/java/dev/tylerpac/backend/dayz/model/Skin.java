package dev.tylerpac.backend.dayz.model;

/**
 * textures/materials are raw JSON arrays exactly as stored. skinType is "texture" (re-texture the held weapon) or
 * "item" (swap the held weapon for variantClass, e.g. Dank_Receiver_SA58 -> Dank_Receiver_SA58_Gold).
 */
public record Skin(
    long id,
    String skinKey,
    String weaponType,
    String displayName,
    String textures,
    String materials,
    String skinType,
    String variantClass
) {
}
