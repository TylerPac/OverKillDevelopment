package dev.tylerpac.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import dev.tylerpac.backend.downloads.DownloadServiceClient;
import dev.tylerpac.backend.downloads.DownloadServiceException;
import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.repo.DownloadTokenRepository;
import dev.tylerpac.backend.repo.ShopOrderRepository;
import tools.jackson.databind.json.JsonMapper;

// What a buyer's Download button gets: the product's bundle from the download service when it is set up,
// otherwise the old per-product link setting.
class ShopDownloadLinkTest {

    private final ShopOrderRepository orders = mock(ShopOrderRepository.class);
    private final DownloadServiceClient downloads = mock(DownloadServiceClient.class);
    private final MockEnvironment env = new MockEnvironment();
    private final ShopDownloadService service = new ShopDownloadService(orders, mock(DownloadTokenRepository.class), "downloads", env, downloads);
    private final User buyer = User.fromSteamAccount("76561199000000001");

    @BeforeEach
    void bought() {
        when(orders.existsByUserAndProductIdAndStatusIgnoreCase(eq(buyer), eq("keycard-crates"), any())).thenReturn(true);
        when(downloads.isConfigured()).thenReturn(true);
    }

    @Test
    void buyersGetASignedLinkToTheProductsBundle() {
        when(downloads.bundleLink("keycard-crates")).thenReturn(JsonMapper.builder().build().readTree("{\"url\":\"https://downloads.test/d/abc\"}"));
        assertEquals(Optional.of("https://downloads.test/d/abc"), service.findDownloadLink(buyer, "keycard-crates"));
    }

    @Test
    void aProductNotSetUpYetFallsBackToItsConfiguredLink() {
        env.setProperty("app.shop.download-link.keycard-crates", "https://example.test/old");
        when(downloads.bundleLink("keycard-crates")).thenThrow(new DownloadServiceException(404, "bundle_not_found"));
        assertEquals(Optional.of("https://example.test/old"), service.findDownloadLink(buyer, "keycard-crates"));
        org.mockito.Mockito.doThrow(new DownloadServiceException(409, "bundle_not_ready")).when(downloads).bundleLink("keycard-crates");
        assertEquals(Optional.of("https://example.test/old"), service.findDownloadLink(buyer, "keycard-crates"));
    }

    @Test
    void anOutageIsReportedRatherThanHidden() {
        when(downloads.bundleLink("keycard-crates")).thenThrow(new DownloadServiceException(503, "downloads_unavailable"));
        assertThrows(IllegalStateException.class, () -> service.findDownloadLink(buyer, "keycard-crates"));
    }

    @Test
    void buyersSeeWhichPartsAreReadyAndGetALinkToEach() {
        JsonMapper json = JsonMapper.builder().build();
        when(downloads.bundles()).thenReturn(json.readTree("[{\"key\":\"keycard-crates-client\",\"current\":{\"id\":1}},"
            + "{\"key\":\"keycard-crates-server\",\"current\":null},{\"key\":\"weapon-system-server\",\"current\":{\"id\":2}}]"));
        assertEquals(java.util.Map.of("client", true, "server", false), service.downloadOptions(buyer, "keycard-crates"));

        when(downloads.bundleLink("keycard-crates-server")).thenReturn(json.readTree("{\"url\":\"https://downloads.test/d/server\"}"));
        assertEquals("https://downloads.test/d/server", service.findPartDownloadLink(buyer, "keycard-crates", "server"));
        assertThrows(IllegalArgumentException.class, () -> service.findPartDownloadLink(buyer, "keycard-crates", "workshop"));
    }

    @Test
    void aPartStillBuildingIsReportedAsNotReady() {
        when(downloads.bundleLink("keycard-crates-client")).thenThrow(new DownloadServiceException(409, "bundle_not_ready"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.findPartDownloadLink(buyer, "keycard-crates", "client"));
        assertEquals("download_not_ready", error.getMessage());
    }

    @Test
    void peopleWhoHaveNotBoughtItGetNoLink() {
        assertThrows(IllegalArgumentException.class, () -> service.findDownloadLink(buyer, "weapon-system"));
        assertThrows(IllegalArgumentException.class, () -> service.downloadOptions(buyer, "weapon-system"));
        assertThrows(IllegalArgumentException.class, () -> service.findPartDownloadLink(buyer, "weapon-system", "server"));
        verify(downloads, never()).bundleLink(any());
    }
}
