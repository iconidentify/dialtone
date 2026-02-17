/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.services;

import com.dialtone.db.models.User;
import com.dialtone.utils.LoggerUtil;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for JWT token management in the Dialtone web interface.
 *
 * Provides secure token generation, validation, and blacklisting for
 * web session management. Uses JJWT library with proper security practices.
 */
public class JwtTokenService {

    private final SecretKey secretKey;
    private final int expiryHours;
    private final String issuer;

    // Token blacklist for revoked tokens (before expiration)
    private final ConcurrentHashMap<String, Instant> blacklistedTokens = new ConcurrentHashMap<>();

    // Cleanup interval for blacklisted tokens (every hour)
    private static final long BLACKLIST_CLEANUP_INTERVAL_MS = 60 * 60 * 1000;
    private volatile long lastCleanup = System.currentTimeMillis();

    public JwtTokenService(Properties config) {
        // Get JWT secret from configuration
        String secret = config.getProperty("jwt.secret", "CHANGE_THIS_IN_PRODUCTION_TO_SECURE_RANDOM_STRING");

        // Security check: fail startup if using default secret in production
        boolean isProduction = !Boolean.parseBoolean(config.getProperty("development.mode", "false"));
        if (isProduction && secret.equals("CHANGE_THIS_IN_PRODUCTION_TO_SECURE_RANDOM_STRING")) {
            throw new IllegalStateException(
                "SECURITY ERROR: Default JWT secret detected in production mode. " +
                "Please set a secure random jwt.secret in application.properties. " +
                "Generate one with: openssl rand -hex 32"
            );
        }

        // Create secret key (JJWT will use HMAC-SHA256)
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());

        // Get expiry configuration
        this.expiryHours = Integer.parseInt(config.getProperty("jwt.expiry.hours", "24"));

        // Set issuer
        this.issuer = config.getProperty("jwt.issuer", "dialtone-web-interface");

