package dev.tylerpac.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import dev.tylerpac.backend.security.LoginRolesClient.LoginUnavailableException;

// Against a real local HTTP server standing in for the login service.
class LoginRolesClientTest {

    private HttpServer server;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> query = new AtomicReference<>();

    private String serve(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/roles", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            query.set(exchange.getRequestURI().getQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsOverkillsClientCredentialsAndReadsTheAdminRole() throws Exception {
        LoginRolesClient client = new LoginRolesClient(serve(200, "{\"roles\":[\"admin\"]}"), "s3cret");

        assertTrue(client.isAdmin("76561199000000001"));
        assertEquals("steamId=76561199000000001", query.get());
        assertEquals("Basic " + Base64.getEncoder().encodeToString("overkill:s3cret".getBytes(StandardCharsets.UTF_8)), authorization.get());
    }

    @Test
    void noAdminRoleMeansNotAnAdmin() throws Exception {
        assertFalse(new LoginRolesClient(serve(200, "{\"roles\":[]}"), "s3cret").isAdmin("76561199000000001"));
    }

    @Test
    void rejectedCredentialsCountAsUnavailableNotAsNo() throws Exception {
        LoginRolesClient rejected = new LoginRolesClient(serve(401, "{\"error\":\"invalid_client\"}"), "wrong");
        assertThrows(LoginUnavailableException.class, () -> rejected.isAdmin("76561199000000001"));
    }

    @Test
    void anUnreachableOrUnconfiguredLoginServiceIsUnavailable() {
        assertThrows(LoginUnavailableException.class, () -> new LoginRolesClient("http://127.0.0.1:1", "s3cret").isAdmin("76561199000000001"));
        assertThrows(LoginUnavailableException.class, () -> new LoginRolesClient("http://127.0.0.1:1", "").isAdmin("76561199000000001"));
    }
}
