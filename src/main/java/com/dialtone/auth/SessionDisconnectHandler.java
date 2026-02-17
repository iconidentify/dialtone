/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.auth;

import java.util.concurrent.CompletableFuture;

/**
 * Handler for gracefully disconnecting user sessions.
 *
 * <p>This interface enables single-session enforcement by allowing UserRegistry
 * to trigger graceful disconnects when a user logs in from a new location,
 * without creating tight coupling to FDO/protocol-level disconnect logic.
 *
 * <p><b>Usage Pattern:</b>
 * <pre>
 * SessionDisconnectHandler handler = new RemoteDisconnectHandler();
 * userRegistry.register(username, ctx, pacer, platform, handler);
 * // If user already connected, handler will disconnect old session asynchronously
 * </pre>
 *
 * <p><b>Thread Safety:</b><br>
 * Implementations must ensure disconnect operations execute on the appropriate
 * Netty event loop to avoid concurrency issues.
 *
 * <p><b>Error Handling:</b><br>
 * If graceful disconnect fails, implementations should force-close the channel
 * to ensure old session is properly terminated.
 */
public interface SessionDisconnectHandler {

    /**
     * Disconnect a user session gracefully with a custom message.
     *
     * <p>This method should:
     * <ul>
     *   <li>Send logout FDO sequence with the specified message</li>
     *   <li>Close the channel after ensuring messages are sent</li>
     *   <li>Execute on the channel's event loop for thread safety</li>
     *   <li>Complete the future when disconnect is done or failed</li>
     * </ul>
     *
     * @param connection The user connection to disconnect (must not be null)
     * @param message The disconnect message to show to the user (e.g., "You've been signed on from another location")
     * @return CompletableFuture that completes when disconnect finishes (success or failure)
     */
    CompletableFuture<Void> disconnectSession(UserRegistry.UserConnection connection, String message);
}
