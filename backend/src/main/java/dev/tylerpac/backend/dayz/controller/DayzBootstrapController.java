package dev.tylerpac.backend.dayz.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.tylerpac.backend.dayz.dto.BootstrapRequest;
import dev.tylerpac.backend.dayz.dto.BootstrapResponse;
import dev.tylerpac.backend.dayz.service.DayzBootstrapService;
import dev.tylerpac.backend.dayz.service.DayzServerAuthService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/dayz/bootstrap")
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class DayzBootstrapController {

    private final DayzServerAuthService authService;
    private final DayzBootstrapService bootstrapService;

    public DayzBootstrapController(DayzServerAuthService authService, DayzBootstrapService bootstrapService) {
        this.authService = authService;
        this.bootstrapService = bootstrapService;
    }

    @PostMapping
    public ResponseEntity<BootstrapResponse> bootstrap(@Valid @RequestBody BootstrapRequest request) {
        authService.verify(request.getServerId(), request.getApiKey());
        return ResponseEntity.ok(bootstrapService.bootstrap(request.getSteamId(), request.getPlayerName(), request.getInclude()));
    }
}
