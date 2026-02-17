/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.security;

import com.dialtone.utils.LoggerUtil;

import java.util.regex.Pattern;

/**
 * Security service for input validation and sanitization.
 *
 * Provides methods to validate and sanitize user input to prevent
 * XSS, injection attacks, and other security vulnerabilities.
 */
public class SecurityService {

    // Regex patterns for validation
    private static final Pattern SCREENNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]{1,10}$");
    private static final Pattern SAFE_TEXT_PATTERN = Pattern.compile("^[\\p{L}\\p{N}\\p{P}\\p{Z}]{0,100}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    // Characters that should be escaped/removed for XSS prevention
    private static final Pattern XSS_PATTERN = Pattern.compile("[<>\"'&\\x00-\\x1f\\x7f-\\x9f]");

    // Reserved prefix for ephemeral guest accounts
    private static final String RESERVED_PREFIX = "~";

    /**
     * Validates that a screenname meets Dialtone format requirements.
     * Must be 1-10 characters, alphanumeric only.
     * Cannot start with ~ (reserved for ephemeral guest accounts).
     *
     * @param screenname The screenname to validate
     * @return ValidationResult with success status and error message if invalid
     */
    public static ValidationResult validateScreenname(String screenname) {
        if (screenname == null) {
            return ValidationResult.invalid("Screenname cannot be null");
        }

        String trimmed = screenname.trim();
        if (trimmed.isEmpty()) {
            return ValidationResult.invalid("Screenname cannot be empty");
        }

        // Explicit check for reserved prefix (ephemeral guests only)
        if (trimmed.startsWith(RESERVED_PREFIX)) {
            return ValidationResult.invalid("Screenname cannot start with ~ (reserved for guests)");
        }

        if (trimmed.length() > 10) {
            return ValidationResult.invalid("Screenname must be 10 characters or less");
        }

        if (!SCREENNAME_PATTERN.matcher(trimmed).matches()) {
            return ValidationResult.invalid("Screenname can only contain letters and numbers");
        }

        return ValidationResult.valid(trimmed);
    }

    /**
     * Validates that a password meets protocol requirements.
     * Must be 1-8 characters for protocol compatibility.
     *
     * @param password The password to validate
     * @return ValidationResult with success status and error message if invalid
     */
    public static ValidationResult validatePassword(String password) {
        if (password == null) {
            return ValidationResult.invalid("Password cannot be null");
        }

        if (password.isEmpty()) {
            return ValidationResult.invalid("Password cannot be empty");
        }

        if (password.length() > 8) {
            return ValidationResult.invalid("Password must be 8 characters or less");
        }

        // Check for null bytes and control characters
        if (password.contains("\0") || password.matches(".*[\\x00-\\x1f\\x7f-\\x9f].*")) {
            return ValidationResult.invalid("Password contains invalid characters");
        }

        return ValidationResult.valid(password);
    }

    /**
     * Sanitizes display name or other user-provided text to prevent XSS.
     * Removes potentially dangerous characters while preserving readability.
     *
     * @param input The input text to sanitize
     * @return Sanitized text safe for display
     */
    public static String sanitizeDisplayText(String input) {
        if (input == null) {
            return null;
        }

        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        // Limit length to prevent DoS
        if (trimmed.length() > 100) {
            trimmed = trimmed.substring(0, 100);
        }

        // Remove XSS-prone characters
        String sanitized = XSS_PATTERN.matcher(trimmed).replaceAll("");

        // Normalize whitespace
        sanitized = sanitized.replaceAll("\\s+", " ");

        return sanitized;
    }

    /**
     * Validates and sanitizes X username (without @ symbol).
     *
     * @param username The X username to validate
     * @return ValidationResult with sanitized username or error message
     */
    public static ValidationResult validateXUsername(String username) {
        if (username == null) {
            return ValidationResult.invalid("X username cannot be null");
        }

        String trimmed = username.trim();
        if (trimmed.isEmpty()) {
            return ValidationResult.invalid("X username cannot be empty");
        }

        // Remove @ if present (users sometimes include it)
        if (trimmed.startsWith("@")) {
            trimmed = trimmed.substring(1);
        }

        // X usernames are 1-15 characters, alphanumeric + underscore
        if (trimmed.length() > 15) {
            return ValidationResult.invalid("X username too long");
        }

        if (!trimmed.matches("^[a-zA-Z0-9_]{1,15}$")) {
            return ValidationResult.invalid("X username contains invalid characters");
        }

        return ValidationResult.valid(trimmed);
    }

    /**
     * Validates that a string is safe for use in SQL queries.
     * This is a defense-in-depth measure; we should still use prepared statements.
     *
     * @param input The input to validate
     * @return true if input appears safe, false otherwise
     */
    public static boolean isSqlSafe(String input) {
        if (input == null) {
            return true; // null is safe
        }

        // Check for SQL injection patterns
        String lower = input.toLowerCase();
        String[] sqlKeywords = {
            "select", "insert", "update", "delete", "drop", "create",
            "alter", "exec", "execute", "union", "script", "javascript"
        };

        for (String keyword : sqlKeywords) {
            if (lower.contains(keyword)) {
                LoggerUtil.warn("Potentially unsafe SQL input detected: " + keyword);
                return false;
            }
        }

        // Check for SQL comment patterns
        if (input.contains("--") || input.contains("/*") || input.contains("*/")) {
            LoggerUtil.warn("SQL comment pattern detected in input");
            return false;
        }

        return true;
    }

    /**
     * Validates HTTP request size to prevent DoS attacks.
     *
     * @param contentLength The content length of the request
     * @param maxSize Maximum allowed size in bytes
     * @return true if size is acceptable, false otherwise
     */
    public static boolean validateRequestSize(long contentLength, long maxSize) {
        if (contentLength < 0) {
            return false; // Invalid content length
        }

        if (contentLength > maxSize) {
            LoggerUtil.warn("Request size too large: " + contentLength + " bytes (max: " + maxSize + ")");
            return false;
        }

        return true;
    }

    /**
     * Generates a secure random string for use as CSRF tokens, state parameters, etc.
     *
     * @param length The length of the random string
     * @return Cryptographically secure random string
     */
    public static String generateSecureToken(int length) {
        StringBuilder result = new StringBuilder();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int i = 0; i < length; i++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }

        return result.toString();
    }

    /**
     * Validates that an ID parameter is a positive integer.
     *
     * @param idString The ID string to validate
     * @param fieldName The name of the field for error messages
     * @return ValidationResult with parsed ID or error message
     */
    public static ValidationResult validateId(String idString, String fieldName) {
        if (idString == null || idString.trim().isEmpty()) {
            return ValidationResult.invalid(fieldName + " cannot be empty");
        }

        try {
            int id = Integer.parseInt(idString.trim());
            if (id <= 0) {
                return ValidationResult.invalid(fieldName + " must be a positive number");
            }
            return ValidationResult.valid(String.valueOf(id));
        } catch (NumberFormatException e) {
            return ValidationResult.invalid(fieldName + " must be a valid number");
        }
    }

    /**
     * Result of input validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String value;
        private final String errorMessage;

        private ValidationResult(boolean valid, String value, String errorMessage) {
            this.valid = valid;
            this.value = value;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid(String value) {
            return new ValidationResult(true, value, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, null, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getValue() {
            return value;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}