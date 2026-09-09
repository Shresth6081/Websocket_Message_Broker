package com.example.userservice.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DatabaseConfig {

    private final DataSourceProperties properties;

    @PostConstruct
    public void normalizeJdbcUrl() {
        String url = properties.getUrl();
        if (url != null) {
            if (url.startsWith("postgres://")) {
                String fixed = "jdbc:postgresql://" + url.substring("postgres://".length());
                log.info("Normalizing postgres:// URL to JDBC format: {}", fixed.replaceAll(":.*@", ":****@"));
                properties.setUrl(fixed);
            } else if (url.startsWith("postgresql://")) {
                String fixed = "jdbc:" + url;
                log.info("Normalizing postgresql:// URL to JDBC format: {}", fixed.replaceAll(":.*@", ":****@"));
                properties.setUrl(fixed);
            }
        }
    }
}
