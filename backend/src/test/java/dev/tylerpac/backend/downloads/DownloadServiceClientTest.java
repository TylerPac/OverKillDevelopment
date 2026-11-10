package dev.tylerpac.backend.downloads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

// Against a real local HTTP server standing in for the download service.
class DownloadServiceClientTest {

    private HttpServer server;
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();

    private String serve(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> {
            apiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            path.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsTheApplicationKeyAndReturnsTheBody() throws Exception {
        DownloadServiceClient client = new DownloadServiceClient(serve(200, "{\"url\":\"https://downloads.test/d/x\"}"), "app-key");
        assertEquals("https://downloads.test/d/x", client.bundleLink("keycard-crates").path("url").asString());
        assertEquals("app-key", apiKey.get());
        assertEquals("POST /api/bundles/keycard-crates/link", path.get());
    }

    @Test
    void theServicesErrorCodeIsKept() throws Exception {
        DownloadServiceClient client = new DownloadServiceClient(serve(409, "{\"error\":\"bundle_not_ready\"}"), "app-key");
        DownloadServiceException error = assertThrows(DownloadServiceException.class, () -> client.bundleLink("keycard-crates"));
        assertEquals(409, error.status());
        assertEquals("bundle_not_ready", error.code());
    }

    @Test
    void anUnreachableOrUnconfiguredServiceIsReportedAsSuch() {
        DownloadServiceException down = assertThrows(DownloadServiceException.class,
            () -> new DownloadServiceClient("http://127.0.0.1:1", "app-key").artifacts());
        assertEquals("downloads_unavailable", down.code());
        DownloadServiceException off = assertThrows(DownloadServiceException.class,
            () -> new DownloadServiceClient("", "").artifacts());
        assertEquals("downloads_not_configured", off.code());
    }
}
