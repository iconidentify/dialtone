/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web;

import com.dialtone.db.DatabaseManager;
import com.dialtone.db.SchemaInitializer;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.web.auth.AuthController;
import com.dialtone.web.api.ScreennameController;
import com.dialtone.web.api.ScreennamePreferencesController;
import com.dialtone.web.api.AdminUserController;
import com.dialtone.web.api.AdminScreennameController;
import com.dialtone.web.api.AdminAuditController;
import com.dialtone.web.api.AdminSystemController;
import com.dialtone.web.api.AdminFdoController;
import com.dialtone.web.api.FileTransferController;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.protocol.xfer.XferService;
import com.dialtone.storage.FileStorage;
import com.dialtone.storage.StorageFactory;
import com.dialtone.web.security.CsrfProtectionService;
import com.dialtone.web.services.ResendEmailService;
import com.dialtone.web.services.EmailAuthService;
import com.dialtone.web.security.SecurityService;
import com.dialtone.web.security.AdminAuthenticationFilter;
import com.dialtone.web.services.DiscordAuthService;
import com.dialtone.web.services.JwtTokenService;
import com.dialtone.web.services.XAuthService;
import com.dialtone.web.services.ScreennameService;
import com.dialtone.web.services.ScreennamePreferencesService;
import com.dialtone.web.services.UserService;
import com.dialtone.web.services.AdminSecurityService;
import com.dialtone.web.services.AdminAuditService;
import com.dialtone.web.services.AolMetricsService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Main web server for Dialtone's user management interface.
 * <p>
 * Provides REST API endpoints for X OAuth authentication, screenname management,
 * and serves the React frontend application.
 */
public class DialtoneWebServer {
    private final DatabaseManager databaseManager;
    private final XAuthService xAuthService;
    private final DiscordAuthService discordAuthService;
    private final ScreennameService screennameService;
    private final ScreennamePreferencesService preferencesService;
    private final UserService userService;
    private final AdminSecurityService adminSecurityService;
    private final AdminAuditService adminAuditService;
    private final AolMetricsService aolMetricsService;
    private final JwtTokenService jwtTokenService;
    private final AuthController authController;
    private final ScreennameController screennameController;
    private final ScreennamePreferencesController preferencesController;
    private final AdminUserController adminUserController;
    private final AdminScreennameController adminScreennameController;
    private final AdminAuditController adminAuditController;
    private final AdminSystemController adminSystemController;
    private final AdminFdoController adminFdoController;
    private final FileTransferController fileTransferController;
    private final AdminAuthenticationFilter adminAuthFilter;
    private final CsrfProtectionService csrfService;
    private final FdoCompiler fdoCompiler;
    private final ResendEmailService resendEmailService;
    private final EmailAuthService emailAuthService;
    private final Gson gson;
    private final Properties config;
    private Javalin app;

