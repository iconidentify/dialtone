/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.api;

import com.dialtone.db.models.User;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.web.security.CsrfProtectionService;
import com.dialtone.web.services.AdminAuditService;
import com.dialtone.web.services.AdminSecurityService;
import io.javalin.http.Context;

import java.util.Map;
import java.util.Optional;

/**
 * Shared utility methods for admin controller operations.
 *
 * <p>Consolidates common patterns across admin controllers:
 * <ul>
 *   <li>Admin authentication and authorization</li>
 *   <li>Client IP/User-Agent extraction</li>
 *   <li>Rate limiting checks</li>
 *   <li>CSRF validation</li>
 *   <li>Audit logging</li>
 * </ul>
 */
public final class AdminControllerUtils {

    private AdminControllerUtils() {
        // Utility class - prevent instantiation
    }

    // =========================================================================
    // Admin Authentication
    // =========================================================================

    /**
     * Extracts and validates admin user from request context.
     *
     * <p>Performs two-layer validation:
     * <ol>
     *   <li>Checks user is authenticated (from JWT middleware)</li>
     *   <li>Verifies user has admin privileges via AdminSecurityService</li>
     * </ol>
     *
     * @param ctx Javalin request context
     * @param adminSecurityService Admin security service for authorization
     * @return Optional containing admin user if valid, empty if unauthorized
     */
    public static Optional<User> getAdminUser(Context ctx, AdminSecurityService adminSecurityService) {
        User user = ctx.attribute("user");
        if (user == null) {
            ctx.status(401).json(SharedErrorResponse.unauthorized("User not authenticated"));
            return Optional.empty();
        }

        if (!adminSecurityService.isAdmin(user)) {
            ctx.status(403).json(SharedErrorResponse.forbidden("Admin access required"));
            return Optional.empty();
        }

        return Optional.of(user);
    }

    /**
     * Extracts authenticated user from request context (non-admin).
     *
     * @param ctx Javalin request context
     * @return Optional containing user if authenticated, empty if not
     */
    public static Optional<User> getAuthenticatedUser(Context ctx) {
        User user = ctx.attribute("user");
        if (user == null) {
            ctx.status(401).json(SharedErrorResponse.unauthorized("User not authenticated"));
            return Optional.empty();
        }
        return Optional.of(user);
    }

    // =========================================================================
    // Rate Limiting
    // =========================================================================

    /**
     * Checks if admin user can perform action based on rate limiting.
     *
     * <p>If rate limit exceeded, sends 429 response automatically.
     *
     * @param ctx Javalin request context
     * @param admin Admin user performing action
     * @param adminSecurityService Admin security service for rate limiting
     * @return true if action allowed, false if rate limited (response already sent)
     */
    public static boolean checkRateLimit(Context ctx, User admin, AdminSecurityService adminSecurityService) {
        if (!adminSecurityService.canPerformAction(admin.id())) {
            ctx.status(429).json(SharedErrorResponse.rateLimited(
                "Too many admin actions. Please wait before trying again."));
            return false;
        }
        return true;
    }

    // =========================================================================
    // CSRF Protection
    // =========================================================================

    /**
     * Validates CSRF token for state-changing operations.
     *
     * <p>Should be called at the beginning of POST/PUT/DELETE handlers.
     *
     * @param ctx Javalin request context
     * @param csrfService CSRF protection service
     * @param operationName Name of operation for logging (e.g., "screenname deletion")
     * @return true if valid, false if invalid (response already sent)
     */
    public static boolean validateCsrf(Context ctx, CsrfProtectionService csrfService, String operationName) {
        try {
            csrfService.requireValidCsrfToken(ctx);
            return true;
        } catch (CsrfProtectionService.CsrfValidationException e) {
            LoggerUtil.warn("CSRF validation failed for " + operationName + ": " + e.getMessage());
            ctx.status(403).json(SharedErrorResponse.csrfFailed("Invalid or missing CSRF token"));
            return false;
        }
    }

    // =========================================================================
    // Client Information Extraction
    // =========================================================================

    /**
     * Extracts client IP address from request, handling proxy headers.
     *
     * <p>Checks in order:
     * <ol>
     *   <li>X-Forwarded-For header (first IP in chain)</li>
     *   <li>Direct connection IP</li>
     * </ol>
     *
     * @param ctx Javalin request context
     * @return Client IP address
     */
    public static String getClientIp(Context ctx) {
        String xForwardedFor = ctx.header("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return ctx.ip();
    }

    /**
     * Extracts client User-Agent from request.
     *
     * @param ctx Javalin request context
     * @return User-Agent header value, or null if not present
     */
    public static String getClientUserAgent(Context ctx) {
        return ctx.header("User-Agent");
    }

    /**
     * Returns client info as a convenient holder for audit logging.
     *
     * @param ctx Javalin request context
     * @return ClientInfo containing IP and User-Agent
     */
    public static ClientInfo getClientInfo(Context ctx) {
        return new ClientInfo(getClientIp(ctx), getClientUserAgent(ctx));
    }

    // =========================================================================
    // Audit Logging Helpers
    // =========================================================================

    /**
     * Logs an admin action with full context.
     *
     * @param adminAuditService Audit service
     * @param admin Admin user performing action
     * @param action Action name (e.g., "DELETE_SCREENNAME")
     * @param targetUserId Target user ID (null if not user-specific)
     * @param targetId Target entity ID (null if not applicable)
     * @param details Additional action details
     * @param ctx Javalin request context (for client info extraction)
     */
    public static void logAdminAction(AdminAuditService adminAuditService,
                                      User admin,
                                      String action,
                                      Integer targetUserId,
                                      Integer targetId,
                                      Map<String, Object> details,
                                      Context ctx) {
        adminAuditService.logAction(admin, action, targetUserId, targetId,
            details, getClientIp(ctx), getClientUserAgent(ctx));
    }

    // =========================================================================
    // Path Parameter Parsing
    // =========================================================================

    /**
     * Parses integer path parameter with error handling.
     *
     * @param ctx Javalin request context
     * @param paramName Path parameter name
     * @param entityName Human-readable entity name for error message
     * @return Optional containing parsed ID, empty if invalid (response already sent)
     */
    public static Optional<Integer> parsePathParamId(Context ctx, String paramName, String entityName) {
        try {
            return Optional.of(Integer.parseInt(ctx.pathParam(paramName)));
        } catch (NumberFormatException e) {
            ctx.status(400).json(SharedErrorResponse.badRequest("Invalid " + entityName + " ID"));
            return Optional.empty();
        }
    }

    // =========================================================================
    // Supporting Records
    // =========================================================================

    /**
     * Holder for client connection information.
     */
    public record ClientInfo(String ip, String userAgent) {}
}
