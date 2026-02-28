package dev.tylerpac.backend.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.tylerpac.backend.model.ProcessedStripeEvent;

public interface ProcessedStripeEventRepository extends JpaRepository<ProcessedStripeEvent, Long> {
    boolean existsByEventId(String eventId);
}
