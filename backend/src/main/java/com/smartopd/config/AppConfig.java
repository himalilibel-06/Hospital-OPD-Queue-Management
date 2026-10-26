package com.smartopd.config;

import com.smartopd.dao.ConnectionProvider;
import com.smartopd.service.QueueService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * AppConfig - Spring configuration exposing core domain services as singletons.
 * 
 * ============================================================================
 * OOP & Architectural Concepts:
 * - Dependency Injection (DI): Injects configured database path from property smartopd.db.path.
 * - Singleton Pattern: Exactly ONE QueueService instance coordinates all queue state,
 *   single-writer lock serialization, and write-through SQLite persistence.
 * ============================================================================
 */
@Configuration
public class AppConfig {

    @Value("${smartopd.db.path:}")
    private String configuredDbPath;

    @Bean
    public QueueService queueService(com.smartopd.api.security.SessionStore sessionStore) {
        String dbPath = ConnectionProvider.resolveDbPath();
        if (configuredDbPath != null && !configuredDbPath.trim().isEmpty()) {
            dbPath = configuredDbPath.trim();
        }
        File dbFile = new File(dbPath);
        File parentDir = dbFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        ConnectionProvider connectionProvider = new ConnectionProvider(dbPath);
        return new QueueService(connectionProvider, sessionStore);
    }
}
