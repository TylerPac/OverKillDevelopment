package dev.tylerpac.backend.dayz.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.tylerpac.backend.dayz.dto.DayzAuthenticatedRequest;
import dev.tylerpac.backend.dayz.dto.EquipSkinRequest;
import dev.tylerpac.backend.dayz.dto.GrantSkinRequest;
import dev.tylerpac.backend.dayz.dto.PlayerQueryRequest;
import dev.tylerpac.backend.dayz.dto.SkinResponses.CatalogResponse;
import dev.tylerpac.backend.dayz.dto.SkinResponses.EquipResponse;
import dev.tylerpac.backend.dayz.dto.SkinResponses.GrantResponse;
import dev.tylerpac.backend.dayz.dto.SkinResponses.OwnedResponse;
import dev.tylerpac.backend.dayz.service.DayzServerAuthService;
import dev.tylerpac.backend.dayz.service.DayzSkinService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/dayz/skins")
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class DayzSkinController {

    private final DayzServerAuthService authService;
    private final DayzSkinService skinService;

    public DayzSkinController(DayzServerAuthService authService, DayzSkinService skinService) {
        this.authService = authService;
        this.skinService = skinService;
    }

    @PostMapping("/catalog/query")
    public ResponseEntity<CatalogResponse> catalog(@RequestBody DayzAuthenticatedRequest request) {
        authService.verify(request.getServerId(), request.getApiKey());
        return ResponseEntity.ok(skinService.catalog());
    }

    @PostMapping("/owned/query")
    public ResponseEntity<OwnedResponse> owned(@Valid @RequestBody PlayerQueryRequest request) {
        authService.verify(request.getServerId(), request.getApiKey());
        return ResponseEntity.ok(skinService.owned(request.getSteamId()));
    }

    @PostMapping("/grant")
    public ResponseEntity<GrantResponse> grant(@Valid @RequestBody GrantSkinRequest request) {
        authService.verify(request.getServerId(), request.getApiKey());
        return ResponseEntity.ok(skinService.grant(request.getSteamId(), request.getSkinKey(), request.getSource()));
    }

    @PostMapping("/equip")
    public ResponseEntity<EquipResponse> equip(@Valid @RequestBody EquipSkinRequest request) {
        authService.verify(request.getServerId(), request.getApiKey());
        return ResponseEntity.ok(skinService.equip(request.getSteamId(), request.getWeaponType(), request.getSkinId()));
    }
}
