/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.im;

/**
 * Configuration record that encapsulates the behavioral differences between iS and iT instant message tokens.
 *
 * <p>iS (Instant Message with ACK): Sends ACK response via {@code AckIsFdoBuilder}, no echo.
 * <p>iT (Instant Message No-ACK): Sends noop response via {@code NoopFdoBuilder}, echoes message back to sender.
 *
 * <p>This abstraction allows the handler logic to be unified while preserving the distinct protocol behaviors.
 * Response FDO is generated via DSL builders (not templates).
 */
public record IMTokenBehavior(
    String tokenName,           // "iS" or "iT" - for logging
    String logLabel,            // Label for P3ChunkEnqueuer logging
    boolean echoToSender        // Whether to echo the message back to sender
) {
    /**
     * iS token behavior: Sends ACK response (via AckIsFdoBuilder), no echo to sender.
     */
    public static final IMTokenBehavior IS = new IMTokenBehavior("iS", "IS_ACK", false);

    /**
     * iT token behavior: Sends noop response (via NoopFdoBuilder), echoes message back to sender.
     */
    public static final IMTokenBehavior IT = new IMTokenBehavior("iT", "IS_NOOP", true);
}
