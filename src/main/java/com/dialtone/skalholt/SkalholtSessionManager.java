/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.skalholt;

import com.skalholt.events.SkalholtEventType;
import com.dialtone.utils.LoggerUtil;

import java.util.function.Consumer;

/**
 * Manages the Skalholt API session for a single Skalholt telnet connection.
 *
 * <p>Coordinates:
 * <ul>
 *   <li>Auth token capture from telnet output</li>
 *   <li>EventStream client lifecycle</li>
 *   <li>Event delivery to registered handlers</li>
 * </ul>
 */
public class SkalholtSessionManager {

    private final String host;
    private final int port;
    private final int timeoutMs;
    private final boolean enabled;

    private SkalholtAuthToken authToken;
    private SkalholtEventStreamClient eventStreamClient;
    private Consumer<SkalholtSseEvent> eventHandler;

    /**
     * Creates a new SkalholtSessionManager with configuration.
     *
     * @param host Skalholt API host
     * @param port Skalholt API port
     * @param timeoutMs connection timeout
     * @param enabled whether Skalholt integration is enabled
     */
    public SkalholtSessionManager(String host, int port, int timeoutMs, boolean enabled) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
        this.enabled = enabled;
    }

    /**
     * Creates a disabled SkalholtSessionManager.
     *
     * @return a manager that ignores all operations
     */
    public static SkalholtSessionManager disabled() {
        return new SkalholtSessionManager("", 0, 0, false);
    }

    /**
     * Checks if Skalholt integration is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the event handler to receive Skalholt events.
     *
     * @param handler the event handler
     */
    public void setEventHandler(Consumer<SkalholtSseEvent> handler) {
        this.eventHandler = handler;
    }

    /**
     * Called when an AUTH token is received from telnet output.
     *
     * @param base64Token the base64-encoded token
     */
    public void onAuthTokenReceived(String base64Token) {
        if (!enabled) {
            return;
        }

        try {
            this.authToken = new SkalholtAuthToken(base64Token);
            LoggerUtil.info("[SkalholtSession] Auth token captured for user: " + authToken.getUsername());
        } catch (IllegalArgumentException e) {
            LoggerUtil.error("[SkalholtSession] Invalid auth token: " + e.getMessage());
        }
    }

    /**
     * Checks if an auth token has been captured.
     *
     * @return true if auth token is available
     */
    public boolean hasAuthToken() {
        return authToken != null;
    }

    /**
     * Gets the captured auth token.
     *
     * @return the auth token, or null if not yet captured
     */
    public SkalholtAuthToken getAuthToken() {
        return authToken;
    }

    /**
     * Starts the EventStream connection.
     * Must be called after auth token is received.
     */
    public void startEventStream() {
        if (!enabled) {
            LoggerUtil.debug("[SkalholtSession] EventStream disabled, skipping");
            return;
        }

        if (authToken == null) {
            LoggerUtil.warn("[SkalholtSession] Cannot start EventStream: no auth token");
            return;
        }

        if (eventStreamClient != null && eventStreamClient.isConnected()) {
            LoggerUtil.warn("[SkalholtSession] EventStream already connected");
            return;
        }

        LoggerUtil.info("[SkalholtSession] Starting EventStream for " + authToken.getUsername());

        eventStreamClient = new SkalholtEventStreamClient(
                host,
                port,
                authToken,
                this::handleEvent,
                this::handleError,
                timeoutMs
        );

        eventStreamClient.connect();
    }

    /**
     * Handles an event from the EventStream.
     */
    private void handleEvent(SkalholtSseEvent event) {
        SkalholtEventType type = event.getSkalholtEventType();

        // Log event with type-specific details
        if (type != null) {
            switch (type) {
                case GOSSIP -> event.getGossipPayload().ifPresent(gossip ->
                        LoggerUtil.info("[SkalholtSession] GOSSIP: [" + gossip.getTopic() + "] " +
                                gossip.getName() + ": " + gossip.getMessage()));

                case PLAYER_UPDATE_HEALTH -> event.getHealthUpdatePayload().ifPresent(health ->
                        LoggerUtil.info("[SkalholtSession] HEALTH: " + health.getPlayerName() +
                                " " + (health.getAmount() < 0 ? "took" : "healed") +
                                " " + Math.abs(health.getAmount()) + " from " + health.getNpcId()));

                case DRAW_MAP -> event.getDrawMapPayload().ifPresent(map ->
                        LoggerUtil.info("[SkalholtSession] MAP: received " + map.getMap().split("\n").length + " lines"));

                case NPC_KILL -> event.getNpcKillPayload().ifPresent(kill ->
                        LoggerUtil.info("[SkalholtSession] NPC_KILL: " + kill.getName() +
                                " killed, +" + kill.getXpEarned() + " XP"));

                case NPC_DAMAGE -> event.getNpcDamagePayload().ifPresent(damage ->
                        LoggerUtil.debug("[SkalholtSession] NPC_DAMAGE: " + damage.getDamageAmount() +
                                " to " + damage.getNpcId()));

                case USERS -> event.getUsersPayload().ifPresent(users ->
                        LoggerUtil.info("[SkalholtSession] USERS: " + users.getUserMap().size() + " online"));

                case PLAYERDATA -> LoggerUtil.debug("[SkalholtSession] PLAYERDATA: received for " +
                        event.getPlayerId());
            }
        } else {
            LoggerUtil.debug("[SkalholtSession] Unknown event type, uuid=" + event.getUuid());
        }

        // Forward to registered handler if present
        if (eventHandler != null) {
            try {
                eventHandler.accept(event);
            } catch (Exception e) {
                LoggerUtil.error("[SkalholtSession] Error in event handler: " + e.getMessage());
            }
        }
    }

    /**
     * Handles an error from the EventStream.
     */
    private void handleError(Throwable error) {
        LoggerUtil.warn("[SkalholtSession] EventStream error: " + error.getMessage());
    }

    /**
     * Checks if the EventStream is connected.
     *
     * @return true if connected
     */
    public boolean isEventStreamConnected() {
        return eventStreamClient != null && eventStreamClient.isConnected();
    }

    /**
     * Cleans up resources when the session ends.
     */
    public void cleanup() {
        if (eventStreamClient != null) {
            LoggerUtil.info("[SkalholtSession] Cleaning up EventStream");
            eventStreamClient.close();
            eventStreamClient = null;
        }
        authToken = null;
    }
}
