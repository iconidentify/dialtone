/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;

import com.dialtone.aol.core.ProtocolConstants;

/**
 * AOL 3.0 packet type constants.
 * Based on the P3 protocol specification and existing patterns in the codebase.
 */
public enum PacketType {
    // Standard P3 packet types (from PHP enum)
    DATA(0x20, "Data packet"),
    SS(0x21, "Request SSR"),
    SSR(0x22, "Response to SS"),
    INIT(0x23, "Force initialization"),
    ACK(0x24, "Acknowledgment"),
    NAK(0x25, "Negative acknowledgment"),
    HEARTBEAT(0x26, "Heartbeat"),
    
    // AOL 3.0 specific packet types
    A0(0xA0, "AOL 3.0 packet type A0"),
    A1(0xA1, "AOL 3.0 packet type A1"),
    A2(0xA2, "AOL 3.0 packet type A2"),
    A3(0xA3, "AOL 3.0 startup probe"),
    A4(0xA4, "AOL 3.0 packet type A4"),
    A5(0xA5, "AOL 3.0 acknowledgment"),
    A6(0xA6, "AOL 3.0 packet type A6"),
    A7(0xA7, "AOL 3.0 packet type A7"),
    A8(0xA8, "AOL 3.0 packet type A8"),
    A9(0xA9, "AOL 3.0 packet type A9"),
    AA(0xAA, "AOL 3.0 packet type AA"),
    AB(0xAB, "AOL 3.0 packet type AB"),
    AC(0xAC, "AOL 3.0 packet type AC"),
    AD(0xAD, "AOL 3.0 packet type AD"),
    AE(0xAE, "AOL 3.0 packet type AE"),
    AF(0xAF, "AOL 3.0 packet type AF");
    
    private final int value;
    private final String description;
    
    PacketType(int value, String description) {
        this.value = value;
        this.description = description;
    }
    
    public int getValue() {
        return value;
    }
    
    public String getDescription() {
        return description;
    }

    /**
     * Check if a packet type is in the A0-AF range (AOL 3.0 acknowledgment types).
     */
    public static boolean isAolAcknowledgmentType(int type) {
        return (type & ProtocolConstants.AOL_ACK_TYPE_MASK) == ProtocolConstants.AOL_ACK_TYPE_BASE;
    }
    
    /**
     * Check if a packet type is a specific AOL acknowledgment type.
     */
    public static boolean isAolAcknowledgmentType(int type, PacketType expectedType) {
        return type == expectedType.getValue();
    }
    
    /**
     * Check if a packet type is in the A0-AF range but not A5.
     */
    public static boolean isAolAcknowledgmentTypeExcludingA5(int type) {
        return isAolAcknowledgmentType(type) && type != A5.getValue();
    }
    
    /**
     * Get the corresponding acknowledgment type for A4/A6 packets.
     * Note: A6 is an ACK type that opens the gate but doesn't need a response.
     */
    public static PacketType getAcknowledgmentResponseType(int type) {
        if (type == A4.getValue()) {
            return ACK; // 0x24
        }
        // A6 is an ACK type that opens the gate - no response needed
        return null;
    }
    
    @Override
    public String toString() {
        return String.format("%s(0x%02X)", name(), value);
    }
}
