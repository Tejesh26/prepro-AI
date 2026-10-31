package com.tejesh.timetable.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

/**
 * <h2>DatabaseConfig – Cloud Persistence Auto-Configuration</h2>
 *
 * Automatically detects cloud environment variable {@code DATABASE_URL}
 * (standard on Render, Railway, Heroku) and constructs a PostgreSQL DataSource.
 * If {@code DATABASE_URL} is not set, falls back to the configured H2 file database.
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl != null && !databaseUrl.isBlank() &&
                (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://"))) {
            try {
                log.info("DatabaseConfig: Detected cloud PostgreSQL DATABASE_URL. Configuring PostgreSQL DataSource...");
                URI uri = new URI(databaseUrl.replace("postgresql://", "http://").replace("postgres://", "http://"));
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                String path = uri.getPath();
                String userInfo = uri.getUserInfo();
                String username = (userInfo != null && userInfo.contains(":")) ? userInfo.split(":")[0] : properties.getUsername();
                String password = (userInfo != null && userInfo.contains(":")) ? userInfo.split(":")[1] : properties.getPassword();

                String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path;
                HikariDataSource dataSource = new HikariDataSource();
                dataSource.setJdbcUrl(jdbcUrl);
                dataSource.setUsername(username);
                dataSource.setPassword(password);
                dataSource.setDriverClassName("org.postgresql.Driver");
                log.info("DatabaseConfig: Successfully configured PostgreSQL DataSource for {}:{}{}", host, port, path);
                return dataSource;
            } catch (Exception e) {
                log.error("DatabaseConfig: Failed to parse DATABASE_URL: {}. Falling back to default DataSource.", e.getMessage());
            }
        }
        return properties.initializeDataSourceBuilder().build();
    }
}
