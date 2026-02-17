/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.api;

import com.dialtone.db.models.ScreennamePreferences;
import com.dialtone.db.models.User;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.web.security.CsrfProtectionService;
import com.dialtone.web.services.ScreennamePreferencesService;
import io.javalin.http.Context;

import java.util.Optional;

import static com.dialtone.web.api.AdminControllerUtils.*;

/**
 * Controller for screenname preferences endpoints.
 *
 * Handles getting and updating preferences for screennames owned by the authenticated user.
 * Preferences are lazy-loaded - defaults are returned if no preferences row exists.
 */
public class ScreennamePreferencesController {
    private final ScreennamePreferencesService preferencesService;
    private final CsrfProtectionService csrfService;

    public ScreennamePreferencesController(ScreennamePreferencesService preferencesService,
                                           CsrfProtectionService csrfService) {
        this.preferencesService = preferencesService;
        this.csrfService = csrfService;
    }

    /**
     * Gets preferences for a screenname owned by the authenticated user.
     * GET /api/screennames/{id}/preferences
     *
     * Returns default preferences if no preferences row exists.
     */
    public void getPreferences(Context ctx) {
        try {
            Optional<User> userOpt = getAuthenticatedUser(ctx);
            if (userOpt.isEmpty()) return;
            User user = userOpt.get();

            // Parse screenname ID
            Optional<Integer> screennameIdOpt = parsePathParamId(ctx, "id", "screenname");
            if (screennameIdOpt.isEmpty()) return;
            int screennameId = screennameIdOpt.get();

            // Verify user owns this screenname
            if (!preferencesService.userOwnsScreenname(user.id(), screennameId)) {
                ctx.status(404).json(SharedErrorResponse.notFound("Screenname not found or access denied"));
                return;
            }

            // Get preferences (returns defaults if no row exists)
            ScreennamePreferences preferences = preferencesService.getPreferences(screennameId);
            ctx.json(preferences.toResponse());

        } catch (ScreennamePreferencesService.ScreennamePreferencesServiceException e) {
            LoggerUtil.error("Failed to get preferences: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve preferences"));

        } catch (Exception e) {
            LoggerUtil.error("Unexpected error getting preferences: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve preferences"));
        }
    }

    /**
     * Updates preferences for a screenname owned by the authenticated user.
     * PUT /api/screennames/{id}/preferences
     * Body: {"lowColorMode": true}
     *
     * Creates a preferences row if one doesn't exist (upsert pattern).
     */
    public void updatePreferences(Context ctx) {
        try {
            // CSRF Protection
            if (!validateCsrf(ctx, csrfService, "preferences update")) return;

            Optional<User> userOpt = getAuthenticatedUser(ctx);
            if (userOpt.isEmpty()) return;
            User user = userOpt.get();

            // Parse screenname ID
            Optional<Integer> screennameIdOpt = parsePathParamId(ctx, "id", "screenname");
            if (screennameIdOpt.isEmpty()) return;
            int screennameId = screennameIdOpt.get();

            // Verify user owns this screenname
            if (!preferencesService.userOwnsScreenname(user.id(), screennameId)) {
                ctx.status(404).json(SharedErrorResponse.notFound("Screenname not found or access denied"));
                return;
            }

            // Parse request body
            UpdatePreferencesRequest request = ctx.bodyAsClass(UpdatePreferencesRequest.class);

            if (request.lowColorMode == null) {
                ctx.status(400).json(SharedErrorResponse.badRequest("lowColorMode is required"));
                return;
            }

            // Update preferences
            ScreennamePreferences updated = preferencesService.updatePreferences(
                screennameId, request.lowColorMode);

            LoggerUtil.info("Updated preferences for screenname " + screennameId +
                          " (lowColorMode=" + request.lowColorMode + ")");
            ctx.json(updated.toResponse());

        } catch (ScreennamePreferencesService.ScreennamePreferencesServiceException e) {
            LoggerUtil.warn("Preferences update failed: " + e.getMessage());
            ctx.status(400).json(SharedErrorResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            LoggerUtil.error("Unexpected error updating preferences: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to update preferences"));
        }
    }

    /**
     * Request DTO for updating preferences.
     */
    public static class UpdatePreferencesRequest {
        public Boolean lowColorMode;
    }
}
