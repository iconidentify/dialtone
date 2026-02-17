/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.auth;

import com.dialtone.db.models.Screenname;
import com.dialtone.db.models.User;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.web.api.SharedErrorResponse;
import com.dialtone.web.services.AdminSecurityService;
import com.dialtone.web.services.DiscordAuthService;
import com.dialtone.web.services.EmailAuthService;
import com.dialtone.web.services.OAuthBaseService;
import com.dialtone.web.services.JwtTokenService;
import com.dialtone.web.services.ScreennameService;
import com.dialtone.web.services.XAuthService;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;

import java.time.Duration;
import java.util.List;

import static com.dialtone.web.api.AdminControllerUtils.getClientIp;

/**
 * Controller for authentication endpoints.
 *
 * Handles X, Discord OAuth, and Email magic link login flows, session management,
 * and user profile information for the web interface.
 */
public class AuthController {
    public static final String AUTH_COOKIE_NAME = "dialtone_auth_token";
    private static final int AUTH_COOKIE_MAX_AGE_SECONDS = (int) Duration.ofHours(24).getSeconds();

    private final XAuthService xAuthService;
    private final DiscordAuthService discordAuthService;
    private final EmailAuthService emailAuthService;
    private final ScreennameService screennameService;
    private final JwtTokenService jwtTokenService;
    private final AdminSecurityService adminSecurityService;

    public AuthController(XAuthService xAuthService, DiscordAuthService discordAuthService,
                         EmailAuthService emailAuthService, ScreennameService screennameService,
                         JwtTokenService jwtTokenService, AdminSecurityService adminSecurityService) {
        this.xAuthService = xAuthService;
        this.discordAuthService = discordAuthService;
        this.emailAuthService = emailAuthService;
        this.screennameService = screennameService;
        this.jwtTokenService = jwtTokenService;
        this.adminSecurityService = adminSecurityService;
    }

