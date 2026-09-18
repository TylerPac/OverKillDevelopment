package dev.tylerpac.backend.dayz.repo;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import dev.tylerpac.backend.dayz.config.DayzDatabase;
import dev.tylerpac.backend.dayz.model.Player;

@Repository
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class PlayerRepository {

    private final JdbcClient jdbc;

    public PlayerRepository(DayzDatabase db) {
        this.jdbc = db.jdbc();
    }

    /** Single round trip: inserts the player, or refreshes name + last_seen. first_seen is never touched on update. */
    public void upsert(long steamId, String playerName, LocalDateTime now) {
        jdbc.sql("""
            INSERT INTO players (steam_id, player_name, first_seen, last_seen)
            VALUES (:steamId, :playerName, :now, :now)
            ON DUPLICATE KEY UPDATE player_name = :playerName, last_seen = :now
            """)
            .param("steamId", steamId)
            .param("playerName", playerName)
            .param("now", now)
            .update();
    }

    public Optional<Player> findBySteamId(long steamId) {
        return jdbc.sql("SELECT steam_id, player_name, first_seen, last_seen FROM players WHERE steam_id = :steamId")
            .param("steamId", steamId)
            .query((rs, rowNum) -> new Player(
                rs.getLong("steam_id"),
                rs.getString("player_name"),
                rs.getObject("first_seen", LocalDateTime.class),
                rs.getObject("last_seen", LocalDateTime.class)))
            .optional();
    }
}
