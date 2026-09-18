package dev.tylerpac.backend.dayz;

import java.util.UUID;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import dev.tylerpac.backend.dayz.config.DayzDatabase;

public final class DayzTestSupport {

    private DayzTestSupport() {}

    /** Fresh in-memory H2 (MySQL mode) with the real schema; the JSON column is a CLOB because H2 JSON differs from MySQL. */
    public static DayzDatabase newDatabase() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:dayz-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);
        DayzDatabase db = new DayzDatabase(new HikariDataSource(config));
        db.initSchema("CLOB");
        db.initSkinsAndSkillsSchema("CLOB");
        return db;
    }
}
