/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.core;

import com.dialtone.protocol.SessionContext;
import io.netty.channel.ChannelHandlerContext;

/**
 * Interface for token handlers that process specific protocol tokens.
 * Handlers are responsible for processing tokens and managing their own state.
 */
public interface TokenHandler {
    /**
     * Check if this handler can process the given token.
     *
     * @param token The token string (e.g., "Aa", "iS", "Dd")
     * @return true if this handler can process the token
     */
    boolean canHandle(String token);

    /**
     * Handle the token frame.
     *
     * @param ctx     The channel context
     * @param frame   The raw frame bytes
     * @param session The session context
     * @throws Exception if processing fails
     */
    void handle(ChannelHandlerContext ctx, byte[] frame, SessionContext session) throws Exception;
}
