package dev.tylerpac.backend.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.model.UserTemplate;

public interface UserTemplateRepository extends JpaRepository<UserTemplate, Long> {
    List<UserTemplate> findByUser(User user);
    Optional<UserTemplate> findByUserAndSpreadsheetId(User user, String spreadsheetId);
}
