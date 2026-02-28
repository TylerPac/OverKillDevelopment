package dev.tylerpac.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.model.UserToken;
import dev.tylerpac.backend.model.UserTokenPurpose;
import dev.tylerpac.backend.repo.UserTokenRepository;

@Service
public class UserTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserTokenRepository userTokenRepository;

    public UserTokenService(UserTokenRepository userTokenRepository) {
        this.userTokenRepository = userTokenRepository;
    }

    @Transactional
    public String issueToken(User user, UserTokenPurpose purpose, Duration ttl) {
        userTokenRepository.deleteByUserAndPurpose(user, purpose);

        String rawToken = generateRawToken();
        UserToken entity = new UserToken();
        entity.setUser(user);
        entity.setPurpose(purpose);
        entity.setTokenHash(hashToken(rawToken));
        entity.setCreatedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plus(ttl));
        entity.setUsedAt(null);

        userTokenRepository.save(entity);
        return rawToken;
    }

    @Transactional
    public Optional<User> consumeToken(String rawToken, UserTokenPurpose purpose) {
        String hash = hashToken(rawToken);
        Optional<UserToken> tokenOpt = userTokenRepository
            .findByTokenHashAndPurposeAndUsedAtIsNullAndExpiresAtAfter(hash, purpose, Instant.now());

        if (tokenOpt.isEmpty()) {
            return Optional.empty();
        }

        UserToken token = tokenOpt.get();
        token.setUsedAt(Instant.now());
        userTokenRepository.save(token);
        return Optional.of(token.getUser());
    }

    @Transactional(readOnly = true)
    public boolean isAlreadyVerifiedFromToken(String rawToken) {
        String hash = hashToken(rawToken);
        Optional<UserToken> tokenOpt = userTokenRepository.findByTokenHashAndPurpose(hash, UserTokenPurpose.EMAIL_VERIFICATION);
        if (tokenOpt.isEmpty()) {
            return false;
        }

        UserToken token = tokenOpt.get();
        return token.getUsedAt() != null && token.getUser().isEmailVerified();
    }

    @Transactional
    public void revokeForUser(User user, UserTokenPurpose purpose) {
        userTokenRepository.deleteByUserAndPurpose(user, purpose);
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}