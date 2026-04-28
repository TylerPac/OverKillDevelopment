package dev.tylerpac.backend.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.tylerpac.backend.model.ShopAccessCode;

public interface ShopAccessCodeRepository extends JpaRepository<ShopAccessCode, Long> {
    Optional<ShopAccessCode> findByCode(String code);
    boolean existsByCode(String code);
}