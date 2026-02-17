/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;

import java.time.Duration;
import java.time.Instant;

public final class SessionContext {
    private static final String GUEST_PREFIX = "Guest";

    // Router/service channel binding (from ME)
    private int routerChannelId = 0;

    // Authentication state
    private String username;
    private String password;  // Stored for Skalholt SSO, cleared on disconnect
    private boolean authenticated = false;
    private boolean ephemeral = false;  // True for guest sessions created via fallback

    // Client platform (Mac, Windows, etc.)
    private ClientPlatform platform = ClientPlatform.UNKNOWN;

    // INIT packet data (parsed from 0xA3 packet during handshake)
    private InitPacketData initPacketData;

    // Connection time tracking
    private final Instant connectionTime = Instant.now();

    public int getRouterChannelId() { return routerChannelId; }
    public void setRouterChannelId(int routerChannelId) { this.routerChannelId = routerChannelId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public void clearPassword() { this.password = null; }

    public boolean isAuthenticated() { return authenticated; }

    public boolean isEphemeral() { return ephemeral; }
    public void setEphemeral(boolean ephemeral) { this.ephemeral = ephemeral; }

    public ClientPlatform getPlatform() { return platform; }
    public void setPlatform(ClientPlatform platform) { this.platform = platform; }

    public InitPacketData getInitPacketData() { return initPacketData; }
    public void setInitPacketData(InitPacketData initPacketData) { this.initPacketData = initPacketData; }

    /**
     * Check if INIT packet data has been received and parsed.
     *
     * @return true if INIT data is available
     */
    public boolean hasInitData() {
        return initPacketData != null;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
        if (authenticated && username == null) {
            throw new IllegalStateException(
                "Cannot authenticate session without username");
        }
    }

    /**
     * Get display name for user - never returns null.
     * For authenticated users, returns their username.
     * For unauthenticated users, returns a generated guest name.
     *
     * @return Display name (never null)
     */
    public String getDisplayName() {
        if (username != null && !username.trim().isEmpty()) {
            return username;
        }

        if (!authenticated) {
            return generateGuestName();
        }

        throw new IllegalStateException(
            "Authenticated session has null username - this is a bug");
    }

    /**
     * Generate unique guest name for unauthenticated users.
     *
     * @return Generated guest name
     */
    private String generateGuestName() {
        return GUEST_PREFIX + Integer.toHexString(hashCode()).toUpperCase();
    }

    /**
     * Get connection timestamp.
     *
     * @return Instant when session was created
     */
    public Instant getConnectionTime() {
        return connectionTime;
    }

    /**
     * Get formatted session duration from connection time to now.
     * Format: "HH:MM:SS" or "DDd HH:MM:SS" for sessions longer than 24 hours.
     *
     * @return Formatted duration string
     */
    public String getSessionDuration() {
        Duration duration = Duration.between(connectionTime, Instant.now());

        long seconds = duration.getSeconds();
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (days > 0) {
            return String.format("%dd %02d:%02d:%02d", days, hours, minutes, secs);
        } else {
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        }
    }
  }


