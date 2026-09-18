package dev.tylerpac.backend.dayz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import dev.tylerpac.backend.dayz.controller.DayzModDataController;
import dev.tylerpac.backend.dayz.controller.DayzPlayerController;
import dev.tylerpac.backend.dayz.model.Player;
import dev.tylerpac.backend.dayz.repo.DayzServerRepository;
import dev.tylerpac.backend.dayz.repo.ModDataRepository;
import dev.tylerpac.backend.dayz.repo.PlayerRepository;
import dev.tylerpac.backend.dayz.service.DayzApiException;
import dev.tylerpac.backend.dayz.service.DayzModDataService;
import dev.tylerpac.backend.dayz.service.DayzPlayerService;
import dev.tylerpac.backend.dayz.service.DayzServerAuthService;

class DayzApiTest {

    private static final String STEAM_ID = "76561198012345678";

    private DayzDatabase db;
    private DayzServerRepository serverRepository;
    private PlayerRepository playerRepository;
    private DayzPlayerService playerService;
    private DayzModDataService modDataService;
    private DayzServerAuthService authService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        db = DayzTestSupport.newDatabase();
        serverRepository = new DayzServerRepository(db);
        playerRepository = new PlayerRepository(db);
        ModDataRepository modDataRepository = new ModDataRepository(db);
        playerService = new DayzPlayerService(playerRepository);
        modDataService = new DayzModDataService(modDataRepository, playerRepository, 1024);
        authService = new DayzServerAuthService(serverRepository);
        serverRepository.insert("overkill-test", DayzServerAuthService.sha256Hex("secret"), LocalDateTime.now());

        mvc = MockMvcBuilders
            .standaloneSetup(
                new DayzPlayerController(authService, playerService),
                new DayzModDataController(authService, modDataService))
            .setControllerAdvice(new DayzExceptionHandler())
            .build();
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void registerKeepsFirstSeenAndUpdatesNameAndLastSeen() throws Exception {
        Player first = playerService.register(STEAM_ID, "Old Name");
        Thread.sleep(5);
        Player second = playerService.register(STEAM_ID, "New Name");

        assertEquals(first.firstSeen(), second.firstSeen());
        assertEquals("New Name", second.playerName());
        assertTrue(second.lastSeen().isAfter(first.lastSeen()));
    }

    @Test
    void modDataRoundTripKeepsOneRowPerPlayerAndMod() throws Exception {
        playerService.register(STEAM_ID, "SirPacster");
        String body = """
            {"serverId":"overkill-test","apiKey":"secret","steamId":"%s","modName":"OverKillApiTest",
             "data":{"testString":"Hello from DayZ","testNumber":12345,"enabled":true}}
            """.formatted(STEAM_ID);

        mvc.perform(post("/api/dayz/mod-data").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
        mvc.perform(post("/api/dayz/mod-data").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());

        Integer rows = db.jdbc().sql("SELECT COUNT(*) FROM player_mod_data").query(Integer.class).single();
        assertEquals(1, rows);

        mvc.perform(get("/api/dayz/mod-data/OverKillApiTest/" + STEAM_ID)
                .param("serverId", "overkill-test").param("apiKey", "secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.testString").value("Hello from DayZ"))
            .andExpect(jsonPath("$.testNumber").value(12345))
            .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void saveForUnknownPlayerIs404() throws Exception {
        String body = """
            {"serverId":"overkill-test","apiKey":"secret","steamId":"%s","modName":"M","data":{"a":1}}
            """.formatted(STEAM_ID);
        mvc.perform(post("/api/dayz/mod-data").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNotFound())
            .andExpect(content().string("player_not_found"));
    }

    @Test
    void missingDataIs404() throws Exception {
        playerService.register(STEAM_ID, "SirPacster");
        mvc.perform(get("/api/dayz/mod-data/Nope/" + STEAM_ID)
                .param("serverId", "overkill-test").param("apiKey", "secret"))
            .andExpect(status().isNotFound())
            .andExpect(content().string("data_not_found"));
    }

    @Test
    void badCredentialsAre401() throws Exception {
        mvc.perform(get("/api/dayz/players/" + STEAM_ID)
                .param("serverId", "overkill-test").param("apiKey", "wrong"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dayz/players/" + STEAM_ID))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/dayz/players").contentType(MediaType.APPLICATION_JSON)
                .content("{\"serverId\":\"unknown\",\"apiKey\":\"secret\",\"steamId\":\"" + STEAM_ID + "\",\"playerName\":\"x\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void validationFailuresAre400() throws Exception {
        mvc.perform(post("/api/dayz/players").contentType(MediaType.APPLICATION_JSON)
                .content("{\"serverId\":\"overkill-test\",\"apiKey\":\"secret\",\"steamId\":\"123\",\"playerName\":\"x\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("invalid_steam_id"));
        mvc.perform(post("/api/dayz/mod-data").contentType(MediaType.APPLICATION_JSON)
                .content("{\"serverId\":\"overkill-test\",\"apiKey\":\"secret\",\"steamId\":\"" + STEAM_ID
                    + "\",\"modName\":\"bad name!\",\"data\":{}}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string("invalid_mod_name"));
    }

    @Test
    void oversizePayloadIs413() {
        playerService.register(STEAM_ID, "SirPacster");
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        tools.jackson.databind.node.ObjectNode data = mapper.createObjectNode();
        data.put("blob", "x".repeat(2048));

        DayzApiException ex = assertThrows(DayzApiException.class, () -> modDataService.save(STEAM_ID, "M", data));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.getStatus());
    }

    @Test
void disabledServerIsRejectedOnceCacheExpires() {
        long[] clock = {System.nanoTime()};
        DayzServerAuthService cached = new DayzServerAuthService(serverRepository) {
            @Override
            protected long nowNanos() {
                return clock[0];
            }
        };
        cached.verify("overkill-test", "secret");
        db.jdbc().sql("UPDATE dayz_servers SET enabled = FALSE").update();

        cached.verify("overkill-test", "secret");
        clock[0] += 61_000_000_000L;
        assertThrows(DayzApiException.class, () -> cached.verify("overkill-test", "secret"));
    }

    @Test
    void sha256HexIsStable() {
        assertNotEquals(DayzServerAuthService.sha256Hex("a"), DayzServerAuthService.sha256Hex("b"));
        assertEquals(64, DayzServerAuthService.sha256Hex("a").length());
    }
}