        LoggerUtil.info("JWT token service initialized with " + expiryHours + "h expiry");
    }

    /**
     * Generates a JWT token for an authenticated user.
     *
     * @param user The authenticated user
     * @return JWT token string
     */
    public String generateToken(User user) {
        return generateToken(user, null);
    }

    /**
     * Generates a JWT token for an authenticated user with role information.
     *
     * @param user The authenticated user
     * @param adminSecurityService Service to check admin status (null to skip admin check)
     * @return JWT token string
     */
    public String generateToken(User user, AdminSecurityService adminSecurityService) {
        Instant now = Instant.now();
        Instant expiry = now.plus(expiryHours, ChronoUnit.HOURS);

        JwtBuilder tokenBuilder = Jwts.builder()
            .setIssuer(issuer)
            .setSubject(String.valueOf(user.id()))
            .setAudience("dialtone-web")
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expiry))
            .setId(UUID.randomUUID().toString()) // Unique token ID for blacklisting
            .claim("userId", user.id())
            .claim("authProvider", user.authProvider())
            .claim("providerUserId", user.getProviderUserId())
            .claim("providerUsername", user.getProviderUsername())
            .claim("displayName", user.getDisplayName())
            .claim("email", user.email())
            // Keep legacy claims for backward compatibility
            .claim("xUserId", user.xUserId())
            .claim("xUsername", user.xUsername())
            .claim("discordUserId", user.discordUserId())
            .claim("discordUsername", user.discordUsername());

        // Add admin role information if service is available
        if (adminSecurityService != null) {
            boolean isAdmin = adminSecurityService.isAdmin(user);
            tokenBuilder.claim("isAdmin", isAdmin);

            if (isAdmin) {
                // Generate admin session ID for additional security tracking
                String adminSessionId = UUID.randomUUID().toString();
                tokenBuilder.claim("adminSession", adminSessionId);
                LoggerUtil.debug(String.format("Generated admin token for user: %s (session: %s)",
                               user.getProviderUsername(), adminSessionId));
            }
        }

        return tokenBuilder
            .signWith(secretKey, SignatureAlgorithm.HS256)
            .compact();
    }

    /**
     * Validates and parses a JWT token.
     *
     * @param token JWT token string
     * @return TokenValidationResult with user information or error details
     */
    public TokenValidationResult validateToken(String token) {
        try {
            // Clean up expired blacklisted tokens periodically
            cleanupBlacklistIfNeeded();

            // Parse and validate token
            Jws<Claims> jws = Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .requireAudience("dialtone-web")
                .build()
                .parseSignedClaims(token);

            Claims claims = jws.getBody();

            // Check if token is blacklisted
            String tokenId = claims.getId();
            if (tokenId != null && blacklistedTokens.containsKey(tokenId)) {
                return TokenValidationResult.invalid("Token has been revoked");
            }

            // Extract user information
            Integer userId = claims.get("userId", Integer.class);
            String authProvider = claims.get("authProvider", String.class);
            String providerUserId = claims.get("providerUserId", String.class);
            String providerUsername = claims.get("providerUsername", String.class);
            String displayName = claims.get("displayName", String.class);
            String email = claims.get("email", String.class);
            
            // Legacy claims for backward compatibility
            String xUserId = claims.get("xUserId", String.class);
            String xUsername = claims.get("xUsername", String.class);
            String discordUserId = claims.get("discordUserId", String.class);
            String discordUsername = claims.get("discordUsername", String.class);

            // Handle legacy tokens that don't have authProvider
            if (authProvider == null) {
                authProvider = User.PROVIDER_X;
                providerUserId = xUserId;
                providerUsername = xUsername;
            }

            if (userId == null || providerUserId == null || providerUsername == null) {
                return TokenValidationResult.invalid("Token missing required user claims");
            }

            // Extract admin information
            Boolean isAdmin = claims.get("isAdmin", Boolean.class);
            String adminSession = claims.get("adminSession", String.class);

            // Create TokenUser object
            TokenUser tokenUser = new TokenUser(userId, authProvider, providerUserId, providerUsername,
                                               displayName, email, xUserId, xUsername, discordUserId, discordUsername, tokenId);

            LoggerUtil.debug("Successfully validated token for user: " + providerUsername);
            return TokenValidationResult.valid(tokenUser);

        } catch (ExpiredJwtException e) {
            LoggerUtil.debug("Token expired: " + e.getMessage());
            return TokenValidationResult.invalid("Token has expired");

        } catch (JwtException e) {
            LoggerUtil.warn("Invalid JWT token: " + e.getMessage());
            return TokenValidationResult.invalid("Invalid token");

        } catch (Exception e) {
            LoggerUtil.error("Unexpected error validating token: " + e.getMessage());
            return TokenValidationResult.invalid("Token validation failed");
        }
    }

    /**
     * Revokes a token by adding it to the blacklist.
     * Used for logout functionality.
     *
     * @param token JWT token to revoke
     */
    public void revokeToken(String token) {
        try {
            // Parse token to get ID and expiry (even if expired)
            Jws<Claims> jws = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);

            Claims claims = jws.getBody();
            String tokenId = claims.getId();

            if (tokenId != null) {
                Instant expiry = claims.getExpiration().toInstant();
                blacklistedTokens.put(tokenId, expiry);
                LoggerUtil.debug("Token revoked: " + tokenId);
            }

        } catch (ExpiredJwtException e) {
            // Token is already expired, but we can still blacklist it
            String tokenId = e.getClaims().getId();
            if (tokenId != null) {
                Instant expiry = e.getClaims().getExpiration().toInstant();
                blacklistedTokens.put(tokenId, expiry);
                LoggerUtil.debug("Expired token revoked: " + tokenId);
            }

        } catch (JwtException e) {
            LoggerUtil.warn("Cannot revoke invalid token: " + e.getMessage());
        }
    }

    /**
     * Gets the number of blacklisted tokens (for monitoring).
     *
     * @return Number of blacklisted tokens
     */
    public int getBlacklistedTokenCount() {
        cleanupBlacklistIfNeeded();
        return blacklistedTokens.size();
    }

    /**
     * Cleans up expired tokens from blacklist to prevent memory leaks.
     */
    private void cleanupBlacklistIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup > BLACKLIST_CLEANUP_INTERVAL_MS) {
            synchronized (this) {
                if (now - lastCleanup > BLACKLIST_CLEANUP_INTERVAL_MS) {
                    Instant cutoff = Instant.now();
                    int initialSize = blacklistedTokens.size();

                    blacklistedTokens.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));

                    int removedCount = initialSize - blacklistedTokens.size();
                    if (removedCount > 0) {
                        LoggerUtil.debug("Cleaned up " + removedCount + " expired blacklisted tokens");
                    }

                    lastCleanup = now;
                }
            }
        }
    }

    /**
     * Represents the result of token validation.
     */
    public static class TokenValidationResult {
        private final boolean valid;
        private final TokenUser tokenUser;
        private final String errorMessage;

        private TokenValidationResult(boolean valid, TokenUser tokenUser, String errorMessage) {
            this.valid = valid;
            this.tokenUser = tokenUser;
            this.errorMessage = errorMessage;
        }

        public static TokenValidationResult valid(TokenUser tokenUser) {
            return new TokenValidationResult(true, tokenUser, null);
        }

        public static TokenValidationResult invalid(String errorMessage) {
            return new TokenValidationResult(false, null, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public TokenUser getTokenUser() {
            return tokenUser;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Represents user information extracted from a JWT token.
     */
    public record TokenUser(
        int userId,
        String authProvider,
        String providerUserId,
        String providerUsername,
        String displayName,
        String email,
        String xUserId,
        String xUsername,
        String discordUserId,
        String discordUsername,
        String tokenId
    ) {
        /**
         * Converts TokenUser to a full User object for application use.
         * Note: This creates a User without full database data (created_at uses current time).
         * Use this only for authentication context, not for database operations.
         */
        public User toUser() {
            return User.fromDatabase(
                userId,
                authProvider != null ? authProvider : User.PROVIDER_X,
                xUserId,
                xUsername,
                displayName,  // xDisplayName
                discordUserId,
                discordUsername,
                null,         // discordDisplayName - not stored in token
                email,
                java.time.LocalDateTime.now(), // created_at not available in token
                true  // assume active if token is valid
            );
        }
    }
}