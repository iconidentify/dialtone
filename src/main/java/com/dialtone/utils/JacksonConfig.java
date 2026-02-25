/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Shared ObjectMapper instances. ObjectMapper is thread-safe after configuration,
 * so a single instance can be reused across the application.
 */
public final class JacksonConfig {

    private static final ObjectMapper INSTANCE = new ObjectMapper();

    private static final ObjectMapper PRETTY_INSTANCE = new ObjectMapper();

    static {
        PRETTY_INSTANCE.registerModule(new JavaTimeModule());
        PRETTY_INSTANCE.enable(SerializationFeature.INDENT_OUTPUT);
    }

    private JacksonConfig() {}

    /** Standard ObjectMapper for general JSON serialization/deserialization. */
    public static ObjectMapper mapper() {
        return INSTANCE;
    }

    /** ObjectMapper with pretty-printing and JavaTimeModule for human-readable output. */
    public static ObjectMapper prettyMapper() {
        return PRETTY_INSTANCE;
    }
}
