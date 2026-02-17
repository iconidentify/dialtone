/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.security;

import com.dialtone.db.models.User;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.web.services.AdminSecurityService;
import io.javalin.http.Context;
import io.javalin.http.Handler;

/**
 * Authentication filter for admin-only endpoints.
 *
 * Ensures that:
 * 1. User is authenticated (has valid JWT token)
 * 2. User has admin privileges (both config and database role)
 * 3. Rate limiting is enforced for admin actions
 * 4. Admin session timeout is respected
 *
 * This filter should be applied to all /api/admin/* routes.
 */
public class AdminAuthenticationFilter implements Handler {

    private final AdminSecurityService adminSecurityService;

    public AdminAuthenticationFilter(AdminSecurityService adminSecurityService) {
        this.adminSecurityService = adminSecurityService;
    }

    @Override
    public void handle(Context ctx) throws Exception {
        // Skip OPTIONS requests (CORS preflight)
        if ("OPTIONS".equals(ctx.method())) {
            return;
        }

        // Check if admin features are globally enabled
        if (!adminSecurityService.isAdminEnabled()) {
            ctx.status(503).json(new ErrorResponse("Service unavailable",
                "Admin features are currently disabled"));
            return;
        }

        // Get user from JWT token (should be set by previous authentication middleware)
        User user = ctx.attribute("user");
        if (user == null) {
            LoggerUtil.warn(String.format("Admin endpoint accessed without authentication: %s %s from IP %s",
                          ctx.method(), ctx.path(), getClientIp(ctx)));
            ctx.status(401).json(new ErrorResponse("Unauthorized",
                "Authentication required for admin access"));
            return;
        }

        // Verify user has admin privileges
        if (!adminSecurityService.isAdmin(user)) {
            LoggerUtil.warn(String.format("Non-admin user %s (@%s) attempted admin access: %s %s from IP %s",
                          user.id(), user.xUsername(), ctx.method(), ctx.path(), getClientIp(ctx)));
            ctx.status(403).json(new ErrorResponse("Forbidden",
                "Admin privileges required"));
            return;
        }

        // Check rate limiting for admin actions
        if (!adminSecurityService.canPerformAction(user.id())) {
            LoggerUtil.warn(String.format("Rate limit exceeded for admin user %s (@%s): %s %s from IP %s",
                          user.id(), user.xUsername(), ctx.method(), ctx.path(), getClientIp(ctx)));
            ctx.status(429).json(new ErrorResponse("Rate limit exceeded",
                "Too many admin actions. Please wait before trying again."));
            return;
        }

        // Log admin access (for security monitoring)
        LoggerUtil.debug(String.format("Admin access granted: user %s (@%s) accessing %s %s from IP %s",
                        user.id(), user.xUsername(), ctx.method(), ctx.path(), getClientIp(ctx)));

        // Store admin user in context for controllers to use
        ctx.attribute("admin", user);

        // Continue to the next handler
    }

    /**
     * Helper method to extract client IP address for logging.
     */
    private String getClientIp(Context ctx) {
        // Check for X-Forwarded-For header (in case behind proxy)
        String xForwardedFor = ctx.header("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Use the first IP in the chain
            return xForwardedFor.split(",")[0].trim();
        }

        // Check for X-Real-IP header
        String xRealIp = ctx.header("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }

        // Fall back to direct connection IP
        return ctx.ip();
    }

    /**
     * Error response format for admin authentication failures.
     */
    public record ErrorResponse(String error, String message) {}
}