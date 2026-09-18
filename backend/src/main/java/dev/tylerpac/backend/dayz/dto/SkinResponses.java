package dev.tylerpac.backend.dayz.dto;

import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * Response shapes for skins and skills. Maps are returned as arrays of small objects because Enforce Script's
 * JsonSerializer handles arrays of classes far more reliably than JSON objects with dynamic keys.
 */
public final class SkinResponses {

    private SkinResponses() {}

    public record SkinDto(
        long id,
        String skinKey,
        String weaponType,
        String displayName,
        String skinType,
        String variantClass,
        JsonNode textures,
        JsonNode materials
    ) {
    }

    public record CatalogResponse(List<SkinDto> skins) {
    }

    public record EquippedDto(String weaponType, long skinId) {
    }

    /** Named skinIds (not "owned"): "owned" is a reserved word in Enforce Script and cannot be a field name there. */
    public record OwnedResponse(List<Long> skinIds, List<EquippedDto> equipped) {
    }

    public record GrantResponse(boolean success, boolean granted, long skinId) {
    }

    public record EquipResponse(boolean success, String weaponType, long skinId) {
    }

    public record XpDto(String category, long xp) {
    }

    public record SkillQueryResponse(List<XpDto> xp) {
    }

    public record XpBatchResponse(boolean success, int updated, int skipped) {
    }
}
