package dev.tylerpac.backend.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import dev.tylerpac.backend.model.TierZoneMap;
import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.repo.TierZoneMapRepository;

@Service
public class TierZoneMapService {

    private static final int MAX_MAP_NAME_LENGTH = 100;
    private static final int MAX_MAPS_PER_USER = 20;
    private static final int MAX_POLYGONS_JSON_BYTES = 5 * 1024 * 1024; // 5 MB

    private final TierZoneMapRepository repository;

    public TierZoneMapService(TierZoneMapRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TierZoneMap> listMaps(User user) {
        return repository.findAllByUserOrderByMapNameAsc(user);
    }

    @Transactional(readOnly = true)
    public Optional<TierZoneMap> getMap(User user, String mapName) {
        return repository.findByUserAndMapName(user, sanitizeMapName(mapName));
    }

    @Transactional
    public TierZoneMap saveMap(User user, String mapName, String polygonsJson) {
        String name = sanitizeMapName(mapName);

        if (!StringUtils.hasText(polygonsJson)) {
            throw new IllegalArgumentException("polygons_json_required");
        }
        if (polygonsJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_POLYGONS_JSON_BYTES) {
            throw new IllegalArgumentException("polygons_json_too_large");
        }

        Optional<TierZoneMap> existing = repository.findByUserAndMapName(user, name);
        if (existing.isPresent()) {
            TierZoneMap map = existing.get();
            map.setPolygonsJson(polygonsJson);
            return repository.save(map);
        }

        long count = repository.findAllByUserOrderByMapNameAsc(user).size();
        if (count >= MAX_MAPS_PER_USER) {
            throw new IllegalStateException("max_maps_reached");
        }

        TierZoneMap map = new TierZoneMap();
        map.setUser(user);
        map.setMapName(name);
        map.setPolygonsJson(polygonsJson);
        return repository.save(map);
    }

    @Transactional
    public boolean deleteMap(User user, String mapName) {
        String name = sanitizeMapName(mapName);
        if (!repository.existsByUserAndMapName(user, name)) {
            return false;
        }
        repository.deleteByUserAndMapName(user, name);
        return true;
    }

    private String sanitizeMapName(String mapName) {
        if (!StringUtils.hasText(mapName)) {
            throw new IllegalArgumentException("map_name_required");
        }
        String trimmed = mapName.trim();
        if (trimmed.length() > MAX_MAP_NAME_LENGTH) {
            throw new IllegalArgumentException("map_name_too_long");
        }
        if (!trimmed.matches("[\\w\\-. ]+")) {
            throw new IllegalArgumentException("map_name_invalid_characters");
        }
        return trimmed;
    }
}
