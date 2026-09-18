package dev.tylerpac.backend.dayz.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import dev.tylerpac.backend.dayz.model.DayzServer;
import dev.tylerpac.backend.dayz.repo.DayzServerRepository;

@Service
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class DayzServerAuthService {

    private static final long KNOWN_TTL_NANOS = 60_000_000_000L;
    private static final long UNKNOWN_TTL_NANOS = 10_000_000_000L;
    private static final int UNKNOWN_MAX_ENTRIES = 1024;
    private static final int MAX_CREDENTIAL_LENGTH = 256;
    private static final String DUMMY_HASH = sha256Hex("dayz-unknown-server");

    private record CachedServer(DayzServer server, long expiresAtNanos) {
    }

    private final DayzServerRepository serverRepository;

    /** Real servers: tiny and bounded by rows in dayz_servers, so an attacker can never evict them. */
    private final ConcurrentHashMap<String, CachedServer> known = new ConcurrentHashMap<>();

    /** Unknown ids are cached briefly and capped so random ids cost O(1) without growing memory or hitting the DB. */
    private final ConcurrentHashMap<String, Long> unknown = new ConcurrentHashMap<>();

    public DayzServerAuthService(DayzServerRepository serverRepository) {
        this.serverRepository = serverRepository;
    }

    /** Throws 401 "unauthorized" unless the server exists, is enabled and the key matches. */
    public void verify(String serverId, String apiKey) {
        if (!StringUtils.hasText(serverId) || !StringUtils.hasText(apiKey)
            || serverId.length() > MAX_CREDENTIAL_LENGTH || apiKey.length() > MAX_CREDENTIAL_LENGTH) {
            throw unauthorized();
        }

        DayzServer server = lookup(serverId).orElse(null);

        // Always hash and compare, even for unknown servers, so timing does not reveal which server ids exist.
        String expected = server == null ? DUMMY_HASH : server.apiKeyHash();
        boolean keyMatches = MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            sha256Hex(apiKey).getBytes(StandardCharsets.UTF_8));

        if (server == null || !server.enabled() || !keyMatches) {
            throw unauthorized();
        }
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("sha256_unavailable", ex);
        }
    }

    protected long nowNanos() {
        return System.nanoTime();
    }

    private Optional<DayzServer> lookup(String serverId) {
        long now = nowNanos();

        CachedServer cached = known.get(serverId);
        if (cached != null && now - cached.expiresAtNanos() < 0) {
            return Optional.of(cached.server());
        }

        Long unknownUntil = unknown.get(serverId);
        if (unknownUntil != null && now - unknownUntil < 0) {
            return Optional.empty();
        }

        Optional<DayzServer> loaded = serverRepository.findByServerId(serverId);
        if (loaded.isPresent()) {
            known.put(serverId, new CachedServer(loaded.get(), now + KNOWN_TTL_NANOS));
            unknown.remove(serverId);
        } else {
            known.remove(serverId);
            rememberUnknown(serverId, now);
        }
        return loaded;
    }

    private void rememberUnknown(String serverId, long now) {
        if (unknown.size() >= UNKNOWN_MAX_ENTRIES) {
            unknown.values().removeIf(expiresAt -> now - expiresAt >= 0);
        }
        if (unknown.size() < UNKNOWN_MAX_ENTRIES) {
            unknown.put(serverId, now + UNKNOWN_TTL_NANOS);
        }
    }

    private static DayzApiException unauthorized() {
        return new DayzApiException(HttpStatus.UNAUTHORIZED, "unauthorized");
    }
}
