package dev.tylerpac.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private final Key key;

    public JwtUtil(
        @Value("${SPRING_JWT_SECRET:}") String secret,
        @Value("${app.security.allow-weak-jwt-secret:false}") boolean allowWeakJwtSecret
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("SPRING_JWT_SECRET is required.");
        }

        if (!allowWeakJwtSecret && (secret.length() < 32 || secret.contains("change_me"))) {
            throw new IllegalStateException("SPRING_JWT_SECRET must be at least 32 chars and not use placeholder values.");
        }

        byte[] keyBytes = secret.getBytes();
        this.key = Keys.hmacShaKeyFor(allowWeakJwtSecret ? padKey(keyBytes) : keyBytes);
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
                .setSubject(username)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + ttlMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }
}
