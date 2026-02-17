/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone;

import com.dialtone.server.DialtoneServer;
import com.dialtone.web.DialtoneWebServer;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.db.DatabaseManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * Unified launcher for the complete Dialtone application.
 * 
 * Starts both the Dialtone protocol server and the web management interface
 * in a single JVM process. This ensures proper database access coordination
 * and simplifies deployment in production environments.
 */
public class DialtoneApplication {
    
    private static DialtoneServer aolServer;
    private static DialtoneWebServer webServer;
    private static final CountDownLatch shutdownLatch = new CountDownLatch(1);
    
    public static void main(String[] args) {
        try {
            // ASCII art banner
            printBanner();
            
            // Load configuration
            Properties config = loadConfiguration();
            
            // Initialize database (singleton, shared by both servers)
            String dbPath = config.getProperty("db.path", "db/dialtone.db");
            DatabaseManager.getInstance(dbPath);
            LoggerUtil.info("Database initialized: " + dbPath);
            
            // Start Dialtone Protocol Server in its own thread
            startAolServer(config);
            
            // Start Web Interface Server in its own thread
            startWebServer(config);
            
            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LoggerUtil.info("Shutting down Dialtone Application...");
                shutdown();
            }));
            
            LoggerUtil.info("");
            LoggerUtil.info("========================================");
            LoggerUtil.info("Dialtone Application started successfully!");
            LoggerUtil.info("========================================");
            LoggerUtil.info("");
            
            // Keep the application running
            shutdownLatch.await();
            
        } catch (Exception e) {
            LoggerUtil.error("Failed to start Dialtone Application: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void startAolServer(Properties config) throws Exception {
        int port = Integer.parseInt(config.getProperty("server.port", "5190"));
        String bindAddr = config.getProperty("bind.address", "0.0.0.0");
        boolean verbose = Boolean.parseBoolean(config.getProperty("verbose", "false"));
        long delay = Long.parseLong(config.getProperty("packet.delay.ms", "0"));
        
        LoggerUtil.info("Starting Dialtone Protocol Server on " + bindAddr + ":" + port);
        
        Thread aolThread = new Thread(() -> {
            try {
                aolServer = new DialtoneServer(port, bindAddr, verbose, delay, config);
                aolServer.start();
            } catch (Exception e) {
                LoggerUtil.error("Dialtone Server failed: " + e.getMessage());
                e.printStackTrace();
                shutdown();
            }
        }, "Dialtone-Server-Thread");
        
        aolThread.setDaemon(false);
        aolThread.start();
        
        // Give it a moment to start
        Thread.sleep(1000);
        LoggerUtil.info("✓ Dialtone Protocol Server started on port " + port);
    }

    private static void startWebServer(Properties config) throws Exception {
        int webPort = Integer.parseInt(config.getProperty("web.port", "5200"));
        
        LoggerUtil.info("Starting Web Management Interface on port " + webPort);
        
        Thread webThread = new Thread(() -> {
            try {
                webServer = new DialtoneWebServer(config);
                webServer.start(webPort);
            } catch (Exception e) {
                LoggerUtil.error("Web Server failed: " + e.getMessage());
                e.printStackTrace();
                shutdown();
            }
        }, "Web-Server-Thread");
        
        webThread.setDaemon(false);
        webThread.start();
        
        // Give it a moment to start
        Thread.sleep(1000);
        LoggerUtil.info("✓ Web Management Interface started on port " + webPort);
        LoggerUtil.info("Access the web interface at: http://localhost:" + webPort);
    }
    
    /**
     * Loads configuration from application.properties.
     */
    private static Properties loadConfiguration() throws IOException {
        Properties config = new Properties();

        // First, load defaults from classpath resource
        LoggerUtil.info("Loading default configuration from classpath resource");
        try (InputStream inputStream = DialtoneApplication.class.getClassLoader()
                .getResourceAsStream("application.properties")) {

            if (inputStream == null) {
                throw new IOException("application.properties not found in classpath");
            }

            config.load(inputStream);
            LoggerUtil.info("Successfully loaded classpath configuration as defaults");
        }

        // Then, try to override with external file (for Docker/production deployment)
        java.nio.file.Path externalConfigPath = java.nio.file.Paths.get("resources", "application.properties");

        if (java.nio.file.Files.exists(externalConfigPath)) {
            LoggerUtil.info("Loading configuration overrides from external file: " + externalConfigPath.toAbsolutePath());
            try (InputStream inputStream = java.nio.file.Files.newInputStream(externalConfigPath)) {
                // Load external properties and let them override defaults
                Properties externalConfig = new Properties();
                externalConfig.load(inputStream);

                // Override defaults with external values
                config.putAll(externalConfig);

                LoggerUtil.info("Successfully loaded external configuration overrides (" +
                    externalConfig.size() + " properties)");
            } catch (IOException e) {
                LoggerUtil.warn("Failed to load external configuration overrides: " + e.getMessage());
                // Continue with defaults only
            }
        } else {
            LoggerUtil.info("No external configuration file found, using classpath defaults only");
        }

        // Validate required configuration
        validateConfiguration(config);

        return config;
    }
    

    private static void validateConfiguration(Properties config) {
        LoggerUtil.info("Validating configuration...");
        
        // Check database configuration
        String dbPath = config.getProperty("db.path");
        if (dbPath == null || dbPath.trim().isEmpty()) {
            config.setProperty("db.path", "db/dialtone.db");
            LoggerUtil.warn("No db.path configured, using default: db/dialtone.db");
        }
        
        LoggerUtil.info("✓ Configuration validation passed");
    }


    private static void shutdown() {
        try {
            if (webServer != null) {
                LoggerUtil.info("Stopping Web Server...");
                webServer.stop();
            }
            
            if (aolServer != null) {
                LoggerUtil.info("Stopping Dialtone Server...");
                aolServer.stop();
            }
            
            DatabaseManager databaseManager = DatabaseManager.getInstance(null);
            if (databaseManager != null) {
                databaseManager.close();
                LoggerUtil.info("Database connections closed");
            }
            
            LoggerUtil.info("Dialtone Application shutdown complete");
        } catch (Exception e) {
            LoggerUtil.error("Error during shutdown: " + e.getMessage());
        } finally {
            shutdownLatch.countDown();
        }
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════════════════════╗");
        System.out.println("  ║                                                          ║");
        System.out.println("  ║    ██████╗ ██╗ █████╗ ██╗  ████████╗ ██████╗ ███╗   ██╗███████╗ ║");
        System.out.println("  ║    ██╔══██╗██║██╔══██╗██║  ╚══██╔══╝██╔═══██╗████╗  ██║██╔════╝ ║");
        System.out.println("  ║    ██║  ██║██║███████║██║     ██║   ██║   ██║██╔██╗ ██║█████╗   ║");
        System.out.println("  ║    ██║  ██║██║██╔══██║██║     ██║   ██║   ██║██║╚██╗██║██╔══╝   ║");
        System.out.println("  ║    ██████╔╝██║██║  ██║███████╗██║   ╚██████╔╝██║ ╚████║███████╗ ║");
        System.out.println("  ║    ╚═════╝ ╚═╝╚═╝  ╚═╝╚══════╝╚═╝    ╚═════╝ ╚═╝  ╚═══╝╚══════╝ ║");
        System.out.println("  ║                                                          ║");
        System.out.println("  ╚══════════════════════════════════════════════════════════╝");
        System.out.println();
    }
}