    public DialtoneWebServer(Properties config) {
        this.config = config;

        // Initialize database
        String dbPath = config.getProperty("db.path", "db/dialtone.db");
        this.databaseManager = DatabaseManager.getInstance(dbPath);

        // Initialize database schema
        try {
            SchemaInitializer.initializeSchema(databaseManager);
        } catch (Exception e) {
            LoggerUtil.error("Failed to initialize database schema: " + e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
        }

        // Initialize services
        this.xAuthService = new XAuthService(config);
        this.discordAuthService = new DiscordAuthService(config);
        this.userService = new UserService(databaseManager);
        this.adminSecurityService = new AdminSecurityService(config, databaseManager);
        this.screennameService = new ScreennameService(databaseManager, adminSecurityService, userService);
        this.preferencesService = new ScreennamePreferencesService(databaseManager);
        this.adminAuditService = new AdminAuditService(config, databaseManager);
        this.aolMetricsService = new AolMetricsService(databaseManager, config);
        this.jwtTokenService = new JwtTokenService(config);
        this.csrfService = new CsrfProtectionService();

        // Initialize email services
        this.resendEmailService = new ResendEmailService(config);
        this.emailAuthService = new EmailAuthService(databaseManager, resendEmailService, config);

        // Initialize controllers
        this.authController = new AuthController(xAuthService, discordAuthService, emailAuthService, screennameService, jwtTokenService, adminSecurityService);
        this.screennameController = new ScreennameController(screennameService, csrfService);
        this.preferencesController = new ScreennamePreferencesController(preferencesService, csrfService);

        // Initialize admin controllers
        this.adminUserController = new AdminUserController(userService, screennameService, adminSecurityService, adminAuditService, csrfService);
        this.adminScreennameController = new AdminScreennameController(screennameService, preferencesService, userService, adminSecurityService, adminAuditService, csrfService);
        this.adminAuditController = new AdminAuditController(adminAuditService, adminSecurityService, csrfService);
        this.adminSystemController = new AdminSystemController(userService, adminSecurityService, adminAuditService, csrfService, aolMetricsService);

        // Initialize FDO compiler for admin FDO workbench
        this.fdoCompiler = new FdoCompiler(config);
        this.adminFdoController = new AdminFdoController(adminSecurityService, adminAuditService, csrfService, fdoCompiler);

        // Initialize file transfer controller with XferService and FileStorage
        XferService xferService = new XferService(fdoCompiler);
        FileStorage fileStorage;
        try {
            fileStorage = StorageFactory.createWithFallback(config);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to initialize file storage", e);
        }
        this.fileTransferController = new FileTransferController(screennameService, csrfService, xferService, fileStorage, config);

        this.adminAuthFilter = new AdminAuthenticationFilter(adminSecurityService);

        // Configure JSON serialization with LocalDateTime support
        this.gson = new GsonBuilder().registerTypeAdapter(LocalDateTime.class, (JsonSerializer<LocalDateTime>) (src, typeOfSrc, context) -> context.serialize(src.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))).registerTypeAdapter(LocalDateTime.class, (JsonDeserializer<LocalDateTime>) (json, typeOfT, context) -> LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME)).setPrettyPrinting().create();
    }

    /**
     * Starts the web server on the specified port.
     *
     * @param port Port to bind the server to
     */
    public void start(int port) {
        // Parse CORS allowed origins from config
        String corsOriginsConfig = config.getProperty("cors.allowed.origins", "http://localhost:3000,http://localhost:5173,http://localhost:5200");
        String[] corsOrigins = corsOriginsConfig.split(",");

        // Log configured CORS origins
        LoggerUtil.info("CORS allowed origins: " + corsOriginsConfig);

        app = Javalin.create(javalinConfig -> {
            // Configure JSON handling
            javalinConfig.jsonMapper(new GsonJsonMapper(gson));

            // Enable CORS with origins from application.properties
            javalinConfig.bundledPlugins.enableCors(cors -> {
                cors.addRule(corsRule -> {
                    // Add each configured origin
                    for (String origin : corsOrigins) {
                        String trimmedOrigin = origin.trim();
                        if (!trimmedOrigin.isEmpty()) {
                            corsRule.allowHost(trimmedOrigin);
                        }
                    }
                    corsRule.allowCredentials = true;
                });
            });

            // Serve static files for React app (production)
            javalinConfig.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
                staticFiles.precompress = false;
            });

            // Enable request logging in development
            javalinConfig.bundledPlugins.enableDevLogging();
        });

        // Configure routes
        configureRoutes();

        // Configure security middleware
        configureSecurityMiddleware();

        // Error handlers
        configureErrorHandlers();

        // Start server
        app.start(port);
        LoggerUtil.info("Dialtone Web Server started on port " + port);
    }

    /**
     * Stops the web server.
     */
    public void stop() {
        if (app != null) {
            app.stop();
            LoggerUtil.info("Dialtone Web Server stopped");
        }

        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    /**
     * Configures API routes and handlers.
     */
    private void configureRoutes() {
        // Health check endpoint
        app.get("/api/health", ctx -> {
            ctx.json(new HealthResponse("OK", System.currentTimeMillis(), databaseManager.getStats()));
        });

        // Root path now served by static file handler (React app index.html)

        // Authentication routes - X OAuth
        app.get("/api/auth/x/login", authController::initiateXLogin);
        app.get("/api/auth/x/callback", authController::handleXCallback);

        // Authentication routes - Discord OAuth
        app.get("/api/auth/discord/login", authController::initiateDiscordLogin);
        app.get("/api/auth/discord/callback", authController::handleDiscordCallback);

        // Authentication routes - Email Magic Link
        app.post("/api/auth/email/login", authController::initiateEmailLogin);
        app.get("/api/auth/email/verify", authController::verifyMagicLink);

        // Auth info routes
        app.get("/api/auth/providers", authController::getAuthProviders);
        app.get("/api/auth/me", authController::getCurrentUser);
        app.post("/api/auth/logout", authController::logout);

        // Authentication middleware for protected routes
        app.before("/api/auth/me", this::requireAuth);
        app.before("/api/csrf-token", this::requireAuth);
        app.before("/api/screennames", this::requireAuth);
        app.before("/api/screennames/*", this::requireAuth);

        // CSRF token endpoint (requires authentication)
        app.get("/api/csrf-token", this::getCsrfToken);

        // Screenname management routes (require authentication)
        app.get("/api/screennames", screennameController::getScreennames);
        app.post("/api/screennames", screennameController::createScreenname);
        app.put("/api/screennames/{id}", screennameController::updateScreenname);
        app.put("/api/screennames/{id}/password", screennameController::updatePassword);
        app.put("/api/screennames/{id}/primary", screennameController::setPrimary);
        app.delete("/api/screennames/{id}", screennameController::deleteScreenname);

        // Screenname preferences routes (require authentication)
        app.get("/api/screennames/{id}/preferences", preferencesController::getPreferences);
        app.put("/api/screennames/{id}/preferences", preferencesController::updatePreferences);

        // File transfer routes (require authentication)
        app.before("/api/transfer/*", this::requireAuth);
        app.get("/api/transfer/config", fileTransferController::getConfig);
        app.get("/api/transfer/connected-screennames", fileTransferController::getConnectedScreennames);
        app.post("/api/transfer/upload", fileTransferController::uploadFile);
        // Upload routes (client to server file transfer)
        app.post("/api/transfer/request-upload", fileTransferController::requestUpload);
        app.get("/api/transfer/uploads/{screenname}", fileTransferController::listUploads);
        app.get("/api/transfer/uploads/{screenname}/{filename}", fileTransferController::downloadUpload);
        app.delete("/api/transfer/uploads/{screenname}/{filename}", fileTransferController::deleteUpload);

        // Admin route protection middleware
        app.before("/api/admin/*", this::requireAuth);  // First require general auth
        app.before("/api/admin/*", adminAuthFilter);    // Then require admin auth

        // Admin user management routes
        app.get("/api/admin/users", adminUserController::listUsers);
        app.post("/api/admin/users", adminUserController::createUser);
        app.get("/api/admin/users/{id}", adminUserController::getUserDetails);
        app.put("/api/admin/users/{id}/status", adminUserController::updateUserStatus);
        app.delete("/api/admin/users/{id}", adminUserController::deleteUser);
        app.get("/api/admin/users/{id}/screennames", adminUserController::getUserScreennames);
        app.post("/api/admin/users/{id}/screennames", adminUserController::createUserScreenname);
        app.put("/api/admin/users/{userId}/screennames/{screennameId}/password", adminUserController::resetScreennamePassword);

        // Admin screenname management routes
        app.get("/api/admin/screennames", adminScreennameController::listScreennames);
        app.delete("/api/admin/screennames/{id}", adminScreennameController::deleteScreenname);
        app.put("/api/admin/screennames/{id}/password", adminScreennameController::resetScreennamePassword);
        app.get("/api/admin/screennames/{id}/preferences", adminScreennameController::getPreferencesAdmin);
        app.put("/api/admin/screennames/{id}/preferences", adminScreennameController::updatePreferencesAdmin);

        // Admin audit log routes
        app.get("/api/admin/audit", adminAuditController::getAuditLog);
        app.get("/api/admin/audit/stats", adminAuditController::getAuditStats);
        app.post("/api/admin/audit/cleanup", adminAuditController::triggerAuditCleanup);
        app.get("/api/admin/audit/recent", adminAuditController::getRecentActivity);
        app.get("/api/admin/audit/user/{userId}", adminAuditController::getUserAuditLog);

        // Admin system management routes
        app.get("/api/admin/system/stats", adminSystemController::getSystemStats);
        app.get("/api/admin/system/health", adminSystemController::getSystemHealth);
        app.get("/api/admin/aol/metrics", adminSystemController::getAolMetrics);
        app.post("/api/admin/roles/{userId}/grant", adminSystemController::grantAdminRole);
        app.delete("/api/admin/roles/{userId}", adminSystemController::revokeAdminRole);

        // Admin FDO workbench routes
        app.get("/api/admin/fdo/connected-screennames", adminFdoController::getConnectedScreennames);
        app.post("/api/admin/fdo/send", adminFdoController::sendFdo);
    }

    /**
     * Configures security middleware and headers.
     */
    private void configureSecurityMiddleware() {
        // Add security headers to all responses
        app.before("*", ctx -> {
            // Prevent MIME type sniffing
            ctx.header("X-Content-Type-Options", "nosniff");

            // Enable XSS protection (legacy browsers)
            ctx.header("X-XSS-Protection", "1; mode=block");

            // Prevent page from being embedded in frames (clickjacking protection)
            ctx.header("X-Frame-Options", "DENY");

            // Force HTTPS in production (commented for development)
            // ctx.header("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

            // Content Security Policy - restrict resource loading
            ctx.header("Content-Security-Policy", "default-src 'self'; " + "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " + "style-src 'self' 'unsafe-inline'; " + "img-src 'self' data:; " + "connect-src 'self'; " + "font-src 'self'; " + "object-src 'none'; " + "base-uri 'self'");

            // Referrer policy
            ctx.header("Referrer-Policy", "strict-origin-when-cross-origin");
        });

        // Request size validation middleware
        app.before("*", ctx -> {
            if (ctx.method().toString().equals("POST") || ctx.method().toString().equals("PUT")) {
                long contentLength = ctx.contentLength();
                // File transfer endpoint has its own size limit (configured via transfer.max.file.size.mb)
                // Use 110MB to allow for multipart overhead on 100MB files
                long maxSize = ctx.path().startsWith("/api/transfer/")
                    ? 110 * 1024 * 1024  // 110MB for file transfers
                    : 1024 * 1024;        // 1MB for other requests

                if (!SecurityService.validateRequestSize(contentLength, maxSize)) {
                    ctx.status(413).json(new ErrorResponse("Request too large", "Request size exceeds maximum allowed size"));
                }
            }
        });
    }

    /**
     * Configures error handlers for proper JSON error responses.
     */
    private void configureErrorHandlers() {
        app.exception(Exception.class, (e, ctx) -> {
            LoggerUtil.error("Unhandled exception in web request: " + e.getMessage());
            ctx.status(500).json(new ErrorResponse("Internal server error", e.getMessage()));
        });

        app.error(404, ctx -> {
            if (ctx.path().startsWith("/api/")) {
                ctx.json(new ErrorResponse("Not found", "API endpoint not found: " + ctx.path()));
            } else {
                // For non-API routes, serve the React app (SPA routing)
                try {
                    ctx.status(200);
                    ctx.contentType("text/html");
                    ctx.result(DialtoneWebServer.class.getResourceAsStream("/public/index.html"));
                } catch (Exception e) {
                    ctx.status(500).result("Error loading application");
                }
            }
        });

        app.error(401, ctx -> {
            ctx.json(new ErrorResponse("Unauthorized", "Authentication required"));
        });

        app.error(403, ctx -> {
            ctx.json(new ErrorResponse("Forbidden", "Access denied"));
        });
    }

    /**
     * Authentication middleware that validates JWT tokens.
     */
    private void requireAuth(Context ctx) {
        String token = resolveJwtFromRequest(ctx);
        if (token == null) {
            ctx.status(401);
            throw new RuntimeException("Missing or invalid authorization token");
        }

        // Validate JWT token
        JwtTokenService.TokenValidationResult result = jwtTokenService.validateToken(token);

        if (!result.isValid()) {
            LoggerUtil.debug("Token validation failed: " + result.getErrorMessage());
            ctx.status(401);
            throw new RuntimeException("Token validation failed: " + result.getErrorMessage());
        }

        // Store user info in context for controllers to use
        ctx.attribute("user", result.getTokenUser().toUser());
    }

    private String resolveJwtFromRequest(Context ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        String cookieToken = ctx.cookie(AuthController.AUTH_COOKIE_NAME);
        if (cookieToken != null && !cookieToken.isBlank()) {
            return cookieToken;
        }

        return null;
    }

    /**
     * Generates and returns a CSRF token for the authenticated user.
     */
    private void getCsrfToken(Context ctx) {
        try {
            String csrfToken = csrfService.generateCsrfToken(ctx);
            ctx.json(new CsrfTokenResponse(csrfToken));
        } catch (Exception e) {
            LoggerUtil.error("Failed to generate CSRF token: " + e.getMessage());
            ctx.status(500).json(new ErrorResponse("Server error", "Failed to generate CSRF token"));
        }
    }

    /**
     * Response record for health check endpoint.
     */
    private record HealthResponse(String status, long timestamp, String dbStats) {
    }

    /**
     * Response record for CSRF token.
     */
    private record CsrfTokenResponse(String token) {
    }

    /**
     * Response record for error messages.
     */
    private record ErrorResponse(String error, String message) {
    }
}