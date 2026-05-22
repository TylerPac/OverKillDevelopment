package dev.tylerpac.backend.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.tylerpac.backend.model.ProductRelease;

public interface ProductReleaseRepository extends JpaRepository<ProductRelease, String> {
}
