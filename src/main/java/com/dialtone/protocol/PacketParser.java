/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.aol.core.Hex;
import com.dialtone.aol.core.ProtocolConstants;

/**
 * Parser for AOL 3.0 P3 frames and atoms.
 * Combined with PacketProcessor functionality for packet parsing, token extraction, and logging.
 *
 * Frame header (big-endian):
 *  - token:   u16
 *  - flags:   u16
 *  - seq:     u16
 *  - length:  u16 (payload length only)
 *  - payload: bytes[length]
 *  - term:    0x0D (CR) (optional/tolerated)
 *
 * Atom format inside payload:
 *  - proto:  u8
 *  - atom:   u8
 *  - len:    u16 (big-endian)
 *  - value:  len bytes (can be zero)
 */
public final class PacketParser {

    private static final Set<Integer> loggedUnknownIds = new HashSet<>();

    public static final class ParsedAtom {
        public final int protocolId;
        public final int atomId;
        public final int length;
        public final int offsetInPayload; // start of value

        public ParsedAtom(int protocolId, int atomId, int length, int offsetInPayload) {
            this.protocolId = protocolId;
            this.atomId = atomId;
            this.length = length;
            this.offsetInPayload = offsetInPayload;
        }

        @Override
        public String toString() {
            return "(" + protocolId + "," + atomId + ")";
        }
    }

    public static final class ParsedFrame {
        public final int token;
        public final int flags;
        public final int seq;
        public final int payloadLength;
        public final byte[] raw;
        public final int payloadOffset; // offset within raw where payload starts
        public final List<ParsedAtom> atoms;

        public ParsedFrame(int token, int flags, int seq, int payloadLength,
                           byte[] raw, int payloadOffset, List<ParsedAtom> atoms) {
            this.token = token;
            this.flags = flags;
            this.seq = seq;
            this.payloadLength = payloadLength;
            this.raw = raw;
            this.payloadOffset = payloadOffset;
            this.atoms = Collections.unmodifiableList(atoms);
        }

        public boolean containsAtom(int protocolId, int atomId) {
            for (var a : atoms) {
                if (a.protocolId == protocolId && a.atomId == atomId) return true;
            }
            return false;
        }
    }

    public static ParsedFrame parse(byte[] data) {
        if (data == null || data.length < 8) {
            throw new IllegalArgumentException("Frame too short");
        }
        // Guard: don't parse 0x5A short controls here; handler should filter
        if (isFiveA(data) && data.length == com.dialtone.aol.core.ProtocolConstants.SHORT_FRAME_SIZE) {
            throw new IllegalArgumentException("Short control (9B) frame is not a P3 payload");
        }
        int token = u16(data, 0);
        int flags = u16(data, 2);
        int seq = u16(data, 4);
        int len = u16(data, 6);
        int payloadOffset = 8;
        int end = payloadOffset + len;
        if (end > data.length) {
            // Some captures may omit or include trailing 0x0D; cap at data length
            end = Math.min(end, data.length);
        }

        List<ParsedAtom> atoms = new ArrayList<>();
        int pos = payloadOffset;
        while (pos + 4 <= end) {
            int proto = u8(data, pos);
            int atom = u8(data, pos + 1);
            int alen = u16(data, pos + 2);
            int valueOffset = pos + 4;
            int next = valueOffset + alen;
            if (next > end) {
                // Malformed; stop parsing atoms but keep what we have
                break;
            }
            int id = (proto << 8) | atom;
            boolean isAtFamily = (token == 0x4154 || token == 0x4174 || token == 0x6154); // AT, At, aT
            if (isAtFamily && !isKnownAtom(proto, atom)) {
                if (loggedUnknownIds.add(id)) {
                    if (LoggerUtil.isDebugEnabled()) {
                        final int capPos = pos;
                        final int capEnd = end;
                        final byte[] capData = data;
                        LoggerUtil.debug(() -> "Unknown AT sub-block ID: " + String.format("%04X", id) +
                                " sample: " + Hex.bytesToHex(capData, capPos, Math.min(16, capEnd - capPos)));
                    }
                }
                // Skip to next terminator or length-boundary
                pos += 4 + alen; // already doing, but add if (next > end) break;
                continue;
            }
            atoms.add(new ParsedAtom(proto, atom, alen, valueOffset));
            pos = next;
        }

        return new ParsedFrame(token, flags, seq, len, data, payloadOffset, atoms);
    }

    private static boolean isKnownAtom(int proto, int atom) {
        // List known or return true for now
        return true;
    }

    /**
     * Checks if packet is a 0x5A frame.
     */
    public static boolean isFiveA(byte[] b) {
        return ProtocolConstants.isAolFrame(b);
    }

    private static int u8(byte[] b, int i) {
        return b[i] & 0xFF;
    }

    private static int u16(byte[] b, int i) {
        return ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
    }

    private PacketParser() {}

}


