package dev.tylerpac.backend.dayz.repo;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.tylerpac.backend.dayz.config.DayzDatabase;

@Repository
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class SkillXpRepository {

    private final JdbcClient jdbc;

    public SkillXpRepository(DayzDatabase db) {
        this.jdbc = db.jdbc();
    }

    /** Atomic increment, so several servers can add XP for the same player without overwriting each other. */
    public int addXp(long steamId, String category, long delta, LocalDateTime now) {
        return jdbc.sql("""
            INSERT INTO player_skill_xp (player_id, category, xp, updated_at)
            SELECT p.id, :category, :delta, :now FROM players p WHERE p.steam_id = :steamId
            ON DUPLICATE KEY UPDATE xp = xp + :delta, updated_at = :now
            """)
            .param("steamId", steamId)
            .param("category", category)
            .param("delta", delta)
            .param("now", now)
            .update();
    }

    public Map<String, Long> findXp(long steamId) {
        Map<String, Long> xp = new LinkedHashMap<>();
        jdbc.sql("""
            SELECT x.category, x.xp FROM player_skill_xp x
            JOIN players p ON p.id = x.player_id
            WHERE p.steam_id = :steamId
            ORDER BY x.category
            """)
            .param("steamId", steamId)
            .query((rs, rowNum) -> {
                xp.put(rs.getString("category"), rs.getLong("xp"));
                return rowNum;
            })
            .list();
        return xp;
    }
}
