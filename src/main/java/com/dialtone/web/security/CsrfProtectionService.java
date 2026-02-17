/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.security;

import com.dialtone.utils.LoggerUtil;
import io.javalin.http.Context;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CSRF (Cross-Site Request Forgery) protection service.
 *
 * Provides token generation, validation, and automatic cleanup
 * to protect against CSRF attacks on state-changing operations.
 */
public class CsrfProtectionService {

    private final ConcurrentHashMap<String, CsrfToken> activeCsrfTokens = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    // Token configuration
    private static final int TOKEN_LENGTH = 32;
    private static final long TOKEN_VALIDITY_MS = 30 * 60 * 1000; // 30 minutes
    private static final long CLEANUP_INTERVAL_MS = 10 * 60 * 1000; // 10 minutes

    // Cleanup tracking
    private volatile long lastCleanup = System.currentTimeMillis();

    /**
     * Generates a new CSRF token for a session.
     * Tokens are bound to the client's session/IP for additional security.
     *
     * @param ctx Javalin context containing request information
     * @return Generated CSRF token string
     */
    public String generateCsrfToken(Context ctx) {
        String token = generateSecureToken();
        String clientIdentifier = getClientIdentifier(ctx);

        CsrfToken csrfToken = new CsrfToken(
            token,
            clientIdentifier,
            System.currentTimeMillis() + TOKEN_VALIDITY_MS
        );

        activeCsrfTokens.put(token, csrfToken);

        // Perform cleanup if needed
        cleanupExpiredTokensIfNeeded();

        LoggerUtil.debug("Generated CSRF token for client: " + clientIdentifier);
        return token;
    }

    /**
     * Validates a CSRF token from a request.
     *
     * @param ctx Javalin context containing the request
     * @param providedToken The CSRF token provided in the request
     * @return true if token is valid, false otherwise
     */
    public boolean validateCsrfToken(Context ctx, String providedToken) {
        if (providedToken == null || providedToken.trim().isEmpty()) {
            LoggerUtil.debug("CSRF validation failed: no token provided");
            return false;
        }

        CsrfToken storedToken = activeCsrfTokens.get(providedToken.trim());
        if (storedToken == null) {
            LoggerUtil.debug("CSRF validation failed: token not found");
            return false;
        }

        // Check if token has expired
        if (System.currentTimeMillis() > storedToken.expiresAt) {
            LoggerUtil.debug("CSRF validation failed: token expired");
            activeCsrfTokens.remove(providedToken);
            return false;
        }

        // Verify client identifier matches
        String clientIdentifier = getClientIdentifier(ctx);
        if (!storedToken.clientIdentifier.equals(clientIdentifier)) {
            LoggerUtil.warn("CSRF validation failed: client identifier mismatch");
            return false;
        }

        // Token is valid - remove it (one-time use)
        activeCsrfTokens.remove(providedToken);
        LoggerUtil.debug("CSRF token validated successfully");
        return true;
    }

    /**
     * Extracts CSRF token from request headers or form data.
     * Checks multiple common locations for the CSRF token.
     *
     * @param ctx Javalin context
     * @return CSRF token if found, null otherwise
     */
    public String extractCsrfToken(Context ctx) {
        // Check X-CSRF-Token header (preferred for AJAX requests)
        String token = ctx.header("X-CSRF-Token");
        if (token != null && !token.trim().isEmpty()) {
            return token.trim();
        }

        // Check Authorization header custom format
        String authHeader = ctx.header("Authorization");
        if (authHeader != null && authHeader.startsWith("CSRF ")) {
            return authHeader.substring(5).trim();
        }

        // Check form parameter (for traditional form submissions)
        token = ctx.formParam("_csrf");
        if (token != null && !token.trim().isEmpty()) {
            return token.trim();
        }

        // Check query parameter (less secure, but sometimes necessary)
        token = ctx.queryParam("_csrf");
        if (token != null && !token.trim().isEmpty()) {
            return token.trim();
        }

        return null;
    }

    /**
     * Adds CSRF protection to state-changing endpoints.
     * Call this before processing PUT, POST, DELETE requests.
     *
     * @param ctx Javalin context
     * @throws CsrfValidationException if CSRF validation fails
     */
    public void requireValidCsrfToken(Context ctx) throws CsrfValidationException {
        String method = ctx.method().toString();

        // Only require CSRF tokens for state-changing operations
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return; // Safe methods don't need CSRF protection
        }

        String providedToken = extractCsrfToken(ctx);
        if (!validateCsrfToken(ctx, providedToken)) {
            throw new CsrfValidationException("CSRF token validation failed");
        }
    }

    /**
     * Gets a unique identifier for the client making the request.
     * Uses IP address and User-Agent for basic client fingerprinting.
     *
     * @param ctx Javalin context
     * @return Client identifier string
     */
    private String getClientIdentifier(Context ctx) {
        String ip = getClientIp(ctx);
        String userAgent = ctx.userAgent();

        // Create a simple hash of IP + User-Agent
        return String.valueOf((ip + "|" + userAgent).hashCode());
    }

    /**
     * Extracts the real client IP address, considering proxy headers.
     *
     * @param ctx Javalin context
     * @return Client IP address
     */
    private String getClientIp(Context ctx) {
        // Check for forwarded IP headers (reverse proxy scenarios)
        String forwarded = ctx.header("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, use the first one
            return forwarded.split(",")[0].trim();
        }

        String realIp = ctx.header("X-Real-IP");
        if (realIp != null && !realIp.trim().isEmpty()) {
            return realIp.trim();
        }

        // Fall back to direct connection IP
        return ctx.ip();
    }

    /**
     * Generates a cryptographically secure random token.
     *
     * @return Secure random token string
     */
    private String generateSecureToken() {
        StringBuilder token = new StringBuilder();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

        for (int i = 0; i < TOKEN_LENGTH; i++) {
            token.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }

        return token.toString();
    }

    /**
     * Cleans up expired CSRF tokens to prevent memory leaks.
     */
    private void cleanupExpiredTokensIfNeeded() {
        long now = System.currentTimeMillis();

        if (now - lastCleanup > CLEANUP_INTERVAL_MS) {
            synchronized (this) {
                if (now - lastCleanup > CLEANUP_INTERVAL_MS) {
                    int initialSize = activeCsrfTokens.size();

                    activeCsrfTokens.entrySet().removeIf(entry ->
                        now > entry.getValue().expiresAt);

                    int removedCount = initialSize - activeCsrfTokens.size();
                    if (removedCount > 0) {
                        LoggerUtil.debug("Cleaned up " + removedCount + " expired CSRF tokens");
                    }

                    lastCleanup = now;
                }
            }
        }
    }

    /**
     * Gets the number of active CSRF tokens (for monitoring).
     *
     * @return Number of active CSRF tokens
     */
    public int getActiveCsrfTokenCount() {
        cleanupExpiredTokensIfNeeded();
        return activeCsrfTokens.size();
    }

    /**
     * Represents an active CSRF token with metadata.
     */
    private record CsrfToken(String token, String clientIdentifier, long expiresAt) {}

    /**
     * Exception thrown when CSRF validation fails.
     */
    public static class CsrfValidationException extends Exception {
        public CsrfValidationException(String message) {
            super(message);
        }
    }
}