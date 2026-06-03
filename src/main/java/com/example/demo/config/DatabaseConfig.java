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
        // If the URL is in postgresql:// or postgres:// URI format (e.g. from Render or Heroku)
        if (databaseUrl != null && (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://"))) {
            try {
                URI dbUri = new URI(databaseUrl);
                String userInfo = dbUri.getUserInfo();
                if (userInfo != null && userInfo.contains(":")) {
                    String username = userInfo.split(":")[0];
                    String password = userInfo.split(":")[1];
                    
                    // Rebuild into valid JDBC URL format: jdbc:postgresql://host:port/database
                    String dbUrl = "jdbc:postgresql://" + dbUri.getHost() + ":" + dbUri.getPort() + dbUri.getPath();
                    
                    // Render databases require SSL by default
                    if (!dbUrl.contains("?")) {
                        dbUrl += "?sslmode=require";
                    }

                    return DataSourceBuilder.create()
                            .url(dbUrl)
                            .username(username)
                            .password(password)
                            .driverClassName("org.postgresql.Driver")
                            .build();
                }
            } catch (URISyntaxException | NullPointerException e) {
                throw new RuntimeException("Failed to parse Render/Heroku PostgreSQL database URI: " + databaseUrl, e);
            }
        }

        // Fallback to standard Spring Boot datasource creation
        return DataSourceBuilder.create()
                .url(databaseUrl)
                .driverClassName("org.postgresql.Driver")
                .build();
    }
}
