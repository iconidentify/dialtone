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
            rememberLoginOrigin(ctx);
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
        // Read (and clear) the surface that started the login so both success and
        // error paths return there (e.g. /quick) instead of the legacy callback.
        String origin = consumeLoginOrigin(ctx);
        boolean popup = consumeLoginPopup(ctx);
        try {
            String providerError = ctx.queryParam("error");
            if (providerError != null && !providerError.isBlank()) {
                redirectWithError(ctx, providerError, origin, popup);
                return;
            }

            String code = ctx.queryParam("code");
            String state = ctx.queryParam("state");

            if (code == null || state == null) {
                redirectWithError(ctx, "Missing authorization code or state parameter", origin, popup);
                return;
            }

            // Exchange code for user account
            User user = xAuthService.handleCallback(code, state);

            // Complete authentication flow
            completeAuthentication(ctx, user, "X", origin, popup);

        } catch (OAuthBaseService.AuthenticationException e) {
            LoggerUtil.warn("X OAuth authentication failed: " + e.getMessage());
            redirectWithError(ctx, e.getMessage(), origin, popup);

        } catch (Exception e) {
            LoggerUtil.error("Unexpected error in X OAuth callback: " + e.getMessage());
            redirectWithError(ctx, "An unexpected error occurred during authentication", origin, popup);
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

            rememberLoginOrigin(ctx);
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
        // Read (and clear) the surface that started the login so both success and
        // error paths return there (e.g. /quick) instead of the legacy callback.
        String origin = consumeLoginOrigin(ctx);
        boolean popup = consumeLoginPopup(ctx);
        try {
            if (!discordAuthService.isEnabled()) {
                redirectWithError(ctx, "Discord login is not configured", origin, popup);
                return;
            }

            String providerError = ctx.queryParam("error");
            if (providerError != null && !providerError.isBlank()) {
                redirectWithError(ctx, providerError, origin, popup);
                return;
            }

            String code = ctx.queryParam("code");
            String state = ctx.queryParam("state");

            if (code == null || state == null) {
                redirectWithError(ctx, "Missing authorization code or state parameter", origin, popup);
                return;
            }

            // Exchange code for user account
            User user = discordAuthService.handleCallback(code, state);

            // Complete authentication flow
            completeAuthentication(ctx, user, "Discord", origin, popup);

        } catch (OAuthBaseService.AuthenticationException e) {
            LoggerUtil.warn("Discord OAuth authentication failed: " + e.getMessage());
            redirectWithError(ctx, e.getMessage(), origin, popup);

        } catch (Exception e) {
            LoggerUtil.error("Unexpected error in Discord OAuth callback: " + e.getMessage());
            redirectWithError(ctx, "An unexpected error occurred during authentication", origin, popup);
        }
    }

    /**
     * Common authentication completion logic for X, Discord, and email.
     */
    private void completeAuthentication(Context ctx, User user, String provider) {
        completeAuthentication(ctx, user, provider, null);
    }

    /**
     * Completes authentication and redirects the user back to the surface that
     * started the flow. The auth cookie is set regardless, so a recognized
     * {@code origin} (e.g. "quick") can return the user straight to that page,
     * which reads the cookie and shows the signed-in state inline. Unknown or
     * absent origins fall back to the React callback page.
     */
    private void completeAuthentication(Context ctx, User user, String provider, String origin) {
        completeAuthentication(ctx, user, provider, origin, false);
    }

    private void completeAuthentication(Context ctx, User user, String provider, String origin, boolean popup) {
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

        LoggerUtil.info("Successful " + provider + " login for user: " + user.getProviderUsername());

        String redirectUrl = resolvePostLoginRedirect(origin, token, popup);
        ctx.redirect(redirectUrl);
    }

    /**
     * Maps an origin tag to a safe same-origin redirect path. Whitelisted to
     * prevent open-redirect abuse; the cookie is already set so no token needs
     * to ride in the URL for the quick surface.
     */
    private String resolvePostLoginRedirect(String origin, String token, boolean popup) {
        if ("quick".equals(origin)) {
            if (popup) {
                return "/quick-auth?ok=1";
            }
            return "/quick";
        }
        return "/auth/callback?token=" + token + "&success=true";
    }

    /**
     * Redirect to frontend with error information.
     */
    private void redirectWithError(Context ctx, String errorMessage) {
        redirectWithError(ctx, errorMessage, null);
    }

    /**
     * Redirect with error information, returning to the originating surface when
     * recognized (e.g. /quick) so the error is shown inline there rather than on
     * the React callback page.
     */
    private void redirectWithError(Context ctx, String errorMessage, String origin) {
        redirectWithError(ctx, errorMessage, origin, false);
    }

    private void redirectWithError(Context ctx, String errorMessage, String origin, boolean popup) {
        String encoded = java.net.URLEncoder.encode(errorMessage, java.nio.charset.StandardCharsets.UTF_8);
        String errorUrl;
        if ("quick".equals(origin)) {
            errorUrl = popup
                ? "/quick-auth?ok=0&error=" + encoded
                : "/quick?login_error=" + encoded;
        } else {
            errorUrl = "/auth/callback?success=false&error=" + encoded;
        }
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

            emailAuthService.initiateLogin(request.email, getClientIp(ctx), request.origin);

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
        String origin = ctx.queryParam("origin");
        try {
            if (!emailAuthService.isEnabled()) {
                redirectWithError(ctx, "Email login is not configured", origin);
                return;
            }

            String token = ctx.queryParam("token");

            if (token == null || token.isBlank()) {
                redirectWithError(ctx, "Invalid or missing token", origin);
                return;
            }

            User user = emailAuthService.validateMagicLink(token);

            // Complete authentication flow (same as OAuth), returning the user to
            // the surface that initiated the login when one was supplied.
            completeAuthentication(ctx, user, "Email", origin);

        } catch (EmailAuthService.AuthenticationException e) {
            LoggerUtil.warn("Magic link verification failed: " + e.getMessage());
            redirectWithError(ctx, e.getMessage(), origin);

        } catch (Exception e) {
            LoggerUtil.error("Unexpected error verifying magic link: " + e.getMessage());
            redirectWithError(ctx, "An unexpected error occurred during authentication", origin);
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
     * Request for email login initiation. {@code origin} optionally tags which
     * surface initiated the login so the magic link returns there (e.g. "quick").
     */
    public record EmailLoginRequest(String email, String origin) {}

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

    /** Cookie that carries the originating surface across the OAuth round-trip. */
    private static final String LOGIN_ORIGIN_COOKIE = "dt_login_origin";
    private static final String LOGIN_POPUP_COOKIE = "dt_login_popup";
    private static final int LOGIN_ORIGIN_MAX_AGE_SECONDS = 600; // 10 minutes

    /**
     * Remembers the surface that started an OAuth login (e.g. {@code ?origin=quick})
     * so the provider callback can return there. Uses a short-lived SameSite=Lax
     * cookie - Lax (unlike the Strict auth cookie) is sent on the top-level GET
     * navigation back from the provider, which is cross-site. Only whitelisted
     * origins are stored to avoid smuggling arbitrary values into the redirect.
     */
    private void rememberLoginOrigin(Context ctx) {
        String origin = ctx.queryParam("origin");
        if (!"quick".equals(origin)) {
            clearLoginReturnCookies(ctx);
            return;
        }
        ctx.cookie(buildLoginOriginCookie(ctx, origin, LOGIN_ORIGIN_MAX_AGE_SECONDS));
        if (isPopupLogin(ctx)) {
            ctx.cookie(buildLoginPopupCookie(ctx, "1", LOGIN_ORIGIN_MAX_AGE_SECONDS));
        } else {
            ctx.cookie(buildLoginPopupCookie(ctx, "", 0));
        }
    }

    /**
     * Returns the remembered login origin (or null) and clears the cookie so it
     * can't leak into a later, unrelated login.
     */
    private String consumeLoginOrigin(Context ctx) {
        String origin = ctx.cookie(LOGIN_ORIGIN_COOKIE);
        if (origin == null || origin.isEmpty()) {
            return null;
        }
        ctx.cookie(buildLoginOriginCookie(ctx, "", 0));
        return "quick".equals(origin) ? origin : null;
    }

    private boolean consumeLoginPopup(Context ctx) {
        String popup = ctx.cookie(LOGIN_POPUP_COOKIE);
        ctx.cookie(buildLoginPopupCookie(ctx, "", 0));
        return "1".equals(popup);
    }

    private boolean isPopupLogin(Context ctx) {
        String popup = ctx.queryParam("popup");
        return "1".equals(popup) || "true".equalsIgnoreCase(popup);
    }

    private void clearLoginReturnCookies(Context ctx) {
        ctx.cookie(buildLoginOriginCookie(ctx, "", 0));
        ctx.cookie(buildLoginPopupCookie(ctx, "", 0));
    }

    private Cookie buildLoginOriginCookie(Context ctx, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(LOGIN_ORIGIN_COOKIE, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure("https".equalsIgnoreCase(ctx.scheme()));
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setSameSite(SameSite.LAX);
        return cookie;
    }

    private Cookie buildLoginPopupCookie(Context ctx, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(LOGIN_POPUP_COOKIE, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure("https".equalsIgnoreCase(ctx.scheme()));
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setSameSite(SameSite.LAX);
        return cookie;
    }
}
