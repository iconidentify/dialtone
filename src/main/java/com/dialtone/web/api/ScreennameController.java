/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.api;

import com.dialtone.db.models.Screenname;
import com.dialtone.db.models.User;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.web.security.CsrfProtectionService;
import com.dialtone.web.security.SecurityService;
import com.dialtone.web.services.ScreennameService;
import io.javalin.http.Context;

import java.util.List;
import java.util.Optional;

import static com.dialtone.web.api.AdminControllerUtils.*;

/**
 * Controller for screenname management endpoints.
 *
 * Handles CRUD operations for screennames including creation,
 * updates, password changes, and primary screenname management.
 */
public class ScreennameController {
    private final ScreennameService screennameService;
    private final CsrfProtectionService csrfService;

    public ScreennameController(ScreennameService screennameService, CsrfProtectionService csrfService) {
        this.screennameService = screennameService;
        this.csrfService = csrfService;
    }

    /**
     * Gets all screennames for the authenticated user.
     * GET /api/screennames
     */
    public void getScreennames(Context ctx) {
        try {
            Optional<User> userOpt = getAuthenticatedUser(ctx);
            if (userOpt.isEmpty()) return;
            User user = userOpt.get();

            List<Screenname> screennames = screennameService.getScreennamesForUser(user.id());

            // Convert to response DTOs (excluding password hashes)
            List<Screenname.ScreennameResponse> responses = screennames.stream()
                .map(Screenname::toResponse)
                .toList();

            ctx.json(new ScreennamesResponse(responses));

        } catch (Exception e) {
            LoggerUtil.error("Failed to get screennames: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve screennames"));
        }
    }

    /**
     * Creates a new screenname for the authenticated user.
     * POST /api/screennames
     * Body: {"screenname": "newname", "password": "password"}
     */
    public void createScreenname(Context ctx) {
        try {
            // CSRF Protection
            if (!validateCsrf(ctx, csrfService, "screenname creation")) return;

            Optional<User> userOpt = getAuthenticatedUser(ctx);
            if (userOpt.isEmpty()) return;
            User user = userOpt.get();

            // Parse request body
            CreateScreennameRequest request = ctx.bodyAsClass(CreateScreennameRequest.class);

            if (request.screenname == null || request.password == null) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Screenname and password are required"));
                return;
            }

            // Input validation and sanitization
            SecurityService.ValidationResult screennameValidation =
                SecurityService.validateScreenname(request.screenname);
            if (!screennameValidation.isValid()) {
                ctx.status(400).json(SharedErrorResponse.badRequest(screennameValidation.getErrorMessage()));
                return;
            }

            SecurityService.ValidationResult passwordValidation =
                SecurityService.validatePassword(request.password);
            if (!passwordValidation.isValid()) {
                ctx.status(400).json(SharedErrorResponse.badRequest(passwordValidation.getErrorMessage()));
                return;
            }

            // Create screenname using validated input
            Screenname newScreenname = screennameService.createScreenname(
                user.id(), screennameValidation.getValue(), passwordValidation.getValue());

            LoggerUtil.info("Created screenname '" + screennameValidation.getValue() +
                          "' for user " + user.xUsername());
            ctx.status(201).json(newScreenname.toResponse());

        } catch (ScreennameService.ScreennameServiceException e) {
            LoggerUtil.warn("Screenname creation failed: " + e.getMessage());
            ctx.status(400).json(SharedErrorResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            LoggerUtil.error("Unexpected error creating screenname: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to create screenname"));
        }
    }

    /**
     * Updates an existing screenname (changes the screenname text).
     * PUT /api/screennames/{id}
     * Body: {"screenname": "updatedname"}
     */
    public void updateScreenname(Context ctx) {
        try {
            // CSRF Protection
            if (!validateCsrf(ctx, csrfService, "screenname update")) return;

            Optional<User> userOpt = getAuthenticatedUser(ctx);
            if (userOpt.isEmpty()) return;
            User user = userOpt.get();

            // Parse screenname ID
            Optional<Integer> screennameIdOpt = parsePathParamId(ctx, "id", "screenname");
            if (screennameIdOpt.isEmpty()) return;
            int screennameId = screennameIdOpt.get();

            // Parse request body
            UpdateScreennameRequest request = ctx.bodyAsClass(UpdateScreennameRequest.class);

            if (request.screenname == null) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Screenname is required"));
                return;
            }

            // Update screenname
            Screenname updatedScreenname = screennameService.updateScreenname(
                screennameId, user.id(), request.screenname);

            LoggerUtil.info("Updated screenname " + screennameId + " to '" + request.screenname + "'");
            ctx.json(updatedScreenname.toResponse());

        } catch (ScreennameService.ScreennameServiceException e) {
            LoggerUtil.warn("Screenname update failed: " + e.getMessage());
            ctx.status(400).json(SharedErrorResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            LoggerUtil.error("Unexpected error updating screenname: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to update screenname"));
        }
    }

    /**
     * Updates password for a screenname.
     * PUT /api/screennames/{id}/password
     * Body: {"password": "newpassword"}
     */
    public void updatePassword(Context ctx) {
        try {
            // CSRF Protection
            if (!validateCsrf(ctx, csrfService, "password update")) return;

            Optional<User> userOpt = getAuthenticatedUser(ctx);
            if (userOpt.isEmpty()) return;
            User user = userOpt.get();

            // Parse screenname ID
            Optional<Integer> screennameIdOpt = parsePathParamId(ctx, "id", "screenname");
            if (screennameIdOpt.isEmpty()) return;
            int screennameId = screennameIdOpt.get();

            // Parse request body
            UpdatePasswordRequest request = ctx.bodyAsClass(UpdatePasswordRequest.class);

            if (request.password == null) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Password is required"));
                return;
            }

            // Update password
            Screenname updatedScreenname = screennameService.updatePassword(
                screennameId, user.id(), request.password);

            LoggerUtil.info("Updated password for screenname " + screennameId);
            ctx.json(updatedScreenname.toResponse());

        } catch (ScreennameService.ScreennameServiceException e) {
            LoggerUtil.warn("Password update failed: " + e.getMessage());
            ctx.status(400).json(SharedErrorResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            LoggerUtil.error("Unexpected error updating password: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to update password"));
        }
    }

    /**
     * Sets a screenname as the user's primary screenname.
     * PUT /api/screennames/{id}/primary
     */
    public void setPrimary(Context ctx) {
        try {
            // CSRF Protection
            if (!validateCsrf(ctx, csrfService, "set primary")) return;

            Optional<User> userOpt = getAuthenticatedUser(ctx);
            if (userOpt.isEmpty()) return;
            User user = userOpt.get();

            // Parse screenname ID
            Optional<Integer> screennameIdOpt = parsePathParamId(ctx, "id", "screenname");
            if (screennameIdOpt.isEmpty()) return;
            int screennameId = screennameIdOpt.get();

            // Set as primary
            Screenname updatedScreenname = screennameService.setPrimary(screennameId, user.id());

            LoggerUtil.info("Set screenname " + screennameId + " as primary for user " + user.xUsername());
            ctx.json(updatedScreenname.toResponse());

        } catch (ScreennameService.ScreennameServiceException e) {
            LoggerUtil.warn("Set primary failed: " + e.getMessage());
            ctx.status(400).json(SharedErrorResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            LoggerUtil.error("Unexpected error setting primary: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to set primary screenname"));
        }
    }

    /**
     * Deletes a screenname.
     * DELETE /api/screennames/{id}
     */
    public void deleteScreenname(Context ctx) {
        try {
            // CSRF Protection
            if (!validateCsrf(ctx, csrfService, "screenname deletion")) return;

            Optional<User> userOpt = getAuthenticatedUser(ctx);
            if (userOpt.isEmpty()) return;
            User user = userOpt.get();

            // Parse screenname ID
            Optional<Integer> screennameIdOpt = parsePathParamId(ctx, "id", "screenname");
            if (screennameIdOpt.isEmpty()) return;
            int screennameId = screennameIdOpt.get();

            // Delete screenname
            screennameService.deleteScreenname(screennameId, user.id());

            LoggerUtil.info("Deleted screenname " + screennameId + " for user " + user.xUsername());
            ctx.json(new DeleteResponse("Screenname deleted successfully"));

        } catch (ScreennameService.ScreennameServiceException e) {
            LoggerUtil.warn("Screenname deletion failed: " + e.getMessage());
            ctx.status(400).json(SharedErrorResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            LoggerUtil.error("Unexpected error deleting screenname: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to delete screenname"));
        }
    }

    /**
     * Request DTO for creating a new screenname.
     */
    public static class CreateScreennameRequest {
        public String screenname;
        public String password;
    }

    /**
     * Request DTO for updating a screenname.
     */
    public static class UpdateScreennameRequest {
        public String screenname;
    }

    /**
     * Request DTO for updating a password.
     */
    public static class UpdatePasswordRequest {
        public String password;
    }

    /**
     * Response for getting all screennames.
     */
    public record ScreennamesResponse(List<Screenname.ScreennameResponse> screennames) {}

    /**
     * Response for successful deletion.
     */
    public record DeleteResponse(String message) {}
}
