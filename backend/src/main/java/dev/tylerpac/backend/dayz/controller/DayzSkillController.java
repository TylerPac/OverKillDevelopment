package dev.tylerpac.backend.dayz.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.tylerpac.backend.dayz.dto.PlayerQueryRequest;
import dev.tylerpac.backend.dayz.dto.SkinResponses.SkillQueryResponse;
import dev.tylerpac.backend.dayz.dto.SkinResponses.XpBatchResponse;
import dev.tylerpac.backend.dayz.dto.XpBatchRequest;
import dev.tylerpac.backend.dayz.service.DayzServerAuthService;
import dev.tylerpac.backend.dayz.service.DayzSkillService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/dayz/skills")
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class DayzSkillController {

    private final DayzServerAuthService authService;
    private final DayzSkillService skillService;

    public DayzSkillController(DayzServerAuthService authService, DayzSkillService skillService) {
        this.authService = authService;
        this.skillService = skillService;
    }

    @PostMapping("/xp/batch")
    public ResponseEntity<XpBatchResponse> addXpBatch(@Valid @RequestBody XpBatchRequest request) {
        authService.verify(request.getServerId(), request.getApiKey());
        return ResponseEntity.ok(skillService.addXpBatch(request.getEntries()));
    }

    @PostMapping("/query")
    public ResponseEntity<SkillQueryResponse> query(@Valid @RequestBody PlayerQueryRequest request) {
        authService.verify(request.getServerId(), request.getApiKey());
        return ResponseEntity.ok(skillService.query(request.getSteamId()));
    }
}
