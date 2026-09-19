package dev.tylerpac.backend.dayz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
@ConditionalOnProperty(name = "app.dayz.enabled", havingValue = "true")
public class DayzDatabaseConfig {

    @Bean(destroyMethod = "close")
    public DayzDatabase dayzDatabase(
        @Value("${app.dayz.datasource.url}") String url,
        @Value("${app.dayz.datasource.username}") String username,
        @Value("${app.dayz.datasource.password}") String password,
        @Value("${app.dayz.datasource.max-pool-size:10}") int maxPoolSize
    ) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("dayz-pool");
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5_000);
        // Fail fast instead of tying up Tomcat threads when the DayZ database stalls, and reuse prepared statements.
        config.addDataSourceProperty("connectTimeout", "3000");
        config.addDataSourceProperty("socketTimeout", "15000");
        // Lets a JDBC batch (XP flush) go to MySQL as one round trip.
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "100");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "1024");

        DayzDatabase db = new DayzDatabase(new HikariDataSource(config));
        db.initSchema("JSON");
        db.initSkinsAndSkillsSchema("JSON");
        return db;
    }
}
