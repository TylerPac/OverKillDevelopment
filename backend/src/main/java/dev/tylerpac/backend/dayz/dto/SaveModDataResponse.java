package dev.tylerpac.backend.dayz.dto;

public record SaveModDataResponse(boolean success, String steamId, String modName, String updatedAt) {
}
