/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.api;

import com.dialtone.db.models.Screenname;
import com.dialtone.db.models.User;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.web.security.CsrfProtectionService;
import com.dialtone.web.services.AdminAuditService;
import com.dialtone.web.services.AdminSecurityService;
import com.dialtone.web.services.ScreennameService;
import com.dialtone.web.services.UserService;
import io.javalin.http.Context;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.dialtone.web.api.AdminControllerUtils.*;

/**
 * Admin controller for user management operations.
 *
 * Provides admin-only endpoints for managing users, including:
 * - Listing all users with pagination
 * - Getting detailed user information
 * - Enabling/disabling user accounts
 * - Deleting users and associated data
 * - Managing user screennames
 *
 * All operations require admin authentication and are audit logged.
 */
public class AdminUserController {
    private final UserService userService;
    private final ScreennameService screennameService;
    private final AdminSecurityService adminSecurityService;
    private final AdminAuditService adminAuditService;
    private final CsrfProtectionService csrfService;

    public AdminUserController(UserService userService,
                              ScreennameService screennameService,
                              AdminSecurityService adminSecurityService,
                              AdminAuditService adminAuditService,
                              CsrfProtectionService csrfService) {
        this.userService = userService;
        this.screennameService = screennameService;
        this.adminSecurityService = adminSecurityService;
        this.adminAuditService = adminAuditService;
        this.csrfService = csrfService;
    }

