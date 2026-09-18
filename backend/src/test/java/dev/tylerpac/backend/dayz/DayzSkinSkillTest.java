package dev.tylerpac.backend.dayz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import dev.tylerpac.backend.dayz.config.DayzDatabase;
import dev.tylerpac.backend.dayz.controller.DayzExceptionHandler;
import dev.tylerpac.backend.dayz.controller.DayzSkillController;
import dev.tylerpac.backend.dayz.controller.DayzSkinController;
import dev.tylerpac.backend.dayz.repo.DayzServerRepository;
import dev.tylerpac.backend.dayz.repo.PlayerRepository;
import dev.tylerpac.backend.dayz.repo.PlayerSkinRepository;
import dev.tylerpac.backend.dayz.repo.SkillXpRepository;
import dev.tylerpac.backend.dayz.repo.SkinRepository;
import dev.tylerpac.backend.dayz.service.DayzApiException;
import dev.tylerpac.backend.dayz.service.DayzPlayerService;
import dev.tylerpac.backend.dayz.service.DayzServerAuthService;
import dev.tylerpac.backend.dayz.service.DayzSkillService;
import dev.tylerpac.backend.dayz.service.DayzSkinService;

class DayzSkinSkillTest {

    private static final String STEAM_ID = "76561198012345678";
    private static final String CREDS = "\"serverId\":\"overkill-test\",\"apiKey\":\"secret\"";

    private DayzDatabase db;
    private DayzPlayerService playerService;
    private DayzSkinService skinService;
    private DayzSkillService skillService;
    private MockMvc mvc;
    private long akSkinId;
    private long m4SkinId;

    @BeforeEach
    void setUp() {
        db = DayzTestSupport.newDatabase();
        DayzServerRepository serverRepository = new DayzServerRepository(db);
        PlayerRepository playerRepository = new PlayerRepository(db);
        serverRepository.insert("overkill-test", DayzServerAuthService.sha256Hex("secret"), LocalDateTime.now());

        playerService = new DayzPlayerService(playerRepository);
        skinService = new DayzSkinService(new SkinRepository(db), new PlayerSkinRepository(db), playerRepository);
        skillService = new DayzSkillService(new SkillXpRepository(db));
        DayzServerAuthService authService = new DayzServerAuthService(serverRepository);

        akSkinId = insertSkin("AK_Test", "AKM", "Test AK", "[\"a.paa\",\"b.paa\"]", true);
        m4SkinId = insertSkin("M4_Test", "M4A1", "Test M4", "[\"m4.paa\"]", true);
        insertSkin("AK_Hidden", "AKM", "Hidden", "[]", false);

        mvc = MockMvcBuilders
            .standaloneSetup(new DayzSkinController(authService, skinService), new DayzSkillController(authService, skillService))
            .setControllerAdvice(new DayzExceptionHandler())
            .build();

        playerService.register(STEAM_ID, "SirPacster");
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private long insertSkin(String key, String weapon, String name, String textures, boolean enabled) {
        db.jdbc().sql("""
            INSERT INTO skins (skin_key, weapon_type, display_name, textures, materials, enabled, created_at)
            VALUES (:key, :weapon, :name, :textures, '[]', :enabled, :now)
            """)
            .param("key", key).param("weapon", weapon).param("name", name)
            .param("textures", textures).param("enabled", enabled).param("now", LocalDateTime.now())
            .update();
        return db.jdbc().sql("SELECT id FROM skins WHERE skin_key = :key").param("key", key).query(Long.class).single();
    }

    @Test
    void catalogListsOnlyEnabledSkinsWithParsedArrays() throws Exception {
        mvc.perform(post("/api/dayz/skins/catalog/query").contentType(MediaType.APPLICATION_JSON).content("{" + CREDS + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.skins.length()").value(2))
            .andExpect(jsonPath("$.skins[0].skinKey").value("AK_Test"))
            .andExpect(jsonPath("$.skins[0].textures[1]").value("b.paa"));
    }

    @Test
    void grantIsIdempotentAndOwnedReflectsIt() throws Exception {
        String body = "{" + CREDS + ",\"steamId\":\"" + STEAM_ID + "\",\"skinKey\":\"AK_Test\",\"source\":\"admin\"}";
        mvc.perform(post("/api/dayz/skins/grant").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.granted").value(true));
        mvc.perform(post("/api/dayz/skins/grant").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.granted").value(false));

        assertEquals(1, db.jdbc().sql("SELECT COUNT(*) FROM player_skins").query(Integer.class).single());
        mvc.perform(post("/api/dayz/skins/owned/query").contentType(MediaType.APPLICATION_JSON)
                .content("{" + CREDS + ",\"steamId\":\"" + STEAM_ID + "\"}"))
            .andExpect(jsonPath("$.skinIds[0]").value(akSkinId))
            .andExpect(jsonPath("$.equipped.length()").value(0));
    }

    @Test
    void grantErrors() throws Exception {
        mvc.perform(post("/api/dayz/skins/grant").contentType(MediaType.APPLICATION_JSON)
                .content("{" + CREDS + ",\"steamId\":\"" + STEAM_ID + "\",\"skinKey\":\"Nope\",\"source\":\"admin\"}"))
            .andExpect(status().isNotFound()).andExpect(content().string("skin_not_found"));
        mvc.perform(post("/api/dayz/skins/grant").contentType(MediaType.APPLICATION_JSON)
                .content("{" + CREDS + ",\"steamId\":\"76561198999999999\",\"skinKey\":\"AK_Test\",\"source\":\"admin\"}"))
            .andExpect(status().isNotFound()).andExpect(content().string("player_not_found"));
        mvc.perform(post("/api/dayz/skins/grant").contentType(MediaType.APPLICATION_JSON)
                .content("{" + CREDS + ",\"steamId\":\"" + STEAM_ID + "\",\"skinKey\":\"AK_Test\",\"source\":\"hack\"}"))
            .andExpect(status().isBadRequest()).andExpect(content().string("invalid_source"));
    }

    @Test
    void equipRequiresOwnershipAndMatchingWeapon() {
        DayzApiException notOwned = assertThrows(DayzApiException.class, () -> skinService.equip(STEAM_ID, "AKM", akSkinId));
        assertEquals(HttpStatus.FORBIDDEN, notOwned.getStatus());

        skinService.grant(STEAM_ID, "AK_Test", "admin");
        skinService.grant(STEAM_ID, "M4_Test", "admin");

        DayzApiException wrongWeapon = assertThrows(DayzApiException.class, () -> skinService.equip(STEAM_ID, "AKM", m4SkinId));
        assertEquals("skin_wrong_weapon", wrongWeapon.getMessage());

        skinService.equip(STEAM_ID, "AKM", akSkinId);
        assertEquals(akSkinId, skinService.owned(STEAM_ID).equipped().get(0).skinId());

        skinService.equip(STEAM_ID, "AKM", 0);
        assertTrue(skinService.owned(STEAM_ID).equipped().isEmpty());
    }

    @Test
    void disabledSkinsCannotBeGrantedOrEquipped() {
        DayzApiException ex = assertThrows(DayzApiException.class, () -> skinService.grant(STEAM_ID, "AK_Hidden", "admin"));
        assertEquals("skin_not_found", ex.getMessage());
    }

    @Test
    void xpBatchAddsAtomicallyAcrossCalls() throws Exception {
        String body = "{" + CREDS + ",\"entries\":[{\"steamId\":\"" + STEAM_ID + "\",\"deltas\":{\"AR\":100,\"SMG\":5}}]}";
        mvc.perform(post("/api/dayz/skills/xp/batch").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(1));
        mvc.perform(post("/api/dayz/skills/xp/batch").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());

        mvc.perform(post("/api/dayz/skills/query").contentType(MediaType.APPLICATION_JSON)
                .content("{" + CREDS + ",\"steamId\":\"" + STEAM_ID + "\"}"))
            .andExpect(jsonPath("$.xp[0].category").value("AR"))
            .andExpect(jsonPath("$.xp[0].xp").value(200))
            .andExpect(jsonPath("$.xp[1].xp").value(10));
    }

    @Test
    void xpBatchValidationAndUnknownPlayers() throws Exception {
        mvc.perform(post("/api/dayz/skills/xp/batch").contentType(MediaType.APPLICATION_JSON)
                .content("{" + CREDS + ",\"entries\":[{\"steamId\":\"" + STEAM_ID + "\",\"deltas\":{\"AR\":-5}}]}"))
            .andExpect(status().isBadRequest()).andExpect(content().string("invalid_xp_delta"));
        mvc.perform(post("/api/dayz/skills/xp/batch").contentType(MediaType.APPLICATION_JSON)
                .content("{" + CREDS + ",\"entries\":[{\"steamId\":\"" + STEAM_ID + "\",\"deltas\":{\"bad name\":5}}]}"))
            .andExpect(status().isBadRequest()).andExpect(content().string("invalid_category"));
        mvc.perform(post("/api/dayz/skills/xp/batch").contentType(MediaType.APPLICATION_JSON)
                .content("{" + CREDS + ",\"entries\":[{\"steamId\":\"76561198999999999\",\"deltas\":{\"AR\":5}}]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.skipped").value(1));
    }

    @Test
    void badCredentialsAreRejected() throws Exception {
        mvc.perform(post("/api/dayz/skins/catalog/query").contentType(MediaType.APPLICATION_JSON)
                .content("{\"serverId\":\"overkill-test\",\"apiKey\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());
        assertFalse(skinService.catalog().skins().isEmpty());
    }
}
