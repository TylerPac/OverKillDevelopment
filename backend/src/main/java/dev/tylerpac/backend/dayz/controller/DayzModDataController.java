package dev.tylerpac.backend.dayz.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.tylerpac.backend.dayz.dto.SaveModDataRequest;
import dev.tylerpac.backend.dayz.dto.SaveModDataResponse;
import dev.tylerpac.backend.dayz.service.DayzModDataService;
import dev.tylerpac.backend.dayz.service.DayzServerAuthService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/dayz/mod-data")
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class DayzModDataController {

    private final DayzServerAuthService authService;
    private final DayzModDataService modDataService;

    public DayzModDataController(DayzServerAuthService authService, DayzModDataService modDataService) {
        this.authService = authService;
        this.modDataService = modDataService;
    }

    @PostMapping
    public ResponseEntity<SaveModDataResponse> save(@Valid @RequestBody SaveModDataRequest request) {
        authService.verify(request.getServerId(), request.getApiKey());
        return ResponseEntity.ok(modDataService.save(request.getSteamId(), request.getModName(), request.getData()));
    }

    /** Body is only the stored mod JSON so DayZ can deserialize it straight into its own model class. */
    @GetMapping(value = "/{modName}/{steamId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> load(
        @PathVariable String modName,
        @PathVariable String steamId,
        @RequestParam(required = false) String serverId,
        @RequestParam(required = false) String apiKey
    ) {
        authService.verify(serverId, apiKey);
        return ResponseEntity.ok(modDataService.load(modName, steamId));
    }
}