    /**
     * Lists all users with pagination and filtering.
     * GET /api/admin/users
     * Query params: limit, offset, active_only
     */
    public void listUsers(Context ctx) {
        try {
            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            // Parse query parameters
            String limitParam = ctx.queryParam("limit");
            String offsetParam = ctx.queryParam("offset");
            String activeOnlyParam = ctx.queryParam("active_only");

            int limit = Math.min(Math.max(Integer.parseInt(limitParam != null ? limitParam : "20"), 1), 100);
            int offset = Math.max(Integer.parseInt(offsetParam != null ? offsetParam : "0"), 0);
            boolean activeOnly = Boolean.parseBoolean(activeOnlyParam != null ? activeOnlyParam : "false");

            // Get users and total count
            List<User> users = userService.getAllUsers(limit, offset, activeOnly);
            int totalCount = userService.getUserCount(activeOnly);

            // Create response with user details and screenname counts
            List<UserWithDetails> userDetails = users.stream()
                .map(user -> {
                    try {
                        List<Screenname> screennames = userService.getScreennamesForUser(user.id());
                        boolean hasAdminRole = adminSecurityService.hasAdminRole(user.id());
                        return new UserWithDetails(user, screennames.size(), hasAdminRole);
                    } catch (Exception e) {
                        LoggerUtil.error(String.format("Failed to get details for user %d: %s", user.id(), e.getMessage()));
                        return new UserWithDetails(user, 0, false);
                    }
                })
                .toList();

            UsersListResponse response = new UsersListResponse(userDetails, totalCount, limit, offset);
            ctx.json(response);

            LoggerUtil.debug(String.format("Admin %s listed %d users (limit=%d, offset=%d)",
                           admin.xUsername(), users.size(), limit, offset));

        } catch (NumberFormatException e) {
            ctx.status(400).json(SharedErrorResponse.badRequest("Invalid limit or offset parameter"));
        } catch (Exception e) {
            LoggerUtil.error("Failed to list users: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve users"));
        }
    }

    /**
     * Creates a new user manually.
     * POST /api/admin/users
     */
    public void createUser(Context ctx) {
        try {
            if (!validateCsrf(ctx, csrfService, "user creation")) return;

            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            CreateUserRequest request = ctx.bodyAsClass(CreateUserRequest.class);
            if (request == null) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Request body is required"));
                return;
            }

            boolean requestedScreenname = request.screenname != null && !request.screenname.trim().isEmpty();
            if (requestedScreenname &&
                (request.screennamePassword == null || request.screennamePassword.trim().isEmpty())) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Screenname password is required when providing a screenname"));
                return;
            }

            boolean isActive = request.isActive == null || request.isActive;
            String resolvedDisplayName = (request.displayName != null && !request.displayName.trim().isEmpty())
                ? request.displayName.trim()
                : (requestedScreenname ? request.screenname.trim() : request.xUsername);

            User createdUser = userService.createManualUser(
                request.xUserId,
                request.xUsername,
                resolvedDisplayName,
                isActive
            );

            Screenname createdScreenname = null;
            if (requestedScreenname) {
                try {
                    createdScreenname = screennameService.createScreenname(
                        createdUser.id(),
                        request.screenname.trim(),
                        request.screennamePassword.trim()
                    );
                } catch (ScreennameService.ScreennameServiceException e) {
                    try {
                        userService.deleteUser(createdUser.id());
                    } catch (Exception cleanupEx) {
                        LoggerUtil.error("Failed to cleanup user after screenname creation error: " + cleanupEx.getMessage());
                    }
                    ctx.status(400).json(SharedErrorResponse.badRequest(e.getMessage()));
                    return;
                }
            }

            boolean grantAdminRole = request.grantAdminRole != null && request.grantAdminRole;
            boolean grantedAdminRole = false;
            if (grantAdminRole) {
                try {
                    adminSecurityService.grantAdminRole(createdUser.id(), admin.id());
                    grantedAdminRole = true;
                } catch (SQLException e) {
                    LoggerUtil.error("Failed to grant admin role to user " + createdUser.id() + ": " + e.getMessage());
                    ctx.status(500).json(SharedErrorResponse.serverError("Failed to grant admin role"));
                    return;
                }
            }

            int screennameCount = createdScreenname != null ? 1 : 0;
            UserWithDetails responseUser = new UserWithDetails(createdUser, screennameCount, grantedAdminRole);
            ctx.status(201).json(new CreateUserResponse("User created successfully", responseUser));

            Map<String, Object> details = new HashMap<>();
            details.put("x_username", createdUser.xUsername());
            details.put("x_display_name", createdUser.xDisplayName());
            details.put("granted_admin_role", grantedAdminRole);
            details.put("is_active", createdUser.isActive());
            if (createdScreenname != null) {
                details.put("screenname", createdScreenname.screenname());
            }

            logAdminAction(adminAuditService, admin, "CREATE_USER", createdUser.id(), null, details, ctx);

        } catch (UserService.UserServiceException e) {
            ctx.status(400).json(SharedErrorResponse.badRequest(e.getMessage()));
        } catch (Exception e) {
            LoggerUtil.error("Failed to create user: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to create user"));
        }
    }

    /**
     * Gets detailed information about a specific user.
     * GET /api/admin/users/{id}
     */
    public void getUserDetails(Context ctx) {
        try {
            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            Optional<Integer> userIdOpt = parsePathParamId(ctx, "id", "user");
            if (userIdOpt.isEmpty()) return;
            int userId = userIdOpt.get();

            // Get user details
            User user = userService.getUserById(userId);
            if (user == null) {
                ctx.status(404).json(SharedErrorResponse.notFound("User not found"));
                return;
            }

            List<Screenname> screennames = userService.getScreennamesForUser(userId);
            boolean hasAdminRole = adminSecurityService.hasAdminRole(userId);

            UserDetailResponse response = new UserDetailResponse(user, screennames, hasAdminRole);
            ctx.json(response);

            // Audit log
            adminAuditService.logUserAction(admin, "VIEW_USER_DETAILS", userId,
                                          getClientIp(ctx), getClientUserAgent(ctx));

        } catch (Exception e) {
            LoggerUtil.error("Failed to get user details: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve user details"));
        }
    }

    /**
     * Resets password for a specific user's screenname.
     * PUT /api/admin/users/{userId}/screennames/{screennameId}/password
     */
    public void resetScreennamePassword(Context ctx) {
        try {
            if (!validateCsrf(ctx, csrfService, "password reset")) return;

            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            Optional<Integer> userIdOpt = parsePathParamId(ctx, "userId", "user");
            if (userIdOpt.isEmpty()) return;
            int userId = userIdOpt.get();

            Optional<Integer> screennameIdOpt = parsePathParamId(ctx, "screennameId", "screenname");
            if (screennameIdOpt.isEmpty()) return;
            int screennameId = screennameIdOpt.get();

            ResetPasswordRequest request = ctx.bodyAsClass(ResetPasswordRequest.class);
            if (request.password == null || request.password.trim().isEmpty()) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Password is required"));
                return;
            }

            Screenname updated = screennameService.updatePassword(screennameId, userId, request.password.trim());

            ctx.json(new PasswordResetResponse(
                "Password reset successfully",
                updated.id(),
                updated.screenname(),
                userId
            ));

            Map<String, Object> details = Map.of(
                "screenname", updated.screenname()
            );
            logAdminAction(adminAuditService, admin, "RESET_SCREENNAME_PASSWORD", userId, screennameId, details, ctx);

            LoggerUtil.info(String.format("Admin %s reset password for screenname %d (user %d)",
                admin.xUsername(), screennameId, userId));

        } catch (ScreennameService.ScreennameServiceException e) {
            ctx.status(400).json(SharedErrorResponse.badRequest(e.getMessage()));
        } catch (Exception e) {
            LoggerUtil.error("Failed to reset screenname password: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to reset password"));
        }
    }

    /**
     * Updates a user's active status (enable/disable account).
     * PUT /api/admin/users/{id}/status
     * Body: {"active": true|false}
     */
    public void updateUserStatus(Context ctx) {
        try {
            if (!validateCsrf(ctx, csrfService, "user status update")) return;

            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            Optional<Integer> userIdOpt = parsePathParamId(ctx, "id", "user");
            if (userIdOpt.isEmpty()) return;
            int userId = userIdOpt.get();

            // Check if admin is trying to modify themselves
            if (admin.id() == userId) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Cannot modify your own account status"));
                return;
            }

            // Parse request body
            UpdateStatusRequest request = ctx.bodyAsClass(UpdateStatusRequest.class);
            if (request.active == null) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Active status is required"));
                return;
            }

            // Update user status
            User updatedUser = userService.updateUserStatus(userId, request.active);

            ctx.json(new UserStatusResponse(updatedUser.id(), updatedUser.xUsername(), updatedUser.isActive()));

            // Audit log
            Map<String, Object> details = Map.of(
                "old_status", !request.active,
                "new_status", request.active
            );
            logAdminAction(adminAuditService, admin, request.active ? "ENABLE_USER" : "DISABLE_USER",
                          userId, null, details, ctx);

            LoggerUtil.info(String.format("Admin %s %s user %d (@%s)",
                          admin.xUsername(), request.active ? "enabled" : "disabled",
                          userId, updatedUser.xUsername()));

        } catch (UserService.UserServiceException e) {
            LoggerUtil.warn("User status update failed: " + e.getMessage());
            ctx.status(400).json(SharedErrorResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            LoggerUtil.error("Failed to update user status: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to update user status"));
        }
    }

    /**
     * Deletes a user and all associated data.
     * DELETE /api/admin/users/{id}
     */
    public void deleteUser(Context ctx) {
        try {
            if (!validateCsrf(ctx, csrfService, "user deletion")) return;

            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            Optional<Integer> userIdOpt = parsePathParamId(ctx, "id", "user");
            if (userIdOpt.isEmpty()) return;
            int userId = userIdOpt.get();

            // Check if admin is trying to delete themselves
            if (admin.id() == userId) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Cannot delete your own account"));
                return;
            }

            // Get user details before deletion for audit log
            User targetUser = userService.getUserById(userId);
            if (targetUser == null) {
                ctx.status(404).json(SharedErrorResponse.notFound("User not found"));
                return;
            }

            List<Screenname> screennames = userService.getScreennamesForUser(userId);

            // Delete the user
            userService.deleteUser(userId);

            ctx.json(new DeleteResponse("User deleted successfully", userId, targetUser.xUsername()));

            // Audit log
            Map<String, Object> details = new HashMap<>();
            details.put("deleted_username", targetUser.xUsername());
            details.put("deleted_display_name", targetUser.xDisplayName());
            details.put("screennames_count", screennames.size());
            details.put("screennames", screennames.stream().map(Screenname::screenname).toList());

            logAdminAction(adminAuditService, admin, "DELETE_USER", userId, null, details, ctx);

            LoggerUtil.info(String.format("Admin %s deleted user %d (@%s) with %d screennames",
                          admin.xUsername(), userId, targetUser.xUsername(), screennames.size()));

        } catch (UserService.UserServiceException e) {
            LoggerUtil.warn("User deletion failed: " + e.getMessage());
            ctx.status(400).json(SharedErrorResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            LoggerUtil.error("Failed to delete user: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to delete user"));
        }
    }

    /**
     * Gets all screennames for a specific user.
     * GET /api/admin/users/{id}/screennames
     */
    public void getUserScreennames(Context ctx) {
        try {
            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            Optional<Integer> userIdOpt = parsePathParamId(ctx, "id", "user");
            if (userIdOpt.isEmpty()) return;
            int userId = userIdOpt.get();

            // Verify user exists
            User targetUser = userService.getUserById(userId);
            if (targetUser == null) {
                ctx.status(404).json(SharedErrorResponse.notFound("User not found"));
                return;
            }

            List<Screenname> screennames = userService.getScreennamesForUser(userId);

            // Convert to response DTOs (excluding password hashes)
            List<Screenname.ScreennameResponse> responses = screennames.stream()
                .map(Screenname::toResponse)
                .toList();

            ctx.json(new UserScreennamesResponse(targetUser, responses));

            // Audit log
            adminAuditService.logUserAction(admin, "VIEW_USER_SCREENNAMES", userId,
                                          getClientIp(ctx), getClientUserAgent(ctx));

        } catch (Exception e) {
            LoggerUtil.error("Failed to get user screennames: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve user screennames"));
        }
    }

    /**
     * Creates a new screenname for a specific user.
     * POST /api/admin/users/{id}/screennames
     */
    public void createUserScreenname(Context ctx) {
        try {
            if (!validateCsrf(ctx, csrfService, "screenname creation")) return;

            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            Optional<Integer> userIdOpt = parsePathParamId(ctx, "id", "user");
            if (userIdOpt.isEmpty()) return;
            int userId = userIdOpt.get();

            User targetUser = userService.getUserById(userId);
            if (targetUser == null) {
                ctx.status(404).json(SharedErrorResponse.notFound("User not found"));
                return;
            }

            CreateScreennameRequest request = ctx.bodyAsClass(CreateScreennameRequest.class);
            if (request == null || request.screenname == null || request.screenname.trim().isEmpty()) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Screenname is required"));
                return;
            }
            if (request.password == null || request.password.trim().isEmpty()) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Password is required"));
                return;
            }

            Screenname newScreenname = screennameService.createScreenname(
                userId,
                request.screenname.trim(),
                request.password.trim()
            );

            ctx.status(201).json(new CreateScreennameResponse(
                "Screenname created successfully",
                newScreenname.toResponse()
            ));

            Map<String, Object> details = Map.of(
                "target_user_id", userId,
                "target_username", targetUser.xUsername(),
                "screenname", newScreenname.screenname()
            );
            logAdminAction(adminAuditService, admin, "CREATE_SCREENNAME", userId, newScreenname.id(), details, ctx);

            LoggerUtil.info(String.format("Admin %s created screenname %s for user %d",
                admin.xUsername(), newScreenname.screenname(), userId));

        } catch (ScreennameService.ScreennameServiceException e) {
            ctx.status(400).json(SharedErrorResponse.badRequest(e.getMessage()));
        } catch (Exception e) {
            LoggerUtil.error("Failed to create screenname: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to create screenname"));
        }
    }

    // Request/Response DTOs
    public static class UpdateStatusRequest {
        public Boolean active;
    }

    public static class CreateUserRequest {
        public String xUserId;
        public String xUsername;
        public String displayName;
        public String screenname;
        public String screennamePassword;
        public Boolean isActive;
        public Boolean grantAdminRole;
    }

    public static class ResetPasswordRequest {
        public String password;
    }

    public static class CreateScreennameRequest {
        public String screenname;
        public String password;
    }

    public record UserWithDetails(User user, int screennameCount, boolean isAdmin) {}

    public record UsersListResponse(List<UserWithDetails> users, int totalCount, int limit, int offset) {}

    public record UserDetailResponse(User user, List<Screenname> screennames, boolean isAdmin) {}

    public record UserStatusResponse(int id, String username, boolean active) {}

    public record CreateUserResponse(String message, UserWithDetails user) {}
    public record CreateScreennameResponse(String message, Screenname.ScreennameResponse screenname) {}

    public record PasswordResetResponse(String message, int screennameId, String screenname, int userId) {}

    public record UserScreennamesResponse(User user, List<Screenname.ScreennameResponse> screennames) {}

    public record DeleteResponse(String message, int deletedUserId, String deletedUsername) {}
}