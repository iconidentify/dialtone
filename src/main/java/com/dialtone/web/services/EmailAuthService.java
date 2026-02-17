/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.services;

import com.dialtone.db.DatabaseManager;
import com.dialtone.db.models.User;
import com.dialtone.utils.LoggerUtil;

import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for email-based magic link authentication.
 * 
 * Handles:
 * - Magic link token generation
 * - Token validation and user lookup/creation
 * - Rate limiting per email
 * - Token cleanup
 */
public class EmailAuthService {
    
    private static final int TOKEN_BYTES = 32; // 256-bit token
    private static final DateTimeFormatter DB_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final DatabaseManager databaseManager;
    private final ResendEmailService emailService;
    private final String baseUrl;
    private final int expiryMinutes;
    private final int rateLimitPerHour;
    private final boolean enabled;
    
    // Rate limiting: email -> list of request timestamps
    private final ConcurrentHashMap<String, java.util.List<Long>> rateLimitMap = new ConcurrentHashMap<>();
    
    private final SecureRandom secureRandom = new SecureRandom();
    
    public EmailAuthService(DatabaseManager databaseManager, ResendEmailService emailService, Properties config) {
        this.databaseManager = databaseManager;
        this.emailService = emailService;
        this.enabled = Boolean.parseBoolean(config.getProperty("email.enabled", "false"));
        this.baseUrl = config.getProperty("email.base.url", "http://localhost:5200");
        this.expiryMinutes = Integer.parseInt(config.getProperty("email.magic.link.expiry.minutes", "15"));
        this.rateLimitPerHour = Integer.parseInt(config.getProperty("email.rate.limit.per.hour", "3"));
        
        if (enabled) {
            LoggerUtil.info("EmailAuthService initialized (expiry: " + expiryMinutes + " min, rate limit: " + rateLimitPerHour + "/hr)");
        } else {
            LoggerUtil.info("EmailAuthService disabled");
        }
    }
    
    /**
     * Check if email authentication is enabled.
     */
    public boolean isEnabled() {
        return enabled && emailService.isEnabled();
    }
    
    /**
     * Initiate magic link login for an email address.
     * 
     * @param email The email address to send the magic link to
     * @param ipAddress Client IP for audit logging
     * @throws AuthenticationException if rate limited or email send fails
     */
    public void initiateLogin(String email, String ipAddress) throws AuthenticationException {
        if (!isEnabled()) {
            throw new AuthenticationException("Email authentication is not enabled");
        }
        
        // Normalize email
        String normalizedEmail = email.toLowerCase().trim();
        
        // Validate email format
        if (!isValidEmail(normalizedEmail)) {
            throw new AuthenticationException("Invalid email address format");
        }
        
        // Check rate limit
        if (isRateLimited(normalizedEmail)) {
            LoggerUtil.warn("Rate limit exceeded for email: " + maskEmail(normalizedEmail) + " from IP: " + ipAddress);
            throw new AuthenticationException("Too many login attempts. Please try again later.");
        }
        
        try {
            // Generate secure token
            String token = generateSecureToken();
            
            // Calculate expiration
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(expiryMinutes);
            
            // Look up existing user (may be null for new users)
            Integer userId = findUserIdByEmail(normalizedEmail);
            
            // Store token in database
            storeMagicLinkToken(normalizedEmail, token, userId, expiresAt, ipAddress);
            
            // Build magic link URL (must point to API endpoint, not frontend)
            String magicLinkUrl = baseUrl + "/api/auth/email/verify?token=" + token;
            
            // Send email
            emailService.sendMagicLinkEmail(normalizedEmail, magicLinkUrl, expiryMinutes);
            
            // Record rate limit
            recordRateLimitRequest(normalizedEmail);
            
            LoggerUtil.info("Magic link sent to: " + maskEmail(normalizedEmail) + " (userId: " + userId + ", IP: " + ipAddress + ")");
            
        } catch (ResendEmailService.EmailSendException e) {
            LoggerUtil.error("Failed to send magic link email: " + e.getMessage());
            throw new AuthenticationException("Failed to send email. Please try again.", e);
        } catch (SQLException e) {
            LoggerUtil.error("Database error storing magic link token: " + e.getMessage());
            throw new AuthenticationException("An error occurred. Please try again.", e);
        }
    }
    
