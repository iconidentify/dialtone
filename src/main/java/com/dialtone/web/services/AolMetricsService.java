/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.services;

import com.dialtone.db.DatabaseManager;
import com.dialtone.utils.LoggerUtil;

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Service for collecting AOL server operational metrics.
 *
 * Provides insights into the live operation of the Dialtone AOL server,
 * including server status, configuration, and protocol statistics.
 */
public class AolMetricsService {

    private final DatabaseManager databaseManager;
    private final Properties configuration;
    private final long serverStartTime;

    public AolMetricsService(DatabaseManager databaseManager, Properties configuration) {
        this.databaseManager = databaseManager;
        this.configuration = configuration;
        this.serverStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();
    }

    /**
     * Get comprehensive AOL server metrics for the admin dashboard.
     *
     * @return Map containing categorized metrics
     */
    public Map<String, Object> getAolMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            // Server Status
            Map<String, Object> server = new HashMap<>();
            server.put("uptimeHours", getServerUptimeHours());
            server.put("status", "running");
            metrics.put("server", server);

            // AOL Protocol Configuration
            Map<String, Object> protocol = new HashMap<>();
            protocol.put("aolPort", getAolServerPort());
            protocol.put("grokEnabled", isGrokEnabled());
            protocol.put("atomforgeUrl", getAtomforgeUrl());
            protocol.put("verboseLogging", isVerboseLogging());
            metrics.put("protocol", protocol);

            // AI Services
            Map<String, Object> ai = new HashMap<>();
            ai.put("grokEnabled", isGrokEnabled());
            ai.put("grokModel", getGrokModel());
            ai.put("newsServiceEnabled", isNewsServiceEnabled());
            metrics.put("ai", ai);

            // Database Health
            Map<String, Object> database = new HashMap<>();
            database.put("connectionPoolActive", isDatabaseHealthy());
            database.put("connectionPoolSize", getDatabasePoolSize());
            database.put("databasePath", getDatabasePath());
            metrics.put("database", database);

            metrics.put("timestamp", System.currentTimeMillis());

        } catch (Exception e) {
            LoggerUtil.error("Failed to collect AOL metrics: " + e.getMessage());
            metrics.put("error", "Failed to collect metrics: " + e.getMessage());
        }

        return metrics;
    }

    /**
     * Get server uptime in hours.
     */
    private double getServerUptimeHours() {
        long uptimeMs = System.currentTimeMillis() - serverStartTime;
        return Math.round((uptimeMs / (1000.0 * 60.0 * 60.0)) * 100.0) / 100.0;
    }

    /**
     * Get AOL server port from configuration.
     */
    private int getAolServerPort() {
        if (configuration == null) return 5191;
        return Integer.parseInt(configuration.getProperty("server.port", "5191"));
    }

    /**
     * Check if Grok AI is enabled.
     */
    private boolean isGrokEnabled() {
        if (configuration == null) return false;
        return Boolean.parseBoolean(configuration.getProperty("grok.enabled", "false"));
    }

    /**
     * Get Atomforge API URL.
     */
    private String getAtomforgeUrl() {
        if (configuration == null) return "Not configured";
        return configuration.getProperty("atomforge.base.url", "Not configured");
    }

    /**
     * Check if verbose logging is enabled.
     */
    private boolean isVerboseLogging() {
        if (configuration == null) return false;
        return Boolean.parseBoolean(configuration.getProperty("verbose", "false"));
    }

    /**
     * Get Grok AI model name.
     */
    private String getGrokModel() {
        if (configuration == null) return "Not configured";
        return configuration.getProperty("grok.model", "Not configured");
    }

    /**
     * Check if news service is enabled.
     */
    private boolean isNewsServiceEnabled() {
        // News service is typically enabled if Grok is enabled
        return isGrokEnabled();
    }

    /**
     * Get database file path.
     */
    private String getDatabasePath() {
        if (configuration == null) return "db/dialtone.db";
        return configuration.getProperty("db.path", "db/dialtone.db");
    }

    /**
     * Check database health by attempting a simple query.
     */
    private boolean isDatabaseHealthy() {
        try (Connection conn = databaseManager.getDataSource().getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT 1")) {
                stmt.executeQuery();
                return true;
            }
        } catch (SQLException e) {
            LoggerUtil.error("Database health check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get database connection pool size.
     */
    private int getDatabasePoolSize() {
        try {
            // HikariCP provides pool size via JMX, but for simplicity return configured max
            if (configuration != null) {
                String poolSize = configuration.getProperty("db.pool.max.size", "10");
                return Integer.parseInt(poolSize);
            }
            return 10; // Default pool size
        } catch (Exception e) {
            LoggerUtil.error("Failed to get database pool size: " + e.getMessage());
            return 0;
        }
    }
}