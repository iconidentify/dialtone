/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.api;

import com.dialtone.db.models.Screenname;
import com.dialtone.db.models.ScreennamePreferences;
import com.dialtone.db.models.User;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.web.security.CsrfProtectionService;
import com.dialtone.web.services.AdminAuditService;
import com.dialtone.web.services.AdminSecurityService;
import com.dialtone.web.services.ScreennamePreferencesService;
import com.dialtone.web.services.ScreennameService;
import com.dialtone.web.services.UserService;
import io.javalin.http.Context;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.dialtone.web.api.AdminControllerUtils.*;

/**
 * Admin controller for screenname management operations.
 *
 * Provides admin-only endpoints for managing screennames across all users:
 * - Listing all screennames with user information
 * - Deleting specific screennames
 * - Resetting screenname passwords
 * - Bulk screenname operations
 *
 * All operations require admin authentication and are audit logged.
 */
public class AdminScreennameController {
    private final ScreennameService screennameService;
    private final ScreennamePreferencesService preferencesService;
    private final UserService userService;
    private final AdminSecurityService adminSecurityService;
    private final AdminAuditService adminAuditService;
    private final CsrfProtectionService csrfService;

    public AdminScreennameController(ScreennameService screennameService,
                                   ScreennamePreferencesService preferencesService,
                                   UserService userService,
                                   AdminSecurityService adminSecurityService,
                                   AdminAuditService adminAuditService,
                                   CsrfProtectionService csrfService) {
        this.screennameService = screennameService;
        this.preferencesService = preferencesService;
        this.userService = userService;
        this.adminSecurityService = adminSecurityService;
        this.adminAuditService = adminAuditService;
        this.csrfService = csrfService;
    }

