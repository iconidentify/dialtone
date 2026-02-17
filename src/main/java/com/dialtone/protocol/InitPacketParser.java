/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;

import com.dialtone.utils.LoggerUtil;

/**
 * Parser for AOL 3.0 INIT packets (0xA3).
 *
 * <p>INIT packets are sent during connection handshake and contain detailed client information.
 *
 * <p>Packet Structure:
 * <pre>
 * Byte 0:      0x5A (sync byte)
 * Byte 1:      0xA3 (packet type)
 * Bytes 2-3:   Token (varies by client)
 * Bytes 4-5:   Payload length (varies by platform)
 * Bytes 6+:    Payload (client data)
 * </pre>
 *
 * <p>This parser uses tiered parsing to support different client types:
 * <ul>
 *   <li>Basic fields (6 bytes): Always parsed for all clients</li>
 *   <li>Extended fields (52 bytes): Parsed for 32-bit Windows layout</li>
 *   <li>Other layouts (Mac, DOS): Parse basic fields, mark as partial</li>
 * </ul>
 *
 * <p><b>Note:</b> The 52-byte layout is specific to 32-bit Windows clients.
 * Other versions (Mac, DOS) have different layouts.
 */
public class InitPacketParser {

    // INIT packet payload starts at offset 6 (after 5A A3 token len header)
    private static final int PAYLOAD_OFFSET = 6;

    // Minimum bytes needed for basic fields (platform through appMemory)
    private static final int MIN_PAYLOAD_SIZE = 6;

    // Expected payload size for full 32-bit Windows client layout
    private static final int FULL_PAYLOAD_SIZE = 52;

    /**
     * Parse INIT packet data from 0xA3 packet bytes.
     * Uses defensive tiered parsing to handle different client layouts.
     *
     * @param packet Full packet including header (5A A3 ...)
     * @return Parsed InitPacketData, or null if packet is too short for basic fields
     */
    public static InitPacketData parse(byte[] packet) {
        if (packet == null) {
            LoggerUtil.warn("INIT packet is null");
            return null;
        }

        int actualPayloadSize = packet.length - PAYLOAD_OFFSET;

        // Need at least minimum bytes for basic fields
        if (packet.length < PAYLOAD_OFFSET + MIN_PAYLOAD_SIZE) {
            LoggerUtil.warn("INIT packet too short for basic fields: " + packet.length +
                           " bytes (min " + (PAYLOAD_OFFSET + MIN_PAYLOAD_SIZE) + " required)");
            return null;
        }

        try {
            int offset = PAYLOAD_OFFSET;

            // TIER 1: Always parse basic fields (first 6 bytes)
            // These are common to all client types
            int platform = u8(packet, offset + 0x00);
            int versionNum = u8(packet, offset + 0x01);
            int subVersionNum = u8(packet, offset + 0x02);
            int unused = u8(packet, offset + 0x03);
            int machineMemory = u8(packet, offset + 0x04);
            int appMemory = u8(packet, offset + 0x05);

            // TIER 2: Extended fields (32-bit Windows layout)
            // Parse only if we have the full 52-byte payload
            boolean fullyParsed = (actualPayloadSize >= FULL_PAYLOAD_SIZE);

            if (!fullyParsed) {
                LoggerUtil.warn("INIT packet has non-standard size: " + actualPayloadSize +
                               " bytes (expected " + FULL_PAYLOAD_SIZE + " for Windows layout). " +
                               "Parsing available fields.");
            }

            // Initialize extended fields with defaults
            int pcType = 0;
            int releaseMonth = 0;
            int releaseDay = 0;
            int customerClass = 0;
            long udoTimestamp = 0;
            int dosVersion = 0;
            int sessionFlags = 0;
            int videoType = 0;
            int processorType = 0;
            long mediaType = 0;
            long windowsVersion = 0;
            int memoryMode = 0;
            int horizontalRes = 0;
            int verticalRes = 0;
            int numColors = 0;
            int filler = 0;
            int region = 0;
            int[] language = new int[4];
            int connectSpeed = 0;

            // TIER 2: Parse early extended fields opportunistically for Mac/DOS clients
            // Processor type is at offset 0x15 (21 bytes), so we need at least 22 bytes
            if (actualPayloadSize >= 22) {
                pcType = u16(packet, offset + 0x06);
                releaseMonth = u8(packet, offset + 0x08);
                releaseDay = u8(packet, offset + 0x09);
                customerClass = u16(packet, offset + 0x0A);
                udoTimestamp = u32(packet, offset + 0x0C);
                dosVersion = u16(packet, offset + 0x10);
                sessionFlags = u16(packet, offset + 0x12);
                videoType = u8(packet, offset + 0x14);
                processorType = u8(packet, offset + 0x15);
            }

            // TIER 3: Full Windows layout extended fields
            if (fullyParsed) {
                mediaType = u32(packet, offset + 0x16);
                windowsVersion = u32(packet, offset + 0x1A);
                memoryMode = u8(packet, offset + 0x1E);
                horizontalRes = u16(packet, offset + 0x1F);
                verticalRes = u16(packet, offset + 0x21);
                numColors = u16(packet, offset + 0x23);
                filler = u8(packet, offset + 0x25);
                region = u16(packet, offset + 0x26);

                // Language array (4 uint16s)
                for (int i = 0; i < 4; i++) {
                    language[i] = u16(packet, offset + 0x28 + (i * 2));
                }

                connectSpeed = u8(packet, offset + 0x30);
            }

            return new InitPacketData(
                    platform, versionNum, subVersionNum, unused,
                    machineMemory, appMemory, pcType,
                    releaseMonth, releaseDay,
                    customerClass, udoTimestamp,
                    dosVersion, sessionFlags,
                    videoType, processorType,
                    mediaType, windowsVersion, memoryMode,
                    horizontalRes, verticalRes, numColors,
                    filler, region, language, connectSpeed,
                    fullyParsed, actualPayloadSize
            );

        } catch (ArrayIndexOutOfBoundsException e) {
            LoggerUtil.error("Array index out of bounds parsing INIT packet: " + e.getMessage());
            return null;
        } catch (Exception e) {
            LoggerUtil.error("Failed to parse INIT packet: " + e.getMessage());
            return null;
        }
    }

    /**
     * Read unsigned 8-bit value from byte array.
     *
     * @param data Byte array
     * @param offset Offset to read from
     * @return Unsigned 8-bit value (0-255)
     */
    private static int u8(byte[] data, int offset) {
        return data[offset] & 0xFF;
    }

    /**
     * Read unsigned 16-bit value from byte array (big-endian).
     *
     * @param data Byte array
     * @param offset Offset to read from
     * @return Unsigned 16-bit value (0-65535)
     */
    private static int u16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    /**
     * Read unsigned 32-bit value from byte array (big-endian).
     *
     * @param data Byte array
     * @param offset Offset to read from
     * @return Unsigned 32-bit value as long (0-4294967295)
     */
    private static long u32(byte[] data, int offset) {
        return ((data[offset] & 0xFFL) << 24) |
               ((data[offset + 1] & 0xFFL) << 16) |
               ((data[offset + 2] & 0xFFL) << 8) |
               (data[offset + 3] & 0xFFL);
    }
}
