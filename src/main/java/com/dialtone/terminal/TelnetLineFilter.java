/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.terminal;

/**
 * Interface for filtering lines received from a telnet connection.
 * Filters can modify lines, suppress them entirely, or pass them through unchanged.
 *
 * <p>Used primarily for intercepting protocol-specific data (like AUTH tokens)
 * before they reach the client display.
 */
@FunctionalInterface
public interface TelnetLineFilter {

    /**
     * Filters a line received from the telnet connection.
     *
     * @param line the original line received (never null)
     * @return the filtered line to forward to the client, or {@code null} to suppress the line entirely
     */
    String filter(String line);

    /**
     * Returns a filter that passes all lines through unchanged.
     *
     * @return a pass-through filter
     */
    static TelnetLineFilter passThrough() {
        return line -> line;
    }

    /**
     * Chains this filter with another filter. The other filter is applied
     * to the output of this filter.
     *
     * @param after the filter to apply after this one
     * @return a composed filter
     */
    default TelnetLineFilter andThen(TelnetLineFilter after) {
        return line -> {
            String result = this.filter(line);
            return result != null ? after.filter(result) : null;
        };
    }
}
