/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.aol.core;

/**
 * Single-source CRC-16/IBM implementation (polynomial 0xA001), initial 0x0000.
 * Bit-reflected algorithm matching AOL P3 captures.
 */
public final class Crc16Ibm {

    private Crc16Ibm() {}

    /**
     * Computes CRC-16/IBM over a subrange of a byte array.
     *
     * @param a   byte array
     * @param off start offset
     * @param len number of bytes
     * @return 16-bit CRC value in the range [0, 0xFFFF]
     */
    public static int compute(byte[] a, int off, int len) {
        int crc = 0x0000;
        for (int i = off; i < off + len; i++) {
            crc ^= (a[i] & 0xFF);
            for (int b = 0; b < 8; b++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc = (crc >>> 1);
                }
            }
        }
        return crc & 0xFFFF;
    }
}


