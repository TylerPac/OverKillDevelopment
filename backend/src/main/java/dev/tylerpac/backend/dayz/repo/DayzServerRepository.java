package dev.tylerpac.backend.dayz.repo;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.tylerpac.backend.dayz.config.DayzDatabase;
import dev.tylerpac.backend.dayz.model.DayzServer;

@Repository
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class DayzServerRepository {

    private final JdbcClient jdbc;

    public DayzServerRepository(DayzDatabase db) {
        this.jdbc = db.jdbc();
    }

    public Optional<DayzServer> findByServerId(String serverId) {
        return jdbc.sql("SELECT server_id, api_key_hash, enabled FROM dayz_servers WHERE server_id = :serverId")
            .param("serverId", serverId)
            .query((rs, rowNum) -> new DayzServer(
                rs.getString("server_id"),
                rs.getString("api_key_hash"),
                rs.getBoolean("enabled")))
            .optional();
    }

    public void insert(String serverId, String apiKeyHash, LocalDateTime now) {
        jdbc.sql("""
            INSERT INTO dayz_servers (server_id, api_key_hash, enabled, created_at)
            VALUES (:serverId, :apiKeyHash, TRUE, :now)
            """)
            .param("serverId", serverId)
            .param("apiKeyHash", apiKeyHash)
            .param("now", now)
            .update();
    }
}
