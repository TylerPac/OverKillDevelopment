package dev.tylerpac.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.tylerpac.backend.util.JsonUtils;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class JwtUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Key key;
    private final byte[] keyBytes;

    public JwtUtil(
        @Value("${JWT_SECRET:${SPRING_JWT_SECRET:}}") String secret,
        @Value("${app.security.allow-weak-jwt-secret:false}") boolean allowWeakJwtSecret
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is required.");
        }
        if (!allowWeakJwtSecret && (secret.length() < 32 || secret.contains("change_me"))) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 chars and not use placeholder values.");
        }
        byte[] kb = secret.getBytes();
        byte[] used = allowWeakJwtSecret ? padKey(kb) : kb;
        this.keyBytes = used;
        this.key = Keys.hmacShaKeyFor(used);
    }

    private byte[] padKey(byte[] orig) {
        if (orig.length >= 32) return orig;
        byte[] b = new byte[32];
        System.arraycopy(orig, 0, b, 0, orig.length);
        for (int i = orig.length; i < b.length; i++) b[i] = (byte) '0';
        return b;
    }

    public String generateToken(String username, long ttlMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMs))
                .signWith(key)
                .compact();
    }

    public String extractUsername(String token) {
        JsonNode payload = parseAndVerify(token);
        String sub = JsonUtils.textOrNull(payload.path("sub"));
        if (sub == null) throw new JwtException("jwt_missing_subject");
        return sub;
    }

    public boolean validateToken(String token) {
        if (token == null) return false;
        String t = token.trim();
        if (t.isEmpty()) return false;
        try {
            parseAndVerify(t);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // Manual HS-family JWT verification - avoids all deprecated JJWT parser APIs
    private JsonNode parseAndVerify(String token) {
        if (token == null) throw new JwtException("invalid_token");
        String t = token.trim();
        if (t.startsWith("Bearer ")) t = t.substring(7).trim();
        String[] parts = t.split("\\.");
        if (parts.length != 3) throw new JwtException("invalid_token_format");

        String headerB64  = parts[0];
        String payloadB64 = parts[1];
        String sigB64     = parts[2];

        try {
            byte[]   headerBytes = Base64.getUrlDecoder().decode(headerB64);
            JsonNode header      = OBJECT_MAPPER.readTree(headerBytes);
            String   alg         = JsonUtils.textOrEmpty(header.path("alg"));

            String macAlg;
            if      ("HS256".equalsIgnoreCase(alg)) macAlg = "HmacSHA256";
            else if ("HS384".equalsIgnoreCase(alg)) macAlg = "HmacSHA384";
            else if ("HS512".equalsIgnoreCase(alg)) macAlg = "HmacSHA512";
            else throw new JwtException("unsupported_alg: " + alg);

            Mac mac = Mac.getInstance(macAlg);
            mac.init(new SecretKeySpec(keyBytes, macAlg));
            byte[] signingInput = (headerB64 + "." + payloadB64).getBytes(StandardCharsets.US_ASCII);
            byte[] expectedSig  = mac.doFinal(signingInput);
            byte[] actualSig    = Base64.getUrlDecoder().decode(sigB64);
            if (!MessageDigest.isEqual(expectedSig, actualSig)) {
                throw new JwtException("invalid_signature");
            }

            byte[]   payloadBytes = Base64.getUrlDecoder().decode(payloadB64);
            JsonNode payload      = OBJECT_MAPPER.readTree(payloadBytes);

            if (payload.has("exp")) {
                long expSec    = payload.path("exp").asLong(0L);
                long expMillis = expSec * 1000L;
                if (System.currentTimeMillis() > expMillis) {
                    throw new JwtException("token_expired");
                }
            }

            return payload;
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new JwtException("jwt_parse_failed", ex);
        } catch (IllegalArgumentException ex) {
            throw new JwtException("jwt_invalid_base64", ex);
        }
    }
}
