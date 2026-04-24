package dev.tylerpac.backend.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.model.UserGoogleCredential;

public interface UserGoogleCredentialRepository extends JpaRepository<UserGoogleCredential, Long> {
    Optional<UserGoogleCredential> findByUser(User user);
    void deleteByUser(User user);
}
