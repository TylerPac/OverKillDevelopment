package dev.tylerpac.backend.dayz.config;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate template;

    public DayzDatabase(HikariDataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = JdbcClient.create(dataSource);
        this.template = new JdbcTemplate(dataSource);
    }

    public JdbcClient jdbc() {
        return jdbc;
    }

    /** For JDBC batch updates (JdbcClient has no batch API). */
    public JdbcTemplate template() {
        return template;
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

    /** Skins catalog, ownership, per-weapon equipped skin and per-category skill XP. Idempotent. */
    public void initSkinsAndSkillsSchema(String jsonColumnType) {
        jdbc.sql("""
            CREATE TABLE IF NOT EXISTS skins (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                skin_key VARCHAR(64) NOT NULL,
                weapon_type VARCHAR(64) NOT NULL,
                display_name VARCHAR(128) NOT NULL,
                textures %s NOT NULL,
                materials %s NOT NULL,
                enabled BOOLEAN NOT NULL DEFAULT TRUE,
                price_cents INT NULL,
                skin_type VARCHAR(16) NOT NULL DEFAULT 'texture',
                variant_class VARCHAR(64) NULL,
                created_at DATETIME(3) NOT NULL,
                CONSTRAINT uk_skins_skin_key UNIQUE (skin_key)
            )
            """.formatted(jsonColumnType, jsonColumnType)).update();

        // Tables created before item skins existed: add the columns once (CREATE TABLE IF NOT EXISTS never alters).
        addColumnIfMissing("skins", "skin_type", "VARCHAR(16) NOT NULL DEFAULT 'texture'");
        addColumnIfMissing("skins", "variant_class", "VARCHAR(64) NULL");

        jdbc.sql("""
            CREATE TABLE IF NOT EXISTS player_skins (
                id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                player_id BIGINT NOT NULL,
                skin_id BIGINT NOT NULL,
                source VARCHAR(16) NOT NULL,
                granted_at DATETIME(3) NOT NULL,
                CONSTRAINT uk_player_skins_player_skin UNIQUE (player_id, skin_id),
                CONSTRAINT fk_player_skins_player FOREIGN KEY (player_id) REFERENCES players (id) ON DELETE CASCADE,
                CONSTRAINT fk_player_skins_skin FOREIGN KEY (skin_id) REFERENCES skins (id) ON DELETE CASCADE
            )
            """).update();

        jdbc.sql("""
            CREATE TABLE IF NOT EXISTS player_skin_equipped (
                player_id BIGINT NOT NULL,
                weapon_type VARCHAR(64) NOT NULL,
                skin_id BIGINT NOT NULL,
                PRIMARY KEY (player_id, weapon_type),
                CONSTRAINT fk_equipped_player FOREIGN KEY (player_id) REFERENCES players (id) ON DELETE CASCADE,
                CONSTRAINT fk_equipped_skin FOREIGN KEY (skin_id) REFERENCES skins (id) ON DELETE CASCADE
            )
            """).update();

        jdbc.sql("""
            CREATE TABLE IF NOT EXISTS player_skill_xp (
                player_id BIGINT NOT NULL,
                category VARCHAR(32) NOT NULL,
                xp BIGINT NOT NULL,
                updated_at DATETIME(3) NOT NULL,
                PRIMARY KEY (player_id, category),
                CONSTRAINT fk_skill_xp_player FOREIGN KEY (player_id) REFERENCES players (id) ON DELETE CASCADE
            )
            """).update();
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        Long present = jdbc.sql("""
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
            WHERE LOWER(TABLE_NAME) = :table AND LOWER(COLUMN_NAME) = :column AND TABLE_SCHEMA = SCHEMA()
            """)
            .param("table", table)
            .param("column", column)
            .query(Long.class)
            .single();
        if (present == 0) {
            jdbc.sql("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition).update();
        }
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
