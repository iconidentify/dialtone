/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.api;

import com.dialtone.db.models.User;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.web.security.CsrfProtectionService;
import com.dialtone.web.services.AdminAuditService;
import com.dialtone.web.services.AdminSecurityService;
import com.dialtone.web.services.AolMetricsService;
import com.dialtone.web.services.UserService;
import io.javalin.http.Context;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.dialtone.web.api.AdminControllerUtils.*;

/**
 * Admin controller for system management and monitoring.
 *
 * Provides admin-only endpoints for:
 * - System health and statistics
 * - Admin role management (granting/revoking admin privileges)
 * - System configuration viewing
 * - Server health monitoring
 *
 * All operations require admin authentication and are audit logged.
 */
public class AdminSystemController {
    private final UserService userService;
    private final AdminSecurityService adminSecurityService;
    private final AdminAuditService adminAuditService;
    private final CsrfProtectionService csrfService;
    private final AolMetricsService aolMetricsService;

    public AdminSystemController(UserService userService,
                                AdminSecurityService adminSecurityService,
                                AdminAuditService adminAuditService,
                                CsrfProtectionService csrfService,
                                AolMetricsService aolMetricsService) {
        this.userService = userService;
        this.adminSecurityService = adminSecurityService;
        this.adminAuditService = adminAuditService;
        this.csrfService = csrfService;
        this.aolMetricsService = aolMetricsService;
    }