    /**
     * Initiates X OAuth login by redirecting to X authorization page.
     * GET /api/auth/x/login
     */
    public void initiateXLogin(Context ctx) {
        try {
            String authUrl = xAuthService.getAuthorizationUrl();
            LoggerUtil.debug("Redirecting to X OAuth: " + authUrl);
            ctx.redirect(authUrl);

        } catch (Exception e) {
            LoggerUtil.error("Failed to initiate X OAuth login: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError(e.getMessage()));
        }
    }

    /**
     * Handles X OAuth callback after user authorizes the application.
     * GET /api/auth/x/callback
     */
    public void handleXCallback(Context ctx) {
        try {
            String code = ctx.queryParam("code");
            String state = ctx.queryParam("state");

            if (code == null || state == null) {
                ctx.status(400).json(SharedErrorResponse.badRequest(
                    "Missing authorization code or state parameter"));
                return;
            }

            // Exchange code for user account
            User user = xAuthService.handleCallback(code, state);

            // Complete authentication flow
            completeAuthentication(ctx, user, "X");

        } catch (OAuthBaseService.AuthenticationException e) {
            LoggerUtil.warn("X OAuth authentication failed: " + e.getMessage());
            redirectWithError(ctx, e.getMessage());

        } catch (Exception e) {
            LoggerUtil.error("Unexpected error in X OAuth callback: " + e.getMessage());
            redirectWithError(ctx, "An unexpected error occurred during authentication");
        }
    }

    /**
     * Initiates Discord OAuth login by redirecting to Discord authorization page.
     * GET /api/auth/discord/login
     */
    public void initiateDiscordLogin(Context ctx) {
        try {
            if (!discordAuthService.isEnabled()) {
                ctx.status(503).json(SharedErrorResponse.serverError("Discord login is not configured"));
                return;
            }

            String authUrl = discordAuthService.getAuthorizationUrl();
            LoggerUtil.debug("Redirecting to Discord OAuth: " + authUrl);
            ctx.redirect(authUrl);

        } catch (Exception e) {
            LoggerUtil.error("Failed to initiate Discord OAuth login: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError(e.getMessage()));
        }
    }

    /**
     * Handles Discord OAuth callback after user authorizes the application.
     * GET /api/auth/discord/callback
     */
    public void handleDiscordCallback(Context ctx) {
        try {
            if (!discordAuthService.isEnabled()) {
                ctx.status(503).json(SharedErrorResponse.serverError("Discord login is not configured"));
                return;
            }

            String code = ctx.queryParam("code");
            String state = ctx.queryParam("state");

            if (code == null || state == null) {
                ctx.status(400).json(SharedErrorResponse.badRequest(
                    "Missing authorization code or state parameter"));
                return;
            }

            // Exchange code for user account
            User user = discordAuthService.handleCallback(code, state);

            // Complete authentication flow
            completeAuthentication(ctx, user, "Discord");

        } catch (OAuthBaseService.AuthenticationException e) {
            LoggerUtil.warn("Discord OAuth authentication failed: " + e.getMessage());
            redirectWithError(ctx, e.getMessage());

        } catch (Exception e) {
            LoggerUtil.error("Unexpected error in Discord OAuth callback: " + e.getMessage());
            redirectWithError(ctx, "An unexpected error occurred during authentication");
        }
    }

    /**
     * Common authentication completion logic for both X and Discord.
     */
    private void completeAuthentication(Context ctx, User user, String provider) {
        // Generate JWT token for web session with admin status
        String token = jwtTokenService.generateToken(user, adminSecurityService);
        setAuthCookie(ctx, token);

        // Get user's screennames (non-critical, just for logging)
        try {
            List<Screenname> screennames = screennameService.getScreennamesForUser(user.id());
            LoggerUtil.debug("User has " + screennames.size() + " screennames");
        } catch (Exception e) {
            LoggerUtil.warn("Could not retrieve screennames during login: " + e.getMessage());
        }

        LoggerUtil.info("Successful " + provider + " OAuth login for user: " + user.getProviderUsername());

        // Redirect to frontend callback page with token
        String redirectUrl = "/auth/callback?token=" + token + "&success=true";
        ctx.redirect(redirectUrl);
    }

    /**
     * Redirect to frontend with error information.
     */
    private void redirectWithError(Context ctx, String errorMessage) {
        String errorUrl = "/auth/callback?success=false&error=" +
            java.net.URLEncoder.encode(errorMessage, java.nio.charset.StandardCharsets.UTF_8);
        ctx.redirect(errorUrl);
    }

    /**
     * Gets information about the currently authenticated user.
     * GET /api/auth/me
     */
    public void getCurrentUser(Context ctx) {
        try {
            // Extract user from authentication context (set by requireAuth middleware)
            User user = ctx.attribute("user");
            if (user == null) {
                ctx.status(401).json(SharedErrorResponse.unauthorized("User not found in context"));
                return;
            }

            // Get user's screennames
            List<Screenname> screennames = screennameService.getScreennamesForUser(user.id());

            // Check admin status
            boolean isAdmin = adminSecurityService.isAdmin(user);

            UserInfoResponse response = new UserInfoResponse(
                user.id(),
                user.authProvider(),
                user.getProviderUsername(),
                user.getDisplayName(),
                user.xUsername(),
                user.discordUsername(),
                user.email(),
                screennames.stream().map(Screenname::toResponse).toList(),
                isAdmin
            );

            ctx.json(response);

        } catch (Exception e) {
            LoggerUtil.error("Failed to get current user info: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve user information"));
        }
    }

    /**
     * Logs out the current user by invalidating their session.
     * POST /api/auth/logout
     */
    public void logout(Context ctx) {
        try {
            // Extract JWT token from Authorization header and revoke it
            String authHeader = ctx.header("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7); // Remove "Bearer " prefix
                jwtTokenService.revokeToken(token);
                LoggerUtil.debug("User logout: JWT token revoked");
            }

            clearAuthCookie(ctx);

            ctx.json(new LogoutResponse("Logged out successfully"));

        } catch (Exception e) {
            LoggerUtil.error("Error during logout: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to logout"));
        }
    }

