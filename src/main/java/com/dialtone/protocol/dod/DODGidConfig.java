/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.dod;

/**
 * Configuration for binary GID extraction from DOD token frames.
 *
 * <p>Used by f1 and f2 tokens which have GID at fixed offsets within the frame.
 * K1 tokens do not use this as they extract GID via FDO decompilation.
 *
 * <p>Frame structure varies by token type:
 * <ul>
 *   <li>f2: token(2) + GID(4) - GID at offset+2, needs 6 bytes after token</li>
 *   <li>f1: token(2) + streamId(2) + flags(4) + marker(2) + GID(4) - GID at offset+10, needs 14 bytes</li>
 * </ul>
 */
public record DODGidConfig(
    byte tokenByte1,      // First byte of token (e.g., 0x66 for 'f')
    byte tokenByte2,      // Second byte of token (e.g., 0x31 for '1' or 0x32 for '2')
    int gidOffset,        // Offset from token position to GID (e.g., 2 for f2, 10 for f1)
    int minRequiredBytes  // Minimum bytes needed after token offset for complete GID
) {
    /**
     * f2 token: GID at offset+2, needs 6 bytes minimum after token.
     * Frame structure: f2(2) + GID(4)
     */
    public static final DODGidConfig F2 = new DODGidConfig(
        (byte) 0x66, (byte) 0x32, 2, 6);

    /**
     * f1 token: GID at offset+10, needs 14 bytes minimum after token.
     * Frame structure: f1(2) + streamId(2) + flags(4) + marker(2) + GID(4)
     */
    public static final DODGidConfig F1 = new DODGidConfig(
        (byte) 0x66, (byte) 0x31, 10, 14);
}