    /**
     * Gets system statistics and health information.
     * GET /api/admin/system/stats
     */
    public void getSystemStats(Context ctx) {
        try {
            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            // Collect system statistics
            Map<String, Object> stats = new HashMap<>();

            // User statistics
            try {
                int totalUsers = userService.getUserCount(false);
                int activeUsers = userService.getUserCount(true);
                stats.put("totalUsers", totalUsers);
                stats.put("activeUsers", activeUsers);
                stats.put("inactiveUsers", totalUsers - activeUsers);
            } catch (SQLException e) {
                LoggerUtil.error("Failed to get user statistics: " + e.getMessage());
                stats.put("userStatsError", e.getMessage());
            }

            // Screenname statistics
            try {
                int totalScreennames = userService.getScreennameCount();
                stats.put("totalScreennames", totalScreennames);
            } catch (Exception e) {
                LoggerUtil.error("Failed to get screenname statistics: " + e.getMessage());
                stats.put("screennameStatsError", e.getMessage());
            }

            // Admin statistics
            try {
                Set<String> configuredAdmins = adminSecurityService.getConfiguredAdminUsernames();
                stats.put("configuredAdminCount", configuredAdmins.size());
                stats.put("configuredAdmins", configuredAdmins);
                stats.put("adminEnabled", adminSecurityService.isAdminEnabled());
                stats.put("adminSessionTimeout", adminSecurityService.getAdminSessionTimeoutMinutes());
                stats.put("adminMaxScreennames", adminSecurityService.getMaxScreennamesForAdmin());
            } catch (Exception e) {
                LoggerUtil.error("Failed to get admin statistics: " + e.getMessage());
                stats.put("adminStatsError", e.getMessage());
            }

            // Audit log statistics
            try {
                Map<String, Object> auditStats = adminAuditService.getAuditStatistics();
                stats.put("auditLogStats", auditStats);
                stats.put("auditLogSize", adminAuditService.getAuditLogCount(null, null));
            } catch (Exception e) {
                LoggerUtil.error("Failed to get audit statistics: " + e.getMessage());
                stats.put("auditStatsError", e.getMessage());
            }

            // System health indicators
            stats.put("systemHealth", "operational");
            stats.put("timestamp", System.currentTimeMillis());

            SystemStatsResponse response = new SystemStatsResponse(stats);
            ctx.json(response);

            LoggerUtil.debug(String.format("Admin %s viewed system statistics", admin.xUsername()));

        } catch (Exception e) {
            LoggerUtil.error("Failed to get system statistics: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve system statistics"));
        }
    }

    /**
     * Gets AOL server operational metrics.
     * GET /api/admin/aol/metrics
     */
    public void getAolMetrics(Context ctx) {
        try {
            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            Map<String, Object> metrics = aolMetricsService.getAolMetrics();

            AolMetricsResponse response = new AolMetricsResponse(metrics);
            ctx.json(response);

            LoggerUtil.debug(String.format("Admin %s viewed AOL metrics", admin.xUsername()));

        } catch (Exception e) {
            LoggerUtil.error("Failed to get AOL metrics: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve AOL metrics"));
        }
    }

    /**
     * Gets system health check information.
     * GET /api/admin/system/health
     */
    public void getSystemHealth(Context ctx) {
        try {
            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            Map<String, Object> health = new HashMap<>();

            // Database health
            try {
                userService.getUserCount(true); // Simple DB operation to test connectivity
                health.put("database", Map.of(
                    "status", "healthy",
                    "lastChecked", System.currentTimeMillis()
                ));
            } catch (Exception e) {
                health.put("database", Map.of(
                    "status", "error",
                    "error", e.getMessage(),
                    "lastChecked", System.currentTimeMillis()
                ));
            }

            // Admin services health
            health.put("adminServices", Map.of(
                "adminEnabled", adminSecurityService.isAdminEnabled(),
                "auditLogSize", adminAuditService.getAuditLogCount(null, null)
            ));

            // System resources
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            health.put("systemResources", Map.of(
                "maxMemoryMB", maxMemory / (1024 * 1024),
                "totalMemoryMB", totalMemory / (1024 * 1024),
                "usedMemoryMB", usedMemory / (1024 * 1024),
                "freeMemoryMB", freeMemory / (1024 * 1024),
                "memoryUsagePercent", (usedMemory * 100) / totalMemory
            ));

            health.put("overallStatus", "healthy");
            health.put("timestamp", System.currentTimeMillis());

            SystemHealthResponse response = new SystemHealthResponse(health);
            ctx.json(response);

            LoggerUtil.debug(String.format("Admin %s viewed system health", admin.xUsername()));

        } catch (Exception e) {
            LoggerUtil.error("Failed to get system health: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve system health"));
        }
    }

    /**
     * Grants admin role to a user.
     * POST /api/admin/roles/{userId}/grant
     */
    public void grantAdminRole(Context ctx) {
        try {
            if (!validateCsrf(ctx, csrfService, "grant admin role")) return;

            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            // Parse user ID
            Optional<Integer> targetUserIdOpt = parsePathParamId(ctx, "userId", "user");
            if (targetUserIdOpt.isEmpty()) return;
            int targetUserId = targetUserIdOpt.get();

            // Verify target user exists
            User targetUser = userService.getUserById(targetUserId);
            if (targetUser == null) {
                ctx.status(404).json(SharedErrorResponse.notFound("User not found"));
                return;
            }

            // Check if user already has admin role
            boolean alreadyAdmin = adminSecurityService.hasAdminRole(targetUserId);
            if (alreadyAdmin) {
                ctx.status(400).json(SharedErrorResponse.badRequest("User already has admin role"));
                return;
            }

            // Grant admin role
            adminSecurityService.grantAdminRole(targetUserId, admin.id());

            RoleResponse response = new RoleResponse("Admin role granted successfully", targetUserId, targetUser.xUsername(), "admin");
            ctx.json(response);

            // Audit log
            Map<String, Object> details = Map.of(
                "target_user_id", targetUserId,
                "target_username", targetUser.xUsername(),
                "role_granted", "admin"
            );
            logAdminAction(adminAuditService, admin, "GRANT_ADMIN_ROLE", targetUserId, null, details, ctx);

            LoggerUtil.info(String.format("Admin %s granted admin role to user %d (@%s)",
                          admin.xUsername(), targetUserId, targetUser.xUsername()));

        } catch (SQLException e) {
            LoggerUtil.error("Failed to grant admin role: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to grant admin role"));

        } catch (Exception e) {
            LoggerUtil.error("Failed to grant admin role: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to grant admin role"));
        }
    }

    /**
     * Revokes admin role from a user.
     * DELETE /api/admin/roles/{userId}
     */
    public void revokeAdminRole(Context ctx) {
        try {
            if (!validateCsrf(ctx, csrfService, "revoke admin role")) return;

            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            // Parse user ID
            Optional<Integer> targetUserIdOpt = parsePathParamId(ctx, "userId", "user");
            if (targetUserIdOpt.isEmpty()) return;
            int targetUserId = targetUserIdOpt.get();

            // Check if admin is trying to revoke their own role
            if (admin.id() == targetUserId) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Cannot revoke your own admin role"));
                return;
            }

            // Verify target user exists
            User targetUser = userService.getUserById(targetUserId);
            if (targetUser == null) {
                ctx.status(404).json(SharedErrorResponse.notFound("User not found"));
                return;
            }

            // Check if user has admin role
            boolean hasAdminRole = adminSecurityService.hasAdminRole(targetUserId);
            if (!hasAdminRole) {
                ctx.status(400).json(SharedErrorResponse.badRequest("User does not have admin role"));
                return;
            }

            // Revoke admin role
            adminSecurityService.revokeAdminRole(targetUserId);

            RoleResponse response = new RoleResponse("Admin role revoked successfully", targetUserId, targetUser.xUsername(), "user");
            ctx.json(response);

            // Audit log
            Map<String, Object> details = Map.of(
                "target_user_id", targetUserId,
                "target_username", targetUser.xUsername(),
                "role_revoked", "admin"
            );
            logAdminAction(adminAuditService, admin, "REVOKE_ADMIN_ROLE", targetUserId, null, details, ctx);

            LoggerUtil.info(String.format("Admin %s revoked admin role from user %d (@%s)",
                          admin.xUsername(), targetUserId, targetUser.xUsername()));

        } catch (SQLException e) {
            LoggerUtil.error("Failed to revoke admin role: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to revoke admin role"));

        } catch (Exception e) {
            LoggerUtil.error("Failed to revoke admin role: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to revoke admin role"));
        }
    }

    // Response DTOs
    public record SystemStatsResponse(Map<String, Object> statistics) {}

    public record SystemHealthResponse(Map<String, Object> health) {}

    public record AolMetricsResponse(Map<String, Object> metrics) {}

    public record RoleResponse(String message, int userId, String username, String newRole) {}
}
