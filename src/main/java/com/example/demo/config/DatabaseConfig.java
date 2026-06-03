package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

@Configuration
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String databaseUrl;

    @Bean
    public DataSource dataSource() {
        System.out.println("=== DatabaseConfig: dataSource() initialized ===");
        System.out.println("=== Original databaseUrl: " + databaseUrl);
        String rawUrl = databaseUrl;
        if (rawUrl != null && rawUrl.startsWith("jdbc:")) {
            // Strip "jdbc:" to allow URI parsing of user info if it's formatted as jdbc:postgresql://user:pass@host/db
            rawUrl = rawUrl.substring(5);
        }
        System.out.println("=== Parsed rawUrl: " + rawUrl);

        // If the URL is in postgresql:// or postgres:// URI format (e.g. from Render or Heroku)
        if (rawUrl != null && (rawUrl.startsWith("postgres://") || rawUrl.startsWith("postgresql://"))) {
            System.out.println("=== Matches postgres/postgresql URI format ===");
            try {
                URI dbUri = new URI(rawUrl);
                String userInfo = dbUri.getUserInfo();
                if (userInfo != null && userInfo.contains(":")) {
                    String username = userInfo.split(":")[0];
                    String password = userInfo.split(":")[1];
                    
                    // Rebuild into valid JDBC URL format: jdbc:postgresql://host[:port]/database
                    String dbUrl = "jdbc:postgresql://" + dbUri.getHost();
                    if (dbUri.getPort() >= 0) {
                        dbUrl += ":" + dbUri.getPort();
                    }
                    dbUrl += dbUri.getPath();
                    
                    // Render databases require SSL by default
                    if (!dbUrl.contains("?")) {
                        dbUrl += "?sslmode=require";
                    }

                    System.out.println("=== Rebuilt dbUrl: " + dbUrl);
                    System.out.println("=== Username extracted: " + username);

                    return DataSourceBuilder.create()
                            .url(dbUrl)
                            .username(username)
                            .password(password)
                            .driverClassName("org.postgresql.Driver")
                            .build();
                } else {
                    System.out.println("=== No userInfo or colon found in rawUrl ===");
                }
            } catch (URISyntaxException | NullPointerException e) {
                System.out.println("=== URI parsing failed: " + e.getMessage());
                throw new RuntimeException("Failed to parse Render/Heroku PostgreSQL database URI: " + databaseUrl, e);
            }
        }

        System.out.println("=== Falling back to standard Spring Boot datasource ===");
        // Fallback to standard Spring Boot datasource creation
        return DataSourceBuilder.create()
                .url(databaseUrl)
                .driverClassName("org.postgresql.Driver")
                .build();
    }
}
