/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;

/**
 * Metadata extracted from P3 protocol frames.
 * Contains frame-level information including Stream ID for DOD responses.
 */
public class P3FrameMetadata {
    private final int streamId;
    private final String token;
    private final int flags;
    private final int sequence;
    private final byte[] fdoPayload;

    public P3FrameMetadata(int streamId, String token, int flags, int sequence, byte[] fdoPayload) {
        this.streamId = streamId;
        this.token = token;
        this.flags = flags;
        this.sequence = sequence;
        this.fdoPayload = fdoPayload;
    }

    public int getStreamId() {
        return streamId;
    }

    public String getToken() {
        return token;
    }

    public int getFlags() {
        return flags;
    }

    public int getSequence() {
        return sequence;
    }

    public byte[] getFdoPayload() {
        return fdoPayload;
    }

    @Override
    public String toString() {
        return String.format("P3FrameMetadata{streamId=0x%04X(%d), token='%s', flags=0x%04X, seq=%d, payloadSize=%d}",
                streamId, streamId, token, flags, sequence, fdoPayload != null ? fdoPayload.length : 0);
    }
}