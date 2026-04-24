package dev.tylerpac.backend.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.tylerpac.backend.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findBySteam64Id(String steam64Id);
    Optional<User> findByDiscordUserId(String discordUserId);
    Optional<User> findByGithubUserId(String githubUserId);
    Optional<User> findByStripeCustomerId(String stripeCustomerId);
    boolean existsByEmail(String email);
}
