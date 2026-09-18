package dev.tylerpac.backend.dayz.repo;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.tylerpac.backend.dayz.config.DayzDatabase;

@Repository
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class ModDataRepository {

    private final JdbcClient jdbc;

    public ModDataRepository(DayzDatabase db) {
        this.jdbc = db.jdbc();
    }

    /**
     * One statement: resolves the player by steam_id and inserts/updates the (player, mod) row.
     * Returns the affected row count; 0 means the player does not exist (or nothing changed).
     */
    public int upsert(long steamId, String modName, String json, LocalDateTime now) {
        return jdbc.sql("""
            INSERT INTO player_mod_data (player_id, mod_name, data, created_at, updated_at)
            SELECT p.id, :modName, :data, :now, :now FROM players p WHERE p.steam_id = :steamId
            ON DUPLICATE KEY UPDATE data = :data, updated_at = :now
            """)
            .param("steamId", steamId)
            .param("modName", modName)
            .param("data", json)
            .param("now", now)
            .update();
    }

    public Optional<String> findData(long steamId, String modName) {
        return jdbc.sql("""
            SELECT d.data FROM player_mod_data d
            JOIN players p ON p.id = d.player_id
            WHERE p.steam_id = :steamId AND d.mod_name = :modName
            """)
            .param("steamId", steamId)
            .param("modName", modName)
            .query(String.class)
            .optional();
    }
}
