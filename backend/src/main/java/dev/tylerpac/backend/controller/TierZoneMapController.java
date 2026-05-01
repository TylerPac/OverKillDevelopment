package dev.tylerpac.backend.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.tylerpac.backend.model.TierZoneMap;
import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.repo.UserRepository;
import dev.tylerpac.backend.security.JwtUtil;
import dev.tylerpac.backend.service.TierZoneMapService;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/tier-zones")
public class TierZoneMapController {

    private final TierZoneMapService tierZoneMapService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public TierZoneMapController(
        TierZoneMapService tierZoneMapService,
        UserRepository userRepository,
        JwtUtil jwtUtil
    ) {
        this.tierZoneMapService = tierZoneMapService;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public ResponseEntity<?> listMaps(HttpServletRequest request) {
        User user = resolveCurrentUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");
        }

        List<Map<String, Object>> result = tierZoneMapService.listMaps(user).stream()
            .map(m -> Map.<String, Object>of(
                "mapName", m.getMapName(),
                "updatedAt", m.getUpdatedAt().toString()
            ))
            .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{mapName}")
    public ResponseEntity<?> getMap(
        @PathVariable String mapName,
        HttpServletRequest request
    ) {
        User user = resolveCurrentUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");
        }

        try {
            Optional<TierZoneMap> map = tierZoneMapService.getMap(user, mapName);
            if (map.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("map_not_found");
            }
            return ResponseEntity.ok(Map.of(
                "mapName", map.get().getMapName(),
                "polygonsJson", map.get().getPolygonsJson(),
                "updatedAt", map.get().getUpdatedAt().toString()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @GetMapping("/{mapName}/download")
    public ResponseEntity<?> downloadMap(
        @PathVariable String mapName,
        HttpServletRequest request
    ) {
        User user = resolveCurrentUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");
        }

        try {
            Optional<TierZoneMap> map = tierZoneMapService.getMap(user, mapName);
            if (map.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("map_not_found");
            }

            byte[] bytes = map.get().getPolygonsJson().getBytes(StandardCharsets.UTF_8);
            String fileName = mapName.replaceAll("[^\\w\\-.]", "_") + "_tiers.json";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDisposition(
                ContentDisposition.attachment().filename(fileName).build()
            );

            return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    @PutMapping("/{mapName}")
    public ResponseEntity<?> saveMap(
        @PathVariable String mapName,
        @RequestBody Map<String, String> body,
        HttpServletRequest request
    ) {
        User user = resolveCurrentUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");
        }

        String polygonsJson = body.get("polygonsJson");
        if (!StringUtils.hasText(polygonsJson)) {
            return ResponseEntity.badRequest().body("polygons_json_required");
        }

        try {
            TierZoneMap saved = tierZoneMapService.saveMap(user, mapName, polygonsJson);
            return ResponseEntity.ok(Map.of(
                "mapName", saved.getMapName(),
                "updatedAt", saved.getUpdatedAt().toString()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(422).body(ex.getMessage());
        }
    }

    @DeleteMapping("/{mapName}")
    public ResponseEntity<?> deleteMap(
        @PathVariable String mapName,
        HttpServletRequest request
    ) {
        User user = resolveCurrentUser(request);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");
        }

        try {
            boolean deleted = tierZoneMapService.deleteMap(user, mapName);
            if (!deleted) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("map_not_found");
            }
            return ResponseEntity.ok(Map.of("deleted", mapName));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        }
    }

    private User resolveCurrentUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7);
        if (!jwtUtil.validateToken(token)) {
            return null;
        }
        String username = jwtUtil.extractUsername(token);
        return userRepository.findByUsername(username).orElse(null);
    }
}
