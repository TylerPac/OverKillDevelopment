package dev.tylerpac.backend.dayz.config;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Holder for the dedicated DayZ database connection.
 *
 * The pool is deliberately NOT exposed as a {@link DataSource} bean: a second DataSource bean would make
 * Spring Boot back off the auto-configured primary DataSource/JPA/transaction manager used by the website.
 */
public final class DayzDatabase implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final JdbcClient jdbc;

    public DayzDatabase(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = JdbcClient.create(dataSource);
    }

    public JdbcClient jdbc() {
        return jdbc;
    }

    /** Idempotent schema creation. {@code jsonColumnType} is "JSON" on MySQL (tests pass a portable type). */
    public void initSchema(String jsonColumnType) {
        jdbc.sql("""
            CREATE TABLE IF NOT EXISTS players (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                steam_id BIGINT NOT NULL,
                player_name VARCHAR(128) NOT NULL,
                first_seen DATETIME(3) NOT NULL,
                last_seen DATETIME(3) NOT NULL,
                CONSTRAINT uk_players_steam_id UNIQUE (steam_id)
            )
            """).update();

        jdbc.sql("""
            CREATE TABLE IF NOT EXISTS player_mod_data (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                player_id BIGINT NOT NULL,
                mod_name VARCHAR(64) NOT NULL,
                data %s NOT NULL,
                created_at DATETIME(3) NOT NULL,
                updated_at DATETIME(3) NOT NULL,
                CONSTRAINT uk_player_mod_data_player_mod UNIQUE (player_id, mod_name),
                CONSTRAINT fk_player_mod_data_player FOREIGN KEY (player_id) REFERENCES players (id) ON DELETE CASCADE
            )
            """.formatted(jsonColumnType)).update();

        jdbc.sql("""
            CREATE TABLE IF NOT EXISTS dayz_servers (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                server_id VARCHAR(64) NOT NULL,
                api_key_hash CHAR(64) NOT NULL,
                enabled BOOLEAN NOT NULL DEFAULT TRUE,
                created_at DATETIME(3) NOT NULL,
                CONSTRAINT uk_dayz_servers_server_id UNIQUE (server_id)
            )
            """).update();
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