    /**
     * Lists all screennames across all users with pagination.
     * GET /api/admin/screennames
     * Query params: limit, offset, user_id
     */
    public void listScreennames(Context ctx) {
        try {
            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            // Parse query parameters
            String limitParam = ctx.queryParam("limit");
            String offsetParam = ctx.queryParam("offset");
            String userIdParam = ctx.queryParam("user_id");

            int limit = Math.min(Math.max(Integer.parseInt(limitParam != null ? limitParam : "50"), 1), 200);
            int offset = Math.max(Integer.parseInt(offsetParam != null ? offsetParam : "0"), 0);
            Integer userId = null;

            if (userIdParam != null && !userIdParam.trim().isEmpty()) {
                try {
                    userId = Integer.parseInt(userIdParam);
                } catch (NumberFormatException e) {
                    ctx.status(400).json(SharedErrorResponse.badRequest("Invalid user_id parameter"));
                    return;
                }
            }

            // Get screennames with user information
            List<ScreennameWithUser> screennames = getAllScreennamesWithUser(limit, offset, userId);
            int totalCount = getScreennameCount(userId);

            ScreennamesListResponse response = new ScreennamesListResponse(screennames, totalCount, limit, offset);
            ctx.json(response);

            LoggerUtil.debug(String.format("Admin %s listed %d screennames (limit=%d, offset=%d, user_id=%s)",
                           admin.xUsername(), screennames.size(), limit, offset, userId));

        } catch (NumberFormatException e) {
            ctx.status(400).json(SharedErrorResponse.badRequest("Invalid limit or offset parameter"));
        } catch (Exception e) {
            LoggerUtil.error("Failed to list screennames: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve screennames"));
        }
    }

    /**
     * Deletes a specific screenname.
     * DELETE /api/admin/screennames/{id}
     */
    public void deleteScreenname(Context ctx) {
        try {
            if (!validateCsrf(ctx, csrfService, "screenname deletion")) return;

            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            // Parse screenname ID
            Optional<Integer> screennameIdOpt = parsePathParamId(ctx, "id", "screenname");
            if (screennameIdOpt.isEmpty()) return;
            int screennameId = screennameIdOpt.get();

            // Get screenname details before deletion for audit log
            Screenname targetScreenname = getScreennameById(screennameId);
            if (targetScreenname == null) {
                ctx.status(404).json(SharedErrorResponse.notFound("Screenname not found"));
                return;
            }

            User targetUser = userService.getUserById(targetScreenname.userId());

            // Check if this would leave the user with no screennames
            List<Screenname> userScreennames = userService.getScreennamesForUser(targetScreenname.userId());
            boolean isLastScreenname = userScreennames.size() == 1;

            if (isLastScreenname) {
                ctx.status(400).json(SharedErrorResponse.badRequest(
                    "Cannot delete user's last remaining screenname. Delete the user instead."));
                return;
            }

            // Delete the screenname using the admin method (bypasses ownership checks)
            screennameService.deleteScreennameAdmin(screennameId);

            ctx.json(new DeleteResponse("Screenname deleted successfully", screennameId, targetScreenname.screenname()));

            // Audit log
            Map<String, Object> details = Map.of(
                "deleted_screenname", targetScreenname.screenname(),
                "target_user_id", targetScreenname.userId(),
                "target_username", targetUser != null ? targetUser.xUsername() : "unknown",
                "was_primary", targetScreenname.isPrimary()
            );
            logAdminAction(adminAuditService, admin, "DELETE_SCREENNAME", targetScreenname.userId(), screennameId, details, ctx);

            LoggerUtil.info(String.format("Admin %s deleted screenname '%s' (id=%d) for user %d",
                          admin.xUsername(), targetScreenname.screenname(), screennameId, targetScreenname.userId()));

        } catch (Exception e) {
            LoggerUtil.error("Failed to delete screenname: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to delete screenname"));
        }
    }

    /**
     * Resets a screenname's password.
     * PUT /api/admin/screennames/{id}/password
     * Body: {"password": "newpassword"}
     */
    public void resetScreennamePassword(Context ctx) {
        try {
            if (!validateCsrf(ctx, csrfService, "password reset")) return;

            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            // Parse screenname ID
            Optional<Integer> screennameIdOpt = parsePathParamId(ctx, "id", "screenname");
            if (screennameIdOpt.isEmpty()) return;
            int screennameId = screennameIdOpt.get();

            // Parse request body
            ResetPasswordRequest request = ctx.bodyAsClass(ResetPasswordRequest.class);
            if (request.password == null || request.password.trim().isEmpty()) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Password is required"));
                return;
            }

            // Get screenname details before update
            Screenname targetScreenname = getScreennameById(screennameId);
            if (targetScreenname == null) {
                ctx.status(404).json(SharedErrorResponse.notFound("Screenname not found"));
                return;
            }

            User targetUser = userService.getUserById(targetScreenname.userId());

            // Update password using admin bypass (direct SQL operation)
            resetPasswordDirectly(screennameId, request.password);

            ctx.json(new PasswordResetResponse("Password reset successfully", screennameId, targetScreenname.screenname()));

            // Audit log
            Map<String, Object> details = Map.of(
                "target_screenname", targetScreenname.screenname(),
                "target_user_id", targetScreenname.userId(),
                "target_username", targetUser != null ? targetUser.xUsername() : "unknown"
            );
            logAdminAction(adminAuditService, admin, "RESET_SCREENNAME_PASSWORD", targetScreenname.userId(), screennameId, details, ctx);

            LoggerUtil.info(String.format("Admin %s reset password for screenname '%s' (id=%d) for user %d",
                          admin.xUsername(), targetScreenname.screenname(), screennameId, targetScreenname.userId()));

        } catch (Exception e) {
            LoggerUtil.error("Failed to reset screenname password: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to reset password"));
        }
    }

    /**
     * Gets all screennames with their user information.
     */
    private List<ScreennameWithUser> getAllScreennamesWithUser(int limit, int offset, Integer userId) throws SQLException {
        StringBuilder sqlBuilder = new StringBuilder("""
            SELECT s.id, s.user_id, s.screenname, s.is_primary, s.created_at,
                   u.x_username, u.x_display_name, u.is_active
            FROM screennames s
            JOIN users u ON s.user_id = u.id
        """);

        List<Object> parameters = new ArrayList<>();

        if (userId != null) {
            sqlBuilder.append(" WHERE s.user_id = ?");
            parameters.add(userId);
        }

        sqlBuilder.append(" ORDER BY s.created_at DESC LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        List<ScreennameWithUser> screennames = new ArrayList<>();

        try {
            // If filtering by user ID, use existing service methods
            if (userId != null) {
                User user = userService.getUserById(userId);
                if (user != null) {
                    List<Screenname> userScreennames = userService.getScreennamesForUser(userId);
                    for (Screenname screenname : userScreennames) {
                        screennames.add(new ScreennameWithUser(
                            screenname.id(),
                            screenname.screenname(),
                            screenname.isPrimary(),
                            screenname.createdAt(),
                            user.id(),
                            user.authProvider(),
                            user.xUsername(),
                            user.xDisplayName(),
                            user.discordUsername(),
                            user.discordDisplayName(),
                            user.email(),
                            user.isActive()
                        ));
                    }
                }
            } else {
                // For all screennames, we'll implement this differently
                List<User> allUsers = userService.getAllUsers(1000, 0, false);
                int currentOffset = 0;
                int collected = 0;

                for (User user : allUsers) {
                    if (collected >= limit) break;

                    List<Screenname> userScreennames = userService.getScreennamesForUser(user.id());
                    for (Screenname screenname : userScreennames) {
                        if (currentOffset >= offset && collected < limit) {
                            screennames.add(new ScreennameWithUser(
                                screenname.id(),
                                screenname.screenname(),
                                screenname.isPrimary(),
                                screenname.createdAt(),
                                user.id(),
                                user.authProvider(),
                                user.xUsername(),
                                user.xDisplayName(),
                                user.discordUsername(),
                                user.discordDisplayName(),
                                user.email(),
                                user.isActive()
                            ));
                            collected++;
                        }
                        currentOffset++;
                    }
                }
            }
        } catch (Exception e) {
            LoggerUtil.error("Failed to get screennames with user info: " + e.getMessage());
        }

        return screennames;
    }

    /**
     * Gets total count of screennames.
     */
    private int getScreennameCount(Integer userId) throws SQLException {
        if (userId != null) {
            List<Screenname> userScreennames = userService.getScreennamesForUser(userId);
            return userScreennames.size();
        } else {
            // Count all screennames across all users
            List<User> allUsers = userService.getAllUsers(1000, 0, false);
            int count = 0;
            for (User user : allUsers) {
                List<Screenname> userScreennames = userService.getScreennamesForUser(user.id());
                count += userScreennames.size();
            }
            return count;
        }
    }

    /**
     * Gets a screenname by ID.
     */
    private Screenname getScreennameById(int screennameId) throws SQLException {
        List<User> allUsers = userService.getAllUsers(1000, 0, false);

        for (User user : allUsers) {
            List<Screenname> userScreennames = userService.getScreennamesForUser(user.id());
            for (Screenname screenname : userScreennames) {
                if (screenname.id() == screennameId) {
                    return screenname;
                }
            }
        }

        return null;
    }


    /**
     * Resets a screenname password directly (admin bypass).
     */
    private void resetPasswordDirectly(int screennameId, String newPassword) throws SQLException {
        throw new SQLException("Direct password reset requires database manager access - not implemented in this prototype");
    }

    /**
     * Gets preferences for any screenname (admin access).
     * GET /api/admin/screennames/{id}/preferences
     */
    public void getPreferencesAdmin(Context ctx) {
        try {
            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            // Parse screenname ID
            Optional<Integer> screennameIdOpt = parsePathParamId(ctx, "id", "screenname");
            if (screennameIdOpt.isEmpty()) return;
            int screennameId = screennameIdOpt.get();

            // Verify screenname exists
            if (!preferencesService.screennameExists(screennameId)) {
                ctx.status(404).json(SharedErrorResponse.notFound("Screenname not found"));
                return;
            }

            // Get preferences (returns defaults if no row exists)
            ScreennamePreferences preferences = preferencesService.getPreferences(screennameId);
            ctx.json(preferences.toResponse());

            LoggerUtil.debug(String.format("Admin %s viewed preferences for screenname %d",
                           admin.xUsername(), screennameId));

        } catch (ScreennamePreferencesService.ScreennamePreferencesServiceException e) {
            LoggerUtil.error("Failed to get preferences: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve preferences"));

        } catch (Exception e) {
            LoggerUtil.error("Unexpected error getting preferences: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve preferences"));
        }
    }

    /**
     * Updates preferences for any screenname (admin access).
     * PUT /api/admin/screennames/{id}/preferences
     * Body: {"lowColorMode": true}
     */
    public void updatePreferencesAdmin(Context ctx) {
        try {
            if (!validateCsrf(ctx, csrfService, "preferences update")) return;

            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            // Parse screenname ID
            Optional<Integer> screennameIdOpt = parsePathParamId(ctx, "id", "screenname");
            if (screennameIdOpt.isEmpty()) return;
            int screennameId = screennameIdOpt.get();

            // Verify screenname exists and get details for audit
            Screenname targetScreenname = getScreennameById(screennameId);
            if (targetScreenname == null) {
                ctx.status(404).json(SharedErrorResponse.notFound("Screenname not found"));
                return;
            }

            // Parse request body
            UpdatePreferencesRequest request = ctx.bodyAsClass(UpdatePreferencesRequest.class);
            if (request.lowColorMode == null) {
                ctx.status(400).json(SharedErrorResponse.badRequest("lowColorMode is required"));
                return;
            }

            // Get previous preference for audit log
            ScreennamePreferences previousPrefs = preferencesService.getPreferences(screennameId);
            boolean previousLowColorMode = previousPrefs.isLowColorModeEnabled();

            // Update preferences
            ScreennamePreferences updated = preferencesService.updatePreferences(
                screennameId, request.lowColorMode);

            ctx.json(updated.toResponse());

            // Audit log
            Map<String, Object> details = Map.of(
                "target_screenname", targetScreenname.screenname(),
                "previous_low_color_mode", previousLowColorMode,
                "new_low_color_mode", request.lowColorMode
            );
            logAdminAction(adminAuditService, admin, "UPDATE_SCREENNAME_PREFERENCES",
                          targetScreenname.userId(), screennameId, details, ctx);

            LoggerUtil.info(String.format("Admin %s updated preferences for screenname '%s' (id=%d): lowColorMode=%s",
                          admin.xUsername(), targetScreenname.screenname(), screennameId, request.lowColorMode));

        } catch (ScreennamePreferencesService.ScreennamePreferencesServiceException e) {
            LoggerUtil.warn("Preferences update failed: " + e.getMessage());
            ctx.status(400).json(SharedErrorResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            LoggerUtil.error("Unexpected error updating preferences: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to update preferences"));
        }
    }

    // Request/Response DTOs
    public static class ResetPasswordRequest {
        public String password;
    }

    public static class UpdatePreferencesRequest {
        public Boolean lowColorMode;
    }

    public record ScreennameWithUser(
        int id,
        String screenname,
        boolean isPrimary,
        LocalDateTime createdAt,
        int userId,
        String userAuthProvider,
        String userXUsername,
        String userXDisplayName,
        String userDiscordUsername,
        String userDiscordDisplayName,
        String userEmail,
        boolean userIsActive
    ) {}

    public record ScreennamesListResponse(List<ScreennameWithUser> screennames, int totalCount, int limit, int offset) {}

    public record DeleteResponse(String message, int deletedScreennameId, String deletedScreenname) {}

    public record PasswordResetResponse(String message, int screennameId, String screenname) {}
}
