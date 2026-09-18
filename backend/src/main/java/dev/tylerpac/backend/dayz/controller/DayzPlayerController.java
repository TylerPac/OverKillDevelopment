package dev.tylerpac.backend.dayz.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.tylerpac.backend.dayz.dto.PlayerResponse;
import dev.tylerpac.backend.dayz.dto.PlayerQueryRequest;
import dev.tylerpac.backend.dayz.dto.RegisterPlayerRequest;
import dev.tylerpac.backend.dayz.service.DayzPlayerService;
import dev.tylerpac.backend.dayz.service.DayzServerAuthService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/dayz/players")
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class DayzPlayerController {

    private final DayzServerAuthService authService;
    private final DayzPlayerService playerService;

    public DayzPlayerController(DayzServerAuthService authService, DayzPlayerService playerService) {
        this.authService = authService;
        this.playerService = playerService;
    }

    @PostMapping
    public ResponseEntity<PlayerResponse> register(@Valid @RequestBody RegisterPlayerRequest request) {
        authService.verify(request.getServerId(), request.getApiKey());
        return ResponseEntity.ok(PlayerResponse.from(playerService.register(request.getSteamId(), request.getPlayerName())));
    }

    @PostMapping("/query")
    public ResponseEntity<PlayerResponse> query(@Valid @RequestBody PlayerQueryRequest request) {
        authService.verify(request.getServerId(), request.getApiKey());
        return ResponseEntity.ok(PlayerResponse.from(playerService.get(request.getSteamId())));
    }

    @GetMapping("/{steamId}")
    public ResponseEntity<PlayerResponse> get(
        @PathVariable String steamId,
        @RequestParam(required = false) String serverId,
        @RequestParam(required = false) String apiKey
    ) {
        authService.verify(serverId, apiKey);
        return ResponseEntity.ok(PlayerResponse.from(playerService.get(steamId)));
    }
}
