/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.aol.core;

/**
 * Hex utilities (uppercase output).
 */
public final class Hex {

    private static final char[] UPPER_HEX = "0123456789ABCDEF".toCharArray();

    private Hex() {}

    /**
     * Converts a byte array to an uppercase hex string without separators.
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte x : bytes) {
            int v = x & 0xFF;
            sb.append(UPPER_HEX[v >>> 4]).append(UPPER_HEX[v & 0x0F]);
        }
        return sb.toString();
    }


    public static String bytesToHex(byte[] b, int off, int len) {
        StringBuilder sb = new StringBuilder(len * 2);
        for (int i = off; i < off + len; i++) {
            int v = b[i] & 0xff;
            sb.append(UPPER_HEX[v >>> 4]).append(UPPER_HEX[v & 0xf]);
        }
        return sb.toString();
    }

    /**
     * Parses a hex string to bytes. Robust to spaces and colons.
     * Throws IllegalArgumentException for invalid input.
     */
    public static byte[] hexToBytes(String s) {
        if (s == null || s.isEmpty()) {
            return new byte[0];
        }
        String cleaned = s.replaceAll("[\\s:]+", "");
        if (cleaned.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length: " + s);
        }
        int n = cleaned.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            int hi = Character.digit(cleaned.charAt(i * 2), 16);
            int lo = Character.digit(cleaned.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex at index " + (i * 2) + ": '" + s + "'");
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

}