    /**
     * Validate a magic link token and return the authenticated user.
     * Creates a new user if this is their first login.
     * 
     * @param token The magic link token from the URL
     * @return The authenticated User
     * @throws AuthenticationException if token is invalid, expired, or already used
     */
    public User validateMagicLink(String token) throws AuthenticationException {
        if (!isEnabled()) {
            throw new AuthenticationException("Email authentication is not enabled");
        }
        
        try {
            // Look up token
            MagicLinkToken magicLink = findMagicLinkToken(token);
            
            if (magicLink == null) {
                LoggerUtil.warn("Invalid magic link token attempted");
                throw new AuthenticationException("Invalid or expired link. Please request a new one.");
            }
            
            // Check if already used
            if (magicLink.usedAt != null) {
                LoggerUtil.warn("Attempted to reuse magic link for email: " + maskEmail(magicLink.email));
                throw new AuthenticationException("This link has already been used. Please request a new one.");
            }
            
            // Check expiration
            if (LocalDateTime.now().isAfter(magicLink.expiresAt)) {
                LoggerUtil.warn("Expired magic link for email: " + maskEmail(magicLink.email));
                throw new AuthenticationException("This link has expired. Please request a new one.");
            }
            
            // Mark token as used
            markTokenAsUsed(token);
            
            // Get or create user
            User user;
            if (magicLink.userId != null) {
                user = findUserById(magicLink.userId);
                if (user == null) {
                    // User was deleted, create new one
                    user = createEmailUser(magicLink.email);
                }
            } else {
                // New user, create account
                user = createEmailUser(magicLink.email);
            }
            
            LoggerUtil.info("Magic link validated for: " + maskEmail(magicLink.email) + " (userId: " + user.id() + ")");
            
            return user;
            
        } catch (AuthenticationException e) {
            throw e;
        } catch (SQLException e) {
            LoggerUtil.error("Database error validating magic link: " + e.getMessage());
            throw new AuthenticationException("An error occurred. Please try again.", e);
        }
    }
    
    /**
     * Clean up expired tokens from the database.
     * Should be called periodically (e.g., daily).
     */
    public int cleanupExpiredTokens() {
        String sql = "DELETE FROM magic_link_tokens WHERE expires_at < ?";
        
        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, LocalDateTime.now().format(DB_DATETIME_FORMAT));
            int deleted = stmt.executeUpdate();
            
            if (deleted > 0) {
                LoggerUtil.info("Cleaned up " + deleted + " expired magic link tokens");
            }
            
