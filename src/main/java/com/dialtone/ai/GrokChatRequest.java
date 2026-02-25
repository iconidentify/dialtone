/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Request model for xAI Responses API (/v1/responses).
 * Replaces the deprecated Chat Completions API with search_parameters.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GrokChatRequest {

    @JsonProperty("model")
    private String model;

    @JsonProperty("instructions")
    private String instructions;

    @JsonProperty("input")
    private List<Message> input;

    @JsonProperty("temperature")
    private Double temperature;

    @JsonProperty("max_output_tokens")
    private Integer maxOutputTokens;

    @JsonProperty("tools")
    private List<Tool> tools;

    public GrokChatRequest() {
        this.input = new ArrayList<>();
    }

    public GrokChatRequest(String model, List<Message> input) {
        this.model = model;
        this.input = input;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public List<Message> getInput() {
        return input;
    }

    public void setInput(List<Message> input) {
        this.input = input;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public List<Tool> getTools() {
        return tools;
    }

    public void setTools(List<Tool> tools) {
        this.tools = tools;
    }

    public void addMessage(String role, String content) {
        this.input.add(new Message(role, content));
    }

    /**
     * @deprecated Use setMaxOutputTokens instead. Kept for backward compatibility during migration.
     */
    @Deprecated
    public void setMaxTokens(Integer maxTokens) {
        this.maxOutputTokens = maxTokens;
    }

    public static class Message {
        @JsonProperty("role")
        private String role;

        @JsonProperty("content")
        private String content;

        public Message() {}

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    /**
     * Tool definition for the Responses API.
     * Supports web_search and x_search built-in tools.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Tool {
        @JsonProperty("type")
        private String type;

        public Tool() {}

        public Tool(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
}
