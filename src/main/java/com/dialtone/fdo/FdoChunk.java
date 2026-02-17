/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FdoChunk {

    @JsonProperty("index")
    private int index;

    @JsonProperty("size")
    private int size;

    @JsonProperty("hex")
    private String hex;

    /**
     * Optional Stream ID for this chunk.
     * Allows chunks with different Stream IDs in the same response (e.g., DOD multi-stream).
     */
    private Integer streamId;

    public FdoChunk() {
    }

    public FdoChunk(int index, int size, String hex) {
        this.index = index;
        this.size = size;
        this.hex = hex;
        this.streamId = null;
    }

    public Integer getStreamId() {
        return streamId;
    }

    public void setStreamId(Integer streamId) {
        this.streamId = streamId;
    }

    public boolean hasStreamId() {
        return streamId != null;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getHex() {
        return hex;
    }

    public void setHex(String hex) {
        this.hex = hex;
    }

    public byte[] getBinaryData() {
        if (hex == null || hex.isEmpty()) {
            return new byte[0];
        }

        int len = hex.length();
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Hex string must have even length: " + hex);
        }

        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hex characters in: " + hex);
            }
            data[i / 2] = (byte) ((high << 4) + low);
        }
        return data;
    }
}