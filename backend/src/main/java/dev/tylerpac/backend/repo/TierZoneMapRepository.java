package dev.tylerpac.backend.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.tylerpac.backend.model.TierZoneMap;
import dev.tylerpac.backend.model.User;

public interface TierZoneMapRepository extends JpaRepository<TierZoneMap, Long> {

    List<TierZoneMap> findAllByUserOrderByMapNameAsc(User user);

    Optional<TierZoneMap> findByUserAndMapName(User user, String mapName);

    void deleteByUserAndMapName(User user, String mapName);

    boolean existsByUserAndMapName(User user, String mapName);
}
