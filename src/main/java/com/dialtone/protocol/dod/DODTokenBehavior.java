/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.dod;

import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.builders.F1DodFailedFdoBuilder;
import com.dialtone.fdo.dsl.builders.NoopFdoBuilder;

/**
 * Configuration record encapsulating behavioral differences between f2, f1, and K1 DOD tokens.
 *
 * <p>Follows the same pattern as IMTokenBehavior for iS/iT unification.
 *
 * <p>Token behavior summary:
 * <ul>
 *   <li>f2: Direct GID in frame at offset+2, uses sendErrorAck for errors</li>
 *   <li>f1: Direct GID in frame at offset+10, uses FDO template for errors</li>
 *   <li>K1: GID from FDO de_data decompilation, uses noop.fdo.txt for errors</li>
 * </ul>
 *
 * <p>This abstraction allows the handler logic to be unified while preserving
 * the distinct protocol behaviors for each token type.
 */
public record DODTokenBehavior(
    String tokenName,                    // "f2", "f1", or "K1" - for logging
    GidExtractionMethod gidExtraction,   // How to extract the GID from the frame
    ErrorResponseType errorResponse,     // How to respond on error
    FdoBuilder.Static errorBuilder,      // Type-safe FDO builder for error response (null if using ACK)
    String successLogLabel,              // Label for P3ChunkEnqueuer logging on success
    String emptyLogLabel,                // Label for logging when no content found
    boolean usesFoundFlag                // Whether response type has 'found' field
) {
    /**
     * GID extraction strategies for different token types.
     */
    public enum GidExtractionMethod {
        /** GID is at a fixed offset from token position in binary frame (f2, f1) */
        BINARY_OFFSET,
        /** GID is extracted from decompiled FDO de_data field (K1) */
        FDO_DECOMPILE
    }

    /**
     * Error response strategies.
     */
    public enum ErrorResponseType {
        /** Send simple ACK frame (f2) */
        SEND_ACK,
        /** Compile and send FDO template (f1, K1) */
        SEND_FDO_TEMPLATE
    }

    /**
     * f2 token behavior: Direct GID extraction at offset+2, ACK-based error responses.
     */
    public static final DODTokenBehavior F2 = new DODTokenBehavior(
        "f2",
        GidExtractionMethod.BINARY_OFFSET,
        ErrorResponseType.SEND_ACK,
        null,  // Uses sendErrorAck, no builder
        "F2_DOD_RESPONSE",
        "F2_CTRL_ACK",
        false  // DodResponse does not have 'found' flag
    );

    /**
     * f1 token behavior: Direct GID extraction at offset+10, FDO-based error responses.
     */
    public static final DODTokenBehavior F1 = new DODTokenBehavior(
        "f1",
        GidExtractionMethod.BINARY_OFFSET,
        ErrorResponseType.SEND_FDO_TEMPLATE,
        F1DodFailedFdoBuilder.INSTANCE,
        "F1_ATOM_RESPONSE",
        "F1_DOD_FAILED",
        true   // AtomStreamResponse has 'found' flag
    );

    /**
     * K1 token behavior: FDO-based GID extraction, noop FDO for errors.
     */
    public static final DODTokenBehavior K1 = new DODTokenBehavior(
        "K1",
        GidExtractionMethod.FDO_DECOMPILE,
        ErrorResponseType.SEND_FDO_TEMPLATE,
        NoopFdoBuilder.INSTANCE,
        "K1_RESPONSE",
        "K1_ACK",
        true   // K1Response has 'found' flag
    );
}