            return deleted;
            
        } catch (SQLException e) {
            LoggerUtil.error("Failed to cleanup expired tokens: " + e.getMessage());
            return 0;
        }
    }
    
    // ==================== Private Helper Methods ====================
    
    private String generateSecureToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    private boolean isValidEmail(String email) {
        // Basic email validation
        return email != null && 
               email.contains("@") && 
               email.contains(".") &&
               email.indexOf("@") < email.lastIndexOf(".") &&
               email.length() <= 254;
    }
    
    private boolean isRateLimited(String email) {
        java.util.List<Long> requests = rateLimitMap.get(email);
        if (requests == null) {
            return false;
        }
        
        long oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000);
        
        // Remove old requests
        requests.removeIf(timestamp -> timestamp < oneHourAgo);
        
        return requests.size() >= rateLimitPerHour;
    }
    
    private void recordRateLimitRequest(String email) {
        rateLimitMap.computeIfAbsent(email, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                    .add(System.currentTimeMillis());
    }
    
    private Integer findUserIdByEmail(String email) throws SQLException {
        String sql = "SELECT id FROM users WHERE email = ? AND is_active = 1";
        
        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("id");
            }
            
            return null;
        }
    }
    
    private void storeMagicLinkToken(String email, String token, Integer userId, 
                                     LocalDateTime expiresAt, String ipAddress) throws SQLException {
        String sql = """
            INSERT INTO magic_link_tokens (email, token, user_id, expires_at, ip_address)
            VALUES (?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, email);
            stmt.setString(2, token);
            if (userId != null) {
                stmt.setInt(3, userId);
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            stmt.setString(4, expiresAt.format(DB_DATETIME_FORMAT));
            stmt.setString(5, ipAddress);
            
            stmt.executeUpdate();
        }
    }
    
    private MagicLinkToken findMagicLinkToken(String token) throws SQLException {
        String sql = "SELECT id, email, token, user_id, expires_at, used_at FROM magic_link_tokens WHERE token = ?";
        
        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, token);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return new MagicLinkToken(
                    rs.getInt("id"),
                    rs.getString("email"),
                    rs.getString("token"),
                    rs.getObject("user_id") != null ? rs.getInt("user_id") : null,
                    LocalDateTime.parse(rs.getString("expires_at"), DB_DATETIME_FORMAT),
                    rs.getString("used_at") != null ? LocalDateTime.parse(rs.getString("used_at"), DB_DATETIME_FORMAT) : null
                );
            }
            
            return null;
        }
    }
    
    private void markTokenAsUsed(String token) throws SQLException {
        String sql = "UPDATE magic_link_tokens SET used_at = ? WHERE token = ?";
        
        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, LocalDateTime.now().format(DB_DATETIME_FORMAT));
            stmt.setString(2, token);
            stmt.executeUpdate();
        }
    }
    
    private User findUserById(int userId) throws SQLException {
        String sql = """
            SELECT id, auth_provider, x_user_id, x_username, x_display_name,
                   discord_user_id, discord_username, discord_display_name,
                   email, created_at, is_active
            FROM users WHERE id = ?
        """;
        
        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return User.fromDatabase(
                    rs.getInt("id"),
                    rs.getString("auth_provider"),
                    rs.getString("x_user_id"),
                    rs.getString("x_username"),
                    rs.getString("x_display_name"),
                    rs.getString("discord_user_id"),
                    rs.getString("discord_username"),
                    rs.getString("discord_display_name"),
                    rs.getString("email"),
                    LocalDateTime.parse(rs.getString("created_at"), DB_DATETIME_FORMAT),
                    rs.getBoolean("is_active")
                );
            }
            
            return null;
        }
    }
    
    private User createEmailUser(String email) throws SQLException {
        String sql = """
            INSERT INTO users (auth_provider, email, is_active, created_at)
            VALUES (?, ?, 1, ?)
        """;
        
        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setString(1, User.PROVIDER_EMAIL);
            stmt.setString(2, email);
            stmt.setString(3, LocalDateTime.now().format(DB_DATETIME_FORMAT));
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating email user failed, no rows affected.");
            }
            
            // Try getGeneratedKeys first
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    int userId = keys.getInt(1);
                    LoggerUtil.info("Created new email user: " + maskEmail(email) + " (userId: " + userId + ")");
                    
                    return User.fromDatabase(
                        userId,
                        User.PROVIDER_EMAIL,
                        null, null, null, // No X
                        null, null, null, // No Discord
                        email,
                        LocalDateTime.now(),
                        true
                    );
                }
            }
            
            // Fallback: Use SQLite's last_insert_rowid()
            LoggerUtil.debug("getGeneratedKeys() failed, falling back to last_insert_rowid()");
            try (Statement lastIdStmt = conn.createStatement();
                 ResultSet rs = lastIdStmt.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    int userId = rs.getInt(1);
                    LoggerUtil.info("Created new email user (via fallback): " + maskEmail(email) + " (userId: " + userId + ")");
                    
                    return User.fromDatabase(
                        userId,
                        User.PROVIDER_EMAIL,
                        null, null, null, // No X
                        null, null, null, // No Discord
                        email,
                        LocalDateTime.now(),
                        true
                    );
                }
            }
            
            throw new SQLException("Failed to get generated user ID after insert");
        }
    }
    
    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***@" + email.substring(atIndex + 1);
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
    
    // ==================== Inner Classes ====================
    
    private record MagicLinkToken(
        int id,
        String email,
        String token,
        Integer userId,
        LocalDateTime expiresAt,
        LocalDateTime usedAt
    ) {}
    
    /**
     * Exception thrown for authentication failures.
     */
    public static class AuthenticationException extends Exception {
        public AuthenticationException(String message) {
            super(message);
        }
        
        public AuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

