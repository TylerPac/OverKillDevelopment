package dev.tylerpac.backend.dayz.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import dev.tylerpac.backend.dayz.dto.BootstrapResponse;
import dev.tylerpac.backend.dayz.dto.PlayerResponse;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class DayzBootstrapService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DayzPlayerService playerService;
    private final DayzSkinService skinService;
    private final DayzSkillService skillService;

    public DayzBootstrapService(
        DayzPlayerService playerService,
        DayzSkinService skinService,
        DayzSkillService skillService
    ) {
        this.playerService = playerService;
        this.skinService = skinService;
        this.skillService = skillService;
    }

    public BootstrapResponse bootstrap(String steamId, String playerName, List<String> include) {
        PlayerResponse player = PlayerResponse.from(playerService.register(steamId, playerName));

        Set<String> wanted = new HashSet<>();
        if (include != null) {
            wanted.addAll(include);
        }

        List<BootstrapResponse.Part> parts = new ArrayList<>();
        if (wanted.contains("skins")) {
            parts.add(new BootstrapResponse.Part("skins", MAPPER.writeValueAsString(skinService.owned(steamId))));
        }
        if (wanted.contains("skills")) {
            parts.add(new BootstrapResponse.Part("skills", MAPPER.writeValueAsString(skillService.query(steamId))));
        }
        return new BootstrapResponse(player, parts);
    }
}
