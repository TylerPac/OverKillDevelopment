package dev.tylerpac.backend.dayz.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import dev.tylerpac.backend.dayz.dto.SkinResponses.CatalogResponse;
import dev.tylerpac.backend.dayz.dto.SkinResponses.EquipResponse;
import dev.tylerpac.backend.dayz.dto.SkinResponses.EquippedDto;
import dev.tylerpac.backend.dayz.dto.SkinResponses.GrantResponse;
import dev.tylerpac.backend.dayz.dto.SkinResponses.OwnedResponse;
import dev.tylerpac.backend.dayz.dto.SkinResponses.SkinDto;
import dev.tylerpac.backend.dayz.model.Skin;
import dev.tylerpac.backend.dayz.repo.PlayerRepository;
import dev.tylerpac.backend.dayz.repo.PlayerSkinRepository;
import dev.tylerpac.backend.dayz.repo.SkinRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class DayzSkinService {

    private static final long CATALOG_TTL_NANOS = 60_000_000_000L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record CachedCatalog(CatalogResponse catalog, long expiresAtNanos) {
    }

    private final SkinRepository skinRepository;
    private final PlayerSkinRepository playerSkinRepository;
    private final PlayerRepository playerRepository;

    private volatile CachedCatalog cachedCatalog;

    public DayzSkinService(
        SkinRepository skinRepository,
        PlayerSkinRepository playerSkinRepository,
        PlayerRepository playerRepository
    ) {
        this.skinRepository = skinRepository;
        this.playerSkinRepository = playerSkinRepository;
        this.playerRepository = playerRepository;
    }

    protected long nowNanos() {
        return System.nanoTime();
    }

    /** Single cached entry, so catalog reads are O(1) no matter how many servers ask. */
    public CatalogResponse catalog() {
        long now = nowNanos();
        CachedCatalog cached = cachedCatalog;
        if (cached != null && now - cached.expiresAtNanos() < 0) {
            return cached.catalog();
        }

        List<SkinDto> skins = new ArrayList<>();
        for (Skin skin : skinRepository.findAllEnabled()) {
            skins.add(toDto(skin));
        }
        CatalogResponse catalog = new CatalogResponse(skins);
        cachedCatalog = new CachedCatalog(catalog, now + CATALOG_TTL_NANOS);
        return catalog;
    }

    public OwnedResponse owned(String steamId) {
        long id = DayzPlayerService.parseSteamId(steamId);
        List<EquippedDto> equipped = new ArrayList<>();
        for (Map.Entry<String, Long> e : playerSkinRepository.findEquipped(id).entrySet()) {
            equipped.add(new EquippedDto(e.getKey(), e.getValue()));
        }
        return new OwnedResponse(playerSkinRepository.findOwnedSkinIds(id), equipped);
    }

    public GrantResponse grant(String steamId, String skinKey, String source) {
        long id = DayzPlayerService.parseSteamId(steamId);
        Skin skin = skinRepository.findEnabledByKey(skinKey)
            .orElseThrow(() -> new DayzApiException(HttpStatus.NOT_FOUND, "skin_not_found"));

        int inserted = playerSkinRepository.grant(id, skin.id(), source, DayzPlayerService.utcNow());
        if (inserted == 0 && playerRepository.findBySteamId(id).isEmpty()) {
            throw new DayzApiException(HttpStatus.NOT_FOUND, "player_not_found");
        }
        return new GrantResponse(true, inserted > 0, skin.id());
    }

    public EquipResponse equip(String steamId, String weaponType, long skinId) {
        long id = DayzPlayerService.parseSteamId(steamId);

        if (skinId == 0) {
            playerSkinRepository.clearEquipped(id, weaponType);
            return new EquipResponse(true, weaponType, 0);
        }

        Skin skin = skinRepository.findEnabledById(skinId)
            .orElseThrow(() -> new DayzApiException(HttpStatus.NOT_FOUND, "skin_not_found"));
        if (!skin.weaponType().equals(weaponType)) {
            throw new DayzApiException(HttpStatus.BAD_REQUEST, "skin_wrong_weapon");
        }
        if (!playerSkinRepository.owns(id, skinId)) {
            throw new DayzApiException(HttpStatus.FORBIDDEN, "skin_not_owned");
        }

        playerSkinRepository.setEquipped(id, weaponType, skinId);
        return new EquipResponse(true, weaponType, skinId);
    }

    static SkinDto toDto(Skin skin) {
        return new SkinDto(
            skin.id(), skin.skinKey(), skin.weaponType(), skin.displayName(),
            parseArray(skin.textures()), parseArray(skin.materials()));
    }

    private static JsonNode parseArray(String json) {
        JsonNode node = MAPPER.readTree(json);
        if (node == null || !node.isArray()) {
            return MAPPER.createArrayNode();
        }
        return node;
    }
}
