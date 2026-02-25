/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.db;

import com.dialtone.utils.LoggerUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages SQLite database connection pooling and initialization.
 *
 * Provides a singleton HikariCP connection pool for SQLite database access.
 * Handles database file creation and path setup.
 */
public class DatabaseManager {
    private static DatabaseManager instance;
    private final HikariDataSource dataSource;
    private final String dbPath;

    private DatabaseManager(String dbPath) {
        this.dbPath = dbPath;

        // Ensure parent directory exists
        createParentDirectoryIfNeeded(dbPath);

        // Configure HikariCP for SQLite
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setMaximumPoolSize(10); // SQLite handles concurrent reads well
        config.setConnectionTimeout(30000); // 30 seconds
        config.setIdleTimeout(600000); // 10 minutes
        config.setMaxLifetime(1800000); // 30 minutes

        // SQLite-specific optimizations
        config.addDataSourceProperty("journal_mode", "WAL"); // Write-Ahead Logging for better concurrency
        config.addDataSourceProperty("synchronous", "NORMAL"); // Good balance of safety vs performance
        config.addDataSourceProperty("cache_size", "2000"); // 2MB cache
        config.addDataSourceProperty("temp_store", "memory"); // Store temp tables in memory

        this.dataSource = new HikariDataSource(config);

        LoggerUtil.info("Database connection pool initialized: " + dbPath);
    }

    /**
     * Gets the singleton DatabaseManager instance.
     * Creates a new instance if one doesn't exist.
     *
     * @param dbPath Path to the SQLite database file
     * @return DatabaseManager instance
     */
    public static synchronized DatabaseManager getInstance(String dbPath) {
        if (instance == null || !instance.dbPath.equals(dbPath)) {
            if (instance != null) {
                instance.close();
            }
            instance = new DatabaseManager(dbPath);
        }
        return instance;
    }

    /**
     * Gets the HikariCP data source for database connections.
     *
     * @return HikariDataSource for connection management
     */
    public HikariDataSource getDataSource() {
        return dataSource;
    }

    /**
     * Gets the database file path.
     *
     * @return Database file path
     */
    public String getDbPath() {
        return dbPath;
    }

    /**
     * Checks if the database file exists.
     *
     * @return true if database file exists, false otherwise
     */
    public boolean databaseExists() {
        File dbFile = new File(dbPath);
        return dbFile.exists() && dbFile.length() > 0;
    }

    /**
     * Creates the parent directory for the database file if it doesn't exist.
     *
     * @param dbPath Path to database file
     */
    private void createParentDirectoryIfNeeded(String dbPath) {
        Path path = Paths.get(dbPath);
        Path parentDir = path.getParent();

        if (parentDir != null) {
            File dir = parentDir.toFile();
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (created) {
                    LoggerUtil.info("Created database directory: " + parentDir);
                } else {
                    LoggerUtil.warn("Failed to create database directory: " + parentDir);
                }
            }
        }
    }

    /**
     * Closes the database connection pool.
     * Should be called during application shutdown.
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            LoggerUtil.info("Database connection pool closed");
        }
    }

    /**
     * Gets basic database statistics for monitoring.
     *
     * @return String with pool statistics
     */
    public String getStats() {
        if (dataSource == null) {
            return "DatabaseManager not initialized";
        }

        return String.format("DB Pool - Active: %d, Idle: %d, Total: %d, Pending: %d",
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getTotalConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }
}