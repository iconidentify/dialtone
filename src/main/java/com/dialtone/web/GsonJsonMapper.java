/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.javalin.json.JsonMapper;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;

/**
 * Gson-based JSON mapper for Javalin.
 *
 * Integrates Gson with Javalin's JSON handling system,
 * supporting custom serializers and LocalDateTime handling.
 */
public class GsonJsonMapper implements JsonMapper {
    private final Gson gson;

    public GsonJsonMapper(Gson gson) {
        this.gson = gson;
    }

    @Override
    public String toJsonString(@NotNull Object obj, @NotNull Type type) {
        return gson.toJson(obj, type);
    }

    @Override
    public <T> T fromJsonString(@NotNull String json, @NotNull Type targetType) {
        try {
            return gson.fromJson(json, targetType);
        } catch (JsonSyntaxException e) {
            throw new RuntimeException("Failed to parse JSON: " + e.getMessage(), e);
        }
    }
}