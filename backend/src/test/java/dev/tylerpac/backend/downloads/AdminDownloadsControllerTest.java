package dev.tylerpac.backend.downloads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.tylerpac.backend.dto.ShopProductResponse;
import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.repo.UserRepository;
import dev.tylerpac.backend.security.AdminAccess;
import dev.tylerpac.backend.security.SecurityConfig;
import dev.tylerpac.backend.security.SharedLoginUserService;
import dev.tylerpac.backend.service.StripeShopService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// The real security chain and admin check in front of the Downloads section; the download service is mocked.
@WebMvcTest(controllers = {AdminDownloadsController.class, ProductLinksController.class})
@Import({SecurityConfig.class, SharedLoginUserService.class})
@TestPropertySource(properties = {
    "app.login.issuer=https://auth.test",
    "app.login.internal-url=http://login.internal",
    "app.login.client-secret=test-value",
    "app.auth.frontend-base-url=https://site.test"
})
class AdminDownloadsControllerTest {

    private static final String ADMIN = "76561199000000001";
    private static final String PLAYER = "76561198012345678";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired private MockMvc mvc;
    @MockitoBean private UserRepository users;
    @MockitoBean private AdminAccess adminAccess;
    @MockitoBean private DownloadServiceClient downloads;
    @MockitoBean private ProductLinkRepository links;
    @MockitoBean private StripeShopService shop;
    @MockitoBean private DownloadGroupRepository groups;

    @BeforeEach
    void accounts() {
        when(users.findByUsername(ADMIN)).thenReturn(Optional.of(User.fromSteamAccount(ADMIN)));
        when(users.findByUsername(PLAYER)).thenReturn(Optional.of(User.fromSteamAccount(PLAYER)));
        doThrow(new IllegalArgumentException("forbidden")).when(adminAccess).requireAdmin(PLAYER);
        when(shop.getProducts()).thenReturn(List.of(new ShopProductResponse("keycard-crates", "KeyCard Crates", "", 40000, "usd")));
        when(downloads.isConfigured()).thenReturn(true);
    }

    private static JsonNode json(String value) {
        return JSON.readTree(value);
    }

    @Test
    void signedOutVisitorsGet401() throws Exception {
        mvc.perform(get("/shop/admin/downloads")).andExpect(status().isUnauthorized());
        verifyNoInteractions(downloads);
    }

    @Test
    void playersWhoAreNotAdminsGet403() throws Exception {
        mvc.perform(get("/shop/admin/downloads").with(user(PLAYER)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("forbidden"));
        mvc.perform(post("/shop/admin/downloads/files/maplink/uploads").with(user(PLAYER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"fileName\":\"a.zip\",\"sizeBytes\":10}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(downloads);
    }

    @Test
    void changesNeedTheCsrfToken() throws Exception {
        mvc.perform(post("/shop/admin/downloads/files/maplink/uploads").with(user(ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content("{\"fileName\":\"a.zip\",\"sizeBytes\":10}"))
            .andExpect(status().isForbidden());
        verify(downloads, never()).startUpload(any(), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void anUploadIsRecordedAgainstTheAdminWhoStartedIt() throws Exception {
        when(downloads.startUpload(eq("maplink"), anyMap())).thenReturn(json(
            "{\"uploadId\":\"abc\",\"uploadToken\":\"t\",\"uploadUrl\":\"https://downloads.test/upload/abc\",\"chunkSizeBytes\":16,\"chunkCount\":1}"));

        mvc.perform(post("/shop/admin/downloads/files/maplink/uploads").with(user(ADMIN)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fileName\":\"maplink.zip\",\"sizeBytes\":10,\"uploadedBy\":\"someone-else\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uploadUrl").value("https://downloads.test/upload/abc"));

        ArgumentCaptor<Map<String, Object>> request = ArgumentCaptor.forClass(Map.class);
        verify(downloads).startUpload(eq("maplink"), request.capture());
        assertEquals(ADMIN, request.getValue().get("uploadedBy"));
        assertEquals("maplink.zip", request.getValue().get("fileName"));
    }

    @Test
    void aFileGetsAClientAndAServerPart() throws Exception {
        when(downloads.saveArtifact(any(), any(), any())).thenReturn(json("{}"));
        // A file uploaded to the client part before parts held several files and had a folder
        when(downloads.artifacts()).thenReturn(json("[{\"key\":\"core-client\",\"displayName\":\"core (Client)\","
            + "\"versions\":[{\"fileName\":\"DankAmmo.pbo\"}]},{\"key\":\"maplink-client\",\"versions\":[{\"fileName\":\"x.pbo\"}]}]"));
        mvc.perform(put("/shop/admin/downloads/files/core").with(user(ADMIN)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"OverKill Dev Core\",\"parts\":[\"client\",\"server\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.modFolder").value("@OverKillDevCore"));
        verify(downloads).saveArtifact("core-client", "OverKill Dev Core (Client)", null);
        verify(downloads).saveArtifact("core-server", "OverKill Dev Core (Server)", null);
        ArgumentCaptor<DownloadGroup> group = ArgumentCaptor.forClass(DownloadGroup.class);
        verify(groups).save(group.capture());
        assertEquals("@OverKillDevCore", group.getValue().getModFolder());
        // Setting up the group places that older file in the mod layout; other groups are untouched
        verify(downloads).saveArtifact("core-client", "core (Client)", "@OverKillDevCore/addons");
        verify(downloads, never()).saveArtifact(eq("maplink-client"), any(), any());
    }

    @Test
    void badFileKeysAndPartsAreRefused() throws Exception {
        for (String body : List.of("{\"displayName\":\"x\",\"parts\":[]}", "{\"displayName\":\"x\",\"parts\":[\"workshop\"]}")) {
            mvc.perform(put("/shop/admin/downloads/files/core").with(user(ADMIN)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_parts"));
        }
        // A base key ending in a part name would clash with another file's part
        mvc.perform(put("/shop/admin/downloads/files/core-server").with(user(ADMIN)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"x\",\"parts\":[\"client\"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_key"));
        mvc.perform(put("/shop/admin/downloads/files/Not_Valid").with(user(ADMIN)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"x\",\"parts\":[\"client\"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_key"));
        verify(downloads, never()).saveArtifact(any(), any(), any());
    }

    @Test
    void eachUploadedFileInAPartGoesInTheModLayout() throws Exception {
        when(groups.findById("core")).thenReturn(Optional.of(new DownloadGroup("core", "@OverKillDevCore")));
        mvc.perform(post("/shop/admin/downloads/files/core/client/entries").with(user(ADMIN)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"fileName\":\"DankAmmo.pbo\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key").value("core-client--dankammo-pbo"));
        verify(downloads).saveArtifact("core-client--dankammo-pbo", "DankAmmo.pbo", "@OverKillDevCore/addons");

        mvc.perform(post("/shop/admin/downloads/files/core/server/entries").with(user(ADMIN)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"fileName\":\"OverKill.bikey\"}"))
            .andExpect(status().isOk());
        verify(downloads).saveArtifact("core-server--overkill-bikey", "OverKill.bikey", "@OverKillDevCore/keys");

        mvc.perform(post("/shop/admin/downloads/files/core/client/entries").with(user(ADMIN)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"fileName\":\"../evil.pbo\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_file_name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void finishedUploadsAreAddedToEveryProductThatIncludesTheGroup() throws Exception {
        // core-client has its old single file plus two uploaded files; one upload has not finished yet
        when(downloads.artifacts()).thenReturn(json("[{\"key\":\"core-client\",\"currentVersionId\":1},"
            + "{\"key\":\"core-client--zeta-pbo\",\"currentVersionId\":3},{\"key\":\"core-client--alpha-pbo\",\"currentVersionId\":2},"
            + "{\"key\":\"core-client--pending-pbo\",\"currentVersionId\":null},{\"key\":\"keycard-client\",\"currentVersionId\":4}]"));
        when(downloads.bundles()).thenReturn(json("[{\"key\":\"keycard-crates-client\",\"displayName\":\"KeyCard Crates (Client)\","
            + "\"fileName\":\"KeycardCrates-Client.zip\",\"artifacts\":[\"keycard-client\",\"core-client\"]},"
            + "{\"key\":\"weapon-system-client\",\"artifacts\":[\"weapons-client\"]}]"));
        when(downloads.saveBundle(any(), anyMap())).thenReturn(json("{}"));

        mvc.perform(post("/shop/admin/downloads/files/core/refresh").with(user(ADMIN)).with(csrf()))
            .andExpect(status().isOk());

        ArgumentCaptor<Map<String, Object>> bundle = ArgumentCaptor.forClass(Map.class);
        verify(downloads).saveBundle(eq("keycard-crates-client"), bundle.capture());
        assertEquals(List.of("keycard-client", "core-client", "core-client--alpha-pbo", "core-client--zeta-pbo"),
            bundle.getValue().get("artifacts"));
        assertEquals("KeycardCrates-Client.zip", bundle.getValue().get("fileName"));
        // A product without this group is left alone
        verify(downloads, never()).saveBundle(eq("weapon-system-client"), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void deletingAFileTakesItOutOfProductsFirst() throws Exception {
        when(downloads.artifacts()).thenReturn(json("[{\"key\":\"core-client--alpha-pbo\",\"currentVersionId\":2},"
            + "{\"key\":\"core-client--zeta-pbo\",\"currentVersionId\":3}]"));
        when(downloads.bundles()).thenReturn(json("[{\"key\":\"keycard-crates-client\",\"fileName\":\"K-Client.zip\","
            + "\"artifacts\":[\"core-client--alpha-pbo\",\"core-client--zeta-pbo\"]}]"));
        when(downloads.saveBundle(any(), anyMap())).thenReturn(json("{}"));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/shop/admin/downloads/files/core-client--zeta-pbo")
                .with(user(ADMIN)).with(csrf()))
            .andExpect(status().isNoContent());

        ArgumentCaptor<Map<String, Object>> bundle = ArgumentCaptor.forClass(Map.class);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(downloads);
        order.verify(downloads).saveBundle(eq("keycard-crates-client"), bundle.capture());
        order.verify(downloads).deleteArtifact("core-client--zeta-pbo");
        assertEquals(List.of("core-client--alpha-pbo"), bundle.getValue().get("artifacts"));
    }

    @Test
    void downloadServiceErrorsReachThePage() throws Exception {
        when(downloads.artifacts()).thenReturn(json("[]"));
        when(downloads.bundles()).thenReturn(json("[]"));
        doThrow(new DownloadServiceException(409, "artifact_in_use")).when(downloads).deleteArtifact("core-client");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/shop/admin/downloads/files/core-client")
                .with(user(ADMIN)).with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("artifact_in_use"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aProductGetsAClientAndAServerDownloadFromItsFilesParts() throws Exception {
        // keycard and core have both parts; api is server-only; an old single download exists from before parts
        when(downloads.artifacts()).thenReturn(json("[{\"key\":\"keycard-client\",\"currentVersionId\":1},{\"key\":\"keycard-server\",\"currentVersionId\":2},"
            + "{\"key\":\"core-client\",\"currentVersionId\":3},{\"key\":\"core-server\",\"currentVersionId\":4},{\"key\":\"api-server\",\"currentVersionId\":5}]"));
        when(downloads.bundles()).thenReturn(json("[{\"key\":\"keycard-crates\"}]"));
        when(downloads.saveBundle(any(), anyMap())).thenReturn(json("{\"state\":\"BUILDING\"}"));

        mvc.perform(put("/shop/admin/downloads/products/keycard-crates").with(user(ADMIN)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"files\":[\"keycard\",\"core\",\"api\"],\"fileName\":\"KeycardCrates.zip\",\"githubUrl\":\"https://github.com/OverKill-Dayz/KeycardCrates\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bundles.length()").value(2));

        ArgumentCaptor<Map<String, Object>> client = ArgumentCaptor.forClass(Map.class);
        verify(downloads).saveBundle(eq("keycard-crates-client"), client.capture());
        assertEquals(List.of("keycard-client", "core-client"), client.getValue().get("artifacts"));
        assertEquals("KeycardCrates-Client.zip", client.getValue().get("fileName"));
        assertEquals("KeyCard Crates (Client)", client.getValue().get("displayName"));

        ArgumentCaptor<Map<String, Object>> server = ArgumentCaptor.forClass(Map.class);
        verify(downloads).saveBundle(eq("keycard-crates-server"), server.capture());
        assertEquals(List.of("keycard-server", "core-server", "api-server"), server.getValue().get("artifacts"));
        assertEquals("KeycardCrates-Server.zip", server.getValue().get("fileName"));

        // The single download from before parts is removed
        verify(downloads).deleteBundle("keycard-crates");
        ArgumentCaptor<ProductLink> saved = ArgumentCaptor.forClass(ProductLink.class);
        verify(links).save(saved.capture());
        assertEquals("https://github.com/OverKill-Dayz/KeycardCrates", saved.getValue().getGithubUrl());
    }

    @Test
    void aProductWhoseFilesHaveNoPartsYetIsRefused() throws Exception {
        when(downloads.artifacts()).thenReturn(json("[{\"key\":\"core\",\"currentVersionId\":1},{\"key\":\"core-client\",\"currentVersionId\":null}]"));
        when(downloads.bundles()).thenReturn(json("[]"));
        mvc.perform(put("/shop/admin/downloads/products/keycard-crates").with(user(ADMIN)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"files\":[\"core\"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("no_parts"));
        verify(downloads, never()).saveBundle(any(), anyMap());
    }

    @Test
    void unknownProductsAndNonGithubLinksAreRefused() throws Exception {
        mvc.perform(put("/shop/admin/downloads/products/not-a-product").with(user(ADMIN)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"files\":[\"core\"]}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("product_not_found"));
        mvc.perform(put("/shop/admin/downloads/products/keycard-crates").with(user(ADMIN)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"githubUrl\":\"https://evil.example/x\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_github_url"));
        verify(downloads, never()).saveBundle(any(), anyMap());
    }

    @Test
    void theSectionStillLoadsWhenTheDownloadServiceIsDown() throws Exception {
        when(downloads.artifacts()).thenThrow(new DownloadServiceException(503, "downloads_unavailable"));
        mvc.perform(get("/shop/admin/downloads").with(user(ADMIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.error").value("downloads_unavailable"))
            .andExpect(jsonPath("$.products[0].id").value("keycard-crates"));
    }

    @Test
    void productGithubLinksArePublic() throws Exception {
        when(links.findAll()).thenReturn(List.of(new ProductLink("keycard-crates", "https://github.com/OverKill-Dayz/KeycardCrates")));
        mvc.perform(get("/shop/product-links"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$['keycard-crates']").value("https://github.com/OverKill-Dayz/KeycardCrates"));
    }
}
