package dev.tylerpac.backend.dayz.repo;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.tylerpac.backend.dayz.config.DayzDatabase;

@Repository
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class PlayerSkinRepository {

    private final JdbcClient jdbc;

    public PlayerSkinRepository(DayzDatabase db) {
        this.jdbc = db.jdbc();
    }

    /** Idempotent: 1 = newly granted, 0 = already owned or the player does not exist. */
    public int grant(long steamId, long skinId, String source, LocalDateTime now) {
        return jdbc.sql("""
            INSERT INTO player_skins (player_id, skin_id, source, granted_at)
            SELECT p.id, :skinId, :source, :now FROM players p WHERE p.steam_id = :steamId
            ON DUPLICATE KEY UPDATE skin_id = skin_id
            """)
            .param("steamId", steamId)
            .param("skinId", skinId)
            .param("source", source)
            .param("now", now)
            .update();
    }

    public List<Long> findOwnedSkinIds(long steamId) {
        return jdbc.sql("""
            SELECT ps.skin_id FROM player_skins ps
            JOIN players p ON p.id = ps.player_id
            WHERE p.steam_id = :steamId
            ORDER BY ps.skin_id
            """)
            .param("steamId", steamId)
            .query(Long.class)
            .list();
    }

    public boolean owns(long steamId, long skinId) {
        return jdbc.sql("""
            SELECT COUNT(*) FROM player_skins ps
            JOIN players p ON p.id = ps.player_id
            WHERE p.steam_id = :steamId AND ps.skin_id = :skinId
            """)
            .param("steamId", steamId)
            .param("skinId", skinId)
            .query(Long.class)
            .single() > 0;
    }

    /** weaponType -> equipped skin id. */
    public Map<String, Long> findEquipped(long steamId) {
        Map<String, Long> equipped = new LinkedHashMap<>();
        jdbc.sql("""
            SELECT e.weapon_type, e.skin_id FROM player_skin_equipped e
            JOIN players p ON p.id = e.player_id
            WHERE p.steam_id = :steamId
            ORDER BY e.weapon_type
            """)
            .param("steamId", steamId)
            .query((rs, rowNum) -> {
                equipped.put(rs.getString("weapon_type"), rs.getLong("skin_id"));
                return rowNum;
            })
            .list();
        return equipped;
    }

    public int setEquipped(long steamId, String weaponType, long skinId) {
        return jdbc.sql("""
            INSERT INTO player_skin_equipped (player_id, weapon_type, skin_id)
            SELECT p.id, :weaponType, :skinId FROM players p WHERE p.steam_id = :steamId
            ON DUPLICATE KEY UPDATE skin_id = :skinId
            """)
            .param("steamId", steamId)
            .param("weaponType", weaponType)
            .param("skinId", skinId)
            .update();
    }

    public int clearEquipped(long steamId, String weaponType) {
        return jdbc.sql("""
            DELETE FROM player_skin_equipped
            WHERE weapon_type = :weaponType AND player_id = (SELECT id FROM players WHERE steam_id = :steamId)
            """)
            .param("steamId", steamId)
            .param("weaponType", weaponType)
            .update();
    }
}
