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

        DayzDatabase db = new DayzDatabase(new HikariDataSource(config));
        db.initSchema("JSON");
        return db;
    }
}
