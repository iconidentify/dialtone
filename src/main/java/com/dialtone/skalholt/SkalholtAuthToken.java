/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.skalholt;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * Represents an authentication token for the Skalholt MUD HTTP API.
 * The token is captured from telnet output in the format "AUTH - &lt;base64token&gt;"
 * where the base64 token encodes "username:password".
 */
public final class SkalholtAuthToken {

    private static final String AUTH_PREFIX = "AUTH - ";

    private final String base64Token;
    private final String username;
    private final long capturedAtMs;

    /**
     * Creates a new SkalholtAuthToken from a base64-encoded token.
     *
     * @param base64Token the base64-encoded "username:password" string
     * @throws IllegalArgumentException if the token is null, empty, or invalid
     */
    public SkalholtAuthToken(String base64Token) {
        if (base64Token == null || base64Token.isBlank()) {
            throw new IllegalArgumentException("Token cannot be null or blank");
        }
        this.base64Token = base64Token.trim();
        this.username = extractUsername(this.base64Token);
        this.capturedAtMs = System.currentTimeMillis();
    }

    /**
     * Attempts to parse an auth token from a telnet line.
     *
     * @param line the telnet line to parse
     * @return a SkalholtAuthToken if the line contains an auth token, null otherwise
     */
    public static SkalholtAuthToken fromTelnetLine(String line) {
        if (line == null || !line.startsWith(AUTH_PREFIX)) {
            return null;
        }
        String token = line.substring(AUTH_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return null;
        }
        try {
            return new SkalholtAuthToken(token);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Checks if a telnet line contains an auth token.
     *
     * @param line the line to check
     * @return true if the line is an AUTH line
     */
    public static boolean isAuthLine(String line) {
        return line != null && line.startsWith(AUTH_PREFIX);
    }

    /**
     * Gets the raw base64 token.
     *
     * @return the base64-encoded token
     */
    public String getBase64Token() {
        return base64Token;
    }

    /**
     * Gets the username extracted from the token.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets the timestamp when this token was captured.
     *
     * @return capture time in milliseconds since epoch
     */
    public long getCapturedAtMs() {
        return capturedAtMs;
    }

    /**
     * Gets the HTTP Basic Authorization header value.
     *
     * @return the value for the Authorization header (e.g., "Basic ZmVyb0Nvb2w6cGFzc3Bhc3M=")
     */
    public String getBasicAuthHeader() {
        return "Basic " + base64Token;
    }

    /**
     * Extracts the username from a base64 "username:password" token.
     */
    private static String extractUsername(String base64Token) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Token);
            String credentials = new String(decoded, StandardCharsets.UTF_8);
            int colonIndex = credentials.indexOf(':');
            if (colonIndex > 0) {
                return credentials.substring(0, colonIndex);
            }
            // No colon found, treat entire decoded string as username
            return credentials;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid base64 token: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SkalholtAuthToken that = (SkalholtAuthToken) o;
        return Objects.equals(base64Token, that.base64Token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(base64Token);
    }

    @Override
    public String toString() {
        return "SkalholtAuthToken{username='" + username + "', capturedAt=" + capturedAtMs + "}";
    }
}
