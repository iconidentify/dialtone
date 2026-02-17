/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.aol.core;

/**
 * Immutable parsed view of a raw AOL frame.
 */
public final class Frame {
    public final byte[] raw;
    public final int tx;
    public final int rx;
    public final int type;
    public final int payloadLen;
    public final boolean crcOk;

    public Frame(byte[] raw, int tx, int rx, int type, int payloadLen, boolean crcOk) {
        this.raw = raw;
        this.tx = tx;
        this.rx = rx;
        this.type = type;
        this.payloadLen = payloadLen;
        this.crcOk = crcOk;
    }
}


