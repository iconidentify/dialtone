/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.aol.core;

/**
 * Parsing and mutation utilities for AOL frames.
 * Centralizes length/CRC stamping and ASCII preview.
 */
public final class FrameCodec {

    private FrameCodec() {}

    /**
     * Parses a raw 0x5A frame into a Frame read model, validating CRC.
     */
    public static Frame parse(byte[] raw) {
        if (!ProtocolConstants.isAolFrame(raw) || raw.length < ProtocolConstants.MIN_FRAME_SIZE) {
            throw new IllegalArgumentException("Not an AOL frame or too short");
        }
        int payloadLen = raw.length > ProtocolConstants.IDX_LEN_LO
                ? (((raw[ProtocolConstants.IDX_LEN_HI] & 0xFF) << 8) | (raw[ProtocolConstants.IDX_LEN_LO] & 0xFF))
                : 0;
        // Validate total length against declared length (allow optional trailing 0x0D)
        int expected = 6 + payloadLen;
        if (raw.length != expected && !(raw.length == expected + 1 && raw[raw.length - 1] == 0x0D)) {
            throw new IllegalArgumentException("Length mismatch: declared=" + payloadLen + " total=" + raw.length);
        }
        int tx = raw.length > ProtocolConstants.IDX_TX ? (raw[ProtocolConstants.IDX_TX] & 0xFF) : 0;
        int rx = raw.length > ProtocolConstants.IDX_RX ? (raw[ProtocolConstants.IDX_RX] & 0xFF) : 0;
        int type = raw.length > ProtocolConstants.IDX_TYPE ? (raw[ProtocolConstants.IDX_TYPE] & 0xFF) : 0;
        boolean crcOk = false;
        if (raw.length >= (ProtocolConstants.IDX_LEN_LO + 1)) {
            int crcWire = ((raw[ProtocolConstants.IDX_CRC_HI] & 0xFF) << 8) | (raw[ProtocolConstants.IDX_CRC_LO] & 0xFF);
            int crcCalc = Crc16Ibm.compute(raw, ProtocolConstants.IDX_LEN_HI, raw.length - (ProtocolConstants.IDX_MAGIC + 4));
            crcOk = (crcWire == crcCalc);
        }
        return new Frame(raw, tx, rx, type, payloadLen, crcOk);
    }

    /**
     * Extracts a printable two-character ASCII token (e.g., "AT", "]K").
     * Returns "9B" for short control frames, or null when not applicable.
     */
    public static String extractTokenAscii(byte[] frame) {
        if (frame == null || frame.length < ProtocolConstants.SHORT_FRAME_SIZE) return null;
        if ((frame[ProtocolConstants.IDX_MAGIC] & 0xFF) != ProtocolConstants.MAGIC) return null;
        // 9B short control
        int len = ((frame[ProtocolConstants.IDX_LEN_HI] & 0xFF) << 8) | (frame[ProtocolConstants.IDX_LEN_LO] & 0xFF);
        if (frame.length == ProtocolConstants.SHORT_FRAME_SIZE && len == 3) return "9B";
        if (frame.length < ProtocolConstants.MIN_FULL_FRAME_SIZE) return null;
        char c1 = (char) (frame[ProtocolConstants.IDX_TOKEN] & 0xFF);
        char c2 = (char) (frame[ProtocolConstants.IDX_TOKEN + 1] & 0xFF);
        if (ProtocolConstants.isPrintableChar(c1) && ProtocolConstants.isPrintableChar(c2)) {
            return "" + c1 + c2;
        }
        return null;
    }


    /**
     * Recomputes and writes the header CRC over bytes[3..(len-2)] into [1..2].
     * No-op for invalid frames.
     */
    public static void recomputeHeaderCrc(byte[] frame) {
        if (frame == null || frame.length < (ProtocolConstants.IDX_LEN_LO + 1) || !ProtocolConstants.isAolFrame(frame)) return;
        int crc = Crc16Ibm.compute(frame, ProtocolConstants.IDX_LEN_HI, frame.length - (ProtocolConstants.IDX_MAGIC + 4));
        frame[ProtocolConstants.IDX_CRC_HI] = (byte) ((crc >>> 8) & 0xFF);
        frame[ProtocolConstants.IDX_CRC_LO] = (byte) (crc & 0xFF);
    }

}


