package dev.tylerpac.backend.dto;

import java.util.List;

/** A product an admin gave a player for free (source "grant"), or one redeemed from an old access code ("code"). */
public record ProductGrantResponse(
    Long id,
    String steamId,
    String username,
    String productId,
    String productName,
    String source,
    String grantedBy,
    String status,
    String createdAt
) {

    /** What one grant request did: products given, and products the player already owned. */
    public record Result(String steamId, List<String> granted, List<String> alreadyOwned) {
    }
}