    /**
     * Initiates email magic link login.
     * POST /api/auth/email/login
     *
     * Request body: { "email": "user@example.com" }
     */
    public void initiateEmailLogin(Context ctx) {
        try {
            if (!emailAuthService.isEnabled()) {
                ctx.status(503).json(SharedErrorResponse.serverError("Email login is not configured"));
                return;
            }

            EmailLoginRequest request = ctx.bodyAsClass(EmailLoginRequest.class);

            if (request.email == null || request.email.isBlank()) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Email is required"));
                return;
            }

            emailAuthService.initiateLogin(request.email, getClientIp(ctx));

            ctx.json(new MagicLinkSentResponse(
                "Check your email for a sign-in link",
                true
            ));

        } catch (EmailAuthService.AuthenticationException e) {
            LoggerUtil.warn("Email login initiation failed: " + e.getMessage());
            ctx.status(400).json(SharedErrorResponse.badRequest(e.getMessage()));

        } catch (Exception e) {
            LoggerUtil.error("Unexpected error initiating email login: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError(
                "An unexpected error occurred. Please try again."));
        }
    }

    /**
     * Verifies magic link token and completes authentication.
     * GET /api/auth/email/verify?token=xxx
     */
    public void verifyMagicLink(Context ctx) {
        try {
            if (!emailAuthService.isEnabled()) {
                redirectWithError(ctx, "Email login is not configured");
                return;
            }

            String token = ctx.queryParam("token");
            
            if (token == null || token.isBlank()) {
                redirectWithError(ctx, "Invalid or missing token");
                return;
            }

            User user = emailAuthService.validateMagicLink(token);
            
            // Complete authentication flow (same as OAuth)
            completeAuthentication(ctx, user, "Email");

        } catch (EmailAuthService.AuthenticationException e) {
            LoggerUtil.warn("Magic link verification failed: " + e.getMessage());
            redirectWithError(ctx, e.getMessage());

        } catch (Exception e) {
            LoggerUtil.error("Unexpected error verifying magic link: " + e.getMessage());
            redirectWithError(ctx, "An unexpected error occurred during authentication");
        }
    }

    /**
     * Gets available auth providers and their status.
     * GET /api/auth/providers
     */
    public void getAuthProviders(Context ctx) {
        ctx.json(new AuthProvidersResponse(
            true, // X is always enabled
            discordAuthService.isEnabled(),
            emailAuthService.isEnabled()
        ));
    }

    /**
     * Response for successful login.
     */
    public record LoginResponse(
        int userId,
        String authProvider,
        String providerUsername,
        String displayName,
        String token,
        List<Screenname.ScreennameResponse> screennames
    ) {}

    /**
     * Response for current user info.
     */
    public record UserInfoResponse(
        int userId,
        String authProvider,
        String providerUsername,
        String displayName,
        String xUsername,
        String discordUsername,
        String email,
        List<Screenname.ScreennameResponse> screennames,
        boolean isAdmin
    ) {}

    /**
     * Response for available auth providers.
     */
    public record AuthProvidersResponse(
        boolean xEnabled,
        boolean discordEnabled,
        boolean emailEnabled
    ) {}

    /**
     * Request for email login initiation.
     */
    public record EmailLoginRequest(String email) {}

    /**
     * Response after magic link is sent.
     */
    public record MagicLinkSentResponse(String message, boolean success) {}

    /**
     * Response for logout.
     */
    public record LogoutResponse(String message) {}

    private void setAuthCookie(Context ctx, String token) {
        ctx.cookie(buildAuthCookie(ctx, token, AUTH_COOKIE_MAX_AGE_SECONDS));
    }

    private void clearAuthCookie(Context ctx) {
        ctx.cookie(buildAuthCookie(ctx, "", 0));
    }

    private Cookie buildAuthCookie(Context ctx, String value, int maxAgeSeconds) {
        boolean secure = "https".equalsIgnoreCase(ctx.scheme());
        Cookie cookie = new Cookie(AUTH_COOKIE_NAME, value);
        cookie.setPath("/");
        cookie.setHttpOnly(false);
        cookie.setSecure(secure);
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setSameSite(SameSite.STRICT);
        return cookie;
    }
}
