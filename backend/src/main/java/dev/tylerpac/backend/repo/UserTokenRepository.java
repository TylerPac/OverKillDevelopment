package dev.tylerpac.backend.repo;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.model.UserToken;
import dev.tylerpac.backend.model.UserTokenPurpose;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    Optional<UserToken> findByTokenHashAndPurposeAndUsedAtIsNullAndExpiresAtAfter(
        String tokenHash,
        UserTokenPurpose purpose,
        Instant now
    );

    Optional<UserToken> findByTokenHashAndPurpose(String tokenHash, UserTokenPurpose purpose);

    void deleteByUserAndPurpose(User user, UserTokenPurpose purpose);
}