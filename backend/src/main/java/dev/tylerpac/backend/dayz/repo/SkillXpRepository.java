package dev.tylerpac.backend.dayz.repo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.tylerpac.backend.dayz.config.DayzDatabase;

@Repository
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class SkillXpRepository {

    private static final String UPSERT = """
        INSERT INTO player_skill_xp (player_id, category, xp, updated_at)
        SELECT p.id, ?, ?, ? FROM players p WHERE p.steam_id = ?
        ON DUPLICATE KEY UPDATE xp = xp + ?, updated_at = ?
        """;

    public record XpDelta(long steamId, String category, long delta) {
    }

    private final JdbcClient jdbc;
    private final JdbcTemplate template;

    public SkillXpRepository(DayzDatabase db) {
        this.jdbc = db.jdbc();
        this.template = db.template();
    }

    /**
     * Atomic increments (xp = xp + delta), so several servers can add XP for the same player without overwriting
     * each other. The whole flush is one JDBC batch, i.e. one round trip. Deltas for unknown players add nothing.
     */
    public void addXpBatch(List<XpDelta> deltas, LocalDateTime now) {
        if (deltas.isEmpty()) {
            return;
        }
        List<Object[]> args = new ArrayList<>(deltas.size());
        for (XpDelta d : deltas) {
            args.add(new Object[] {d.category(), d.delta(), now, d.steamId(), d.delta(), now});
        }
        template.batchUpdate(UPSERT, args);
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
