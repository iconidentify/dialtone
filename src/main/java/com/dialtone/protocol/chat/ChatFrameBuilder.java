/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.chat;

import com.dialtone.utils.LoggerUtil;

import java.nio.charset.StandardCharsets;

/**
 * Utility class for building chat-related frames (AA, AB, CA, CB).
 */
public class ChatFrameBuilder {
    private final String logPrefix;

    public ChatFrameBuilder(String logPrefix) {
        this.logPrefix = logPrefix;
    }

    /**
     * Build AA chat message frame using mat_relative_tag to identify sender.
     */
    public byte[] buildChatMessageMatRelativeId(String message, int matRelativeTag) {
        // Replace emojis and non-ASCII characters with spaces (1995 protocol limitation)
        String asciiOnlyMessage = message.replaceAll("[^\\x00-\\x7F]+", " ");

        if (!message.equals(asciiOnlyMessage)) {
            LoggerUtil.warn(logPrefix + "Replaced non-ASCII characters with spaces (1995 protocol limitation)");
            LoggerUtil.debug(() -> logPrefix + "Original: " + message);
            LoggerUtil.debug(() -> logPrefix + "Sanitized: " + asciiOnlyMessage);
        }

        byte[] messageBytes = asciiOnlyMessage.getBytes(StandardCharsets.US_ASCII);

        // Total frame size: 11-byte header + payload + 1-byte terminator
        byte[] frame = new byte[11 + messageBytes.length + 1];

        // Build frame structure (bytes 0-6 will be set by restamper)
        frame[0] = 0x5A;  // Magic
        // Bytes 1-6 will be set by restamper (CRC, length, TX, RX) - leave as 0x00
        frame[7] = 0x20;  // DATA type (enables restamping in Pacer)
        frame[8] = 0x41;  // 'A' (token high)
        frame[9] = 0x41;  // 'A' (token low) - AA for echo
        frame[10] = (byte) (matRelativeTag & 0xFF); // Mat relative tag (sender's global tag)

        // Copy message payload
        System.arraycopy(messageBytes, 0, frame, 11, messageBytes.length);

        // Terminator
        frame[frame.length - 1] = 0x0D;

        return frame;
    }

    /**
     * Build AB chat message frame with explicit username in payload.
     * RESERVED for system messages from special accounts like "OnlineHost".
     */
    public byte[] buildChatMessageWithUsername(String username, String message) {
        // Replace emojis and non-ASCII characters with spaces (1995 protocol limitation)
        String asciiOnlyMessage = message.replaceAll("[^\\x00-\\x7F]+", " ");

        if (!message.equals(asciiOnlyMessage)) {
            LoggerUtil.warn(logPrefix + "Replaced non-ASCII characters with spaces (1995 protocol limitation)");
            LoggerUtil.debug(() -> logPrefix + "Original: " + message);
            LoggerUtil.debug(() -> logPrefix + "Sanitized: " + asciiOnlyMessage);
        }

        // Pad username to exactly 10 characters (AOL max screenname length)
        String paddedUsername = String.format("%-10s", username);
        if (username.length() > 10) {
            paddedUsername = username.substring(0, 10);
            LoggerUtil.warn(logPrefix + "Username '" + username + "' truncated to 10 chars: '" + paddedUsername + "'");
        }

        byte[] nameBytes = paddedUsername.getBytes(StandardCharsets.US_ASCII);
        byte[] messageBytes = asciiOnlyMessage.getBytes(StandardCharsets.US_ASCII);

        // Payload: 10-char username + null + message
        int payloadLen = 10 + 1 + messageBytes.length;
        byte[] payload = new byte[payloadLen];

        // Copy padded username (always 10 bytes)
        System.arraycopy(nameBytes, 0, payload, 0, 10);

        // Null terminator at position 10
        payload[10] = 0x00;

        // Copy message starting at position 11
        System.arraycopy(messageBytes, 0, payload, 11, messageBytes.length);

        // Total frame size: 10-byte header + payload + 1-byte terminator
        byte[] frame = new byte[10 + payloadLen + 1];

        // Build frame structure (bytes 0-6 will be set by restamper)
        frame[0] = 0x5A;  // Magic
        // Bytes 1-6 will be set by restamper (CRC, length, TX, RX) - leave as 0x00
        frame[7] = 0x20;  // DATA type (enables restamping in Pacer)
        frame[8] = 0x41;  // 'A' (token high)
        frame[9] = 0x42;  // 'B' (token low) - AB for received

        // Copy payload (starts at byte 10)
        System.arraycopy(payload, 0, frame, 10, payloadLen);

        // Terminator
        frame[frame.length - 1] = 0x0D;

        return frame;
    }

    /**
     * Build a chat notification frame (arrival or departure).
     */
    public byte[] buildChatNotificationFrame(String username, int matRelativeTag, ChatTokenHandler.ChatEventType eventType) {
        byte[] usernameBytes = username.getBytes(StandardCharsets.US_ASCII);

        // Atom payload = token(2) + " C?"(3) + separator(1) + username
        int atomPayloadLen = 2 + 3 + 1 + usernameBytes.length;

        // Total frame = magic(1) + type(1) + streamID(2) + atomLen(1) + atomPayload + terminator(1)
        int totalFrameLen = 1 + 1 + 2 + 1 + atomPayloadLen + 1;

        byte[] frame = new byte[totalFrameLen];
        int idx = 0;

        // Magic byte
        frame[idx++] = 0x5A;

        // Type
        frame[idx++] = 0x1B;

        // Stream ID (big-endian)
        frame[idx++] = 0x74; // stream ID high
        frame[idx++] = 0x00; // stream ID low

        // Atom payload length
        frame[idx++] = (byte) (atomPayloadLen & 0xFF);

        // Token "mS"
        frame[idx++] = 0x6D; // 'm'
        frame[idx++] = 0x53; // 'S'

        // Payload: " CA" or " CB" depending on event type
        frame[idx++] = 0x20; // space
        frame[idx++] = 0x43; // 'C'
        frame[idx++] = (byte) eventType.getCode(); // 'A' or 'B'

        // Mat relative tag (globally consistent across all clients)
        frame[idx++] = (byte) (matRelativeTag & 0xFF);

        // Username
        System.arraycopy(usernameBytes, 0, frame, idx, usernameBytes.length);
        idx += usernameBytes.length;

        // Terminator
        frame[idx] = 0x0D;

        return frame;
    }
}
