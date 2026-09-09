package com.example.chatservice.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
@Slf4j
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String rawUrl;

    @Value("${spring.datasource.username:}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Bean
    @Primary
    public DataSource dataSource() {
        String jdbcUrl = rawUrl;
        String user = username;
        String pass = password;

        if (rawUrl != null) {
            if (rawUrl.startsWith("postgres://") || rawUrl.startsWith("postgresql://")) {
                try {
                    String cleanUrl = rawUrl.startsWith("postgres://")
                            ? "http://" + rawUrl.substring("postgres://".length())
                            : "http://" + rawUrl.substring("postgresql://".length());
                    URI uri = URI.create(cleanUrl);

                    String host = uri.getHost();
                    int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                    String path = uri.getPath() != null && uri.getPath().length() > 1 ? uri.getPath() : "/chatbrokerdb";

                    if (uri.getUserInfo() != null) {
                        String[] userInfo = uri.getUserInfo().split(":", 2);
                        if (userInfo.length > 0 && (user == null || user.isEmpty())) {
                            user = userInfo[0];
                        }
                        if (userInfo.length > 1 && (pass == null || pass.isEmpty())) {
                            pass = userInfo[1];
                        }
                    }

                    jdbcUrl = String.format("jdbc:postgresql://%s:%d%s", host, port, path);
                } catch (Exception e) {
                    log.warn("Failed to parse URI into JDBC format, using direct prefix: {}", e.getMessage());
                    jdbcUrl = rawUrl.startsWith("postgres://")
                            ? "jdbc:postgresql://" + rawUrl.substring("postgres://".length())
                            : "jdbc:" + rawUrl;
                }
            }
        }

        log.info("Configured DataSource with JDBC URL: {}", jdbcUrl != null ? jdbcUrl.replaceAll(":.*@", ":****@") : null);

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setDriverClassName(driverClassName);
        if (user != null && !user.isEmpty()) {
            dataSource.setUsername(user);
        }
        if (pass != null && !pass.isEmpty()) {
            dataSource.setPassword(pass);
        }
        return dataSource;
    }
}
