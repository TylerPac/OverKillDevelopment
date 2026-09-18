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

    private static final long CACHE_TTL_NANOS = 60_000_000_000L;
    private static final int CACHE_MAX_ENTRIES = 1024;
    private static final int MAX_CREDENTIAL_LENGTH = 256;

    private record CachedServer(DayzServer server, long expiresAtNanos) {
    }

    private final DayzServerRepository serverRepository;
    private final ConcurrentHashMap<String, CachedServer> cache = new ConcurrentHashMap<>();

    public DayzServerAuthService(DayzServerRepository serverRepository) {
        this.serverRepository = serverRepository;
    }

    /** Throws 401 "unauthorized" unless the server exists, is enabled and the key matches. */
    public void verify(String serverId, String apiKey) {
        if (!StringUtils.hasText(serverId) || !StringUtils.hasText(apiKey)
            || serverId.length() > MAX_CREDENTIAL_LENGTH || apiKey.length() > MAX_CREDENTIAL_LENGTH) {
            throw unauthorized();
        }

        Optional<DayzServer> server = lookup(serverId);
        if (server.isEmpty() || !server.get().enabled()) {
            throw unauthorized();
        }

        byte[] expected = server.get().apiKeyHash().getBytes(StandardCharsets.UTF_8);
        byte[] actual = sha256Hex(apiKey).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
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
        CachedServer cached = cache.get(serverId);
        if (cached != null && now - cached.expiresAtNanos() < 0) {
            return Optional.ofNullable(cached.server());
        }

        Optional<DayzServer> loaded = serverRepository.findByServerId(serverId);
        if (cache.size() < CACHE_MAX_ENTRIES) {
            cache.put(serverId, new CachedServer(loaded.orElse(null), now + CACHE_TTL_NANOS));
        }
        return loaded;
    }

    private static DayzApiException unauthorized() {
        return new DayzApiException(HttpStatus.UNAUTHORIZED, "unauthorized");
    }
}
