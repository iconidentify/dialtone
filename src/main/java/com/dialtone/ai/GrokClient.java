/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.ai;

import com.dialtone.utils.LoggerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Client for xAI Grok Responses API (/v1/responses).
 * Migrated from the deprecated Chat Completions API with Live Search
 * to the new Agent Tools API with web_search and x_search tools.
 */
public class GrokClient implements AutoCloseable {

    private static final String DEFAULT_BASE_URL = "https://api.x.ai/v1";
    private static final String DEFAULT_MODEL = "grok-4-fast";
    private static final int DEFAULT_TIMEOUT_MS = 10000;

    // Pattern to strip inline citation markdown like [[1]](url)
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[\\[\\d+\\]\\]\\([^)]+\\)");

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int timeoutMs;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Global kill switch
    private final boolean enabled;

    // Search tools configuration
    private final boolean searchEnabled;
    private final String[] searchSources;

    public GrokClient(Properties properties) {
        this.baseUrl = properties.getProperty("grok.base.url", DEFAULT_BASE_URL);
        this.apiKey = properties.getProperty("grok.api.key");
        this.model = properties.getProperty("grok.model", DEFAULT_MODEL);
        this.timeoutMs = Integer.parseInt(properties.getProperty("grok.timeout.ms", String.valueOf(DEFAULT_TIMEOUT_MS)));
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();

        // Global kill switch (default: true/enabled for backwards compatibility)
        this.enabled = Boolean.parseBoolean(properties.getProperty("grok.enabled", "true"));

        // Search tools configuration
        this.searchEnabled = Boolean.parseBoolean(properties.getProperty("grok.search.enabled", "true"));
        String sourcesProperty = properties.getProperty("grok.search.sources", "web_search,x_search");
        this.searchSources = sourcesProperty.split(",");

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("grok.api.key must be configured in application.properties");
        }

        LoggerUtil.info("GrokClient initialized: enabled=" + enabled + ", model=" + model +
                        ", searchEnabled=" + searchEnabled +
                        ", tools=" + String.join(",", searchSources));
    }

    public GrokClient(String baseUrl, String apiKey, String model, int timeoutMs) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = timeoutMs;
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();

        // Default: enabled (for test constructor)
        this.enabled = true;

        // Default search tools configuration for this constructor
        this.searchEnabled = true;
        this.searchSources = new String[]{"web_search", "x_search"};

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
    }

    /**
     * Send a request to the xAI Responses API.
     *
     * @param request The request with input messages and tools
     * @return The response from Grok containing generated content
     * @throws IOException if the request fails
     */
    public GrokChatResponse chatCompletion(GrokChatRequest request) throws IOException {
        // Check global kill switch
        if (!enabled) {
            LoggerUtil.info("Grok API disabled (grok.enabled=false), skipping API call");
            throw new IOException("Grok API is disabled via configuration (grok.enabled=false)");
        }

        long startTime = System.currentTimeMillis();
        try {
            String url = baseUrl + "/responses";
            HttpPost post = new HttpPost(url);

            post.setConfig(org.apache.hc.client5.http.config.RequestConfig.custom()
                    .setResponseTimeout(Timeout.ofMilliseconds(timeoutMs))
                    .setConnectionRequestTimeout(Timeout.ofMilliseconds(timeoutMs))
                    .build());

            // Set authorization header
            post.setHeader("Authorization", "Bearer " + apiKey);

            // Serialize request to JSON
            String requestJson = objectMapper.writeValueAsString(request);
            LoggerUtil.debug(() -> "Grok API request: " + requestJson);

            post.setEntity(new StringEntity(requestJson, ContentType.APPLICATION_JSON));

            GrokChatResponse response = httpClient.execute(post, httpResponse -> {
                int statusCode = httpResponse.getCode();
                String responseBody = new String(httpResponse.getEntity().getContent().readAllBytes());

                if (statusCode != 200) {
                    LoggerUtil.error("Grok API error: HTTP " + statusCode + " - " + responseBody);
                    throw new IOException("HTTP " + statusCode + ": " + responseBody);
                }

                LoggerUtil.debug(() -> "Grok API response: " + responseBody);

                try {
                    return objectMapper.readValue(responseBody, GrokChatResponse.class);
                } catch (Exception e) {
                    throw new IOException("Failed to parse Grok API response: " + e.getMessage(), e);
                }
            });

            long elapsedMs = System.currentTimeMillis() - startTime;
            double elapsedSec = elapsedMs / 1000.0;
            LoggerUtil.info(String.format("Grok API request completed in %.1fs", elapsedSec));

            return response;

        } catch (IOException e) {
            long elapsedMs = System.currentTimeMillis() - startTime;
            double elapsedSec = elapsedMs / 1000.0;
            LoggerUtil.error(String.format("Grok API request failed after %.1fs: %s", elapsedSec, e.getMessage()));
            throw e;
        }
    }

    /**
     * Create a simple request with a user message.
     *
     * @param userMessage The message from the user
     * @return A configured request
     */
    public GrokChatRequest createRequest(String userMessage) {
        GrokChatRequest request = new GrokChatRequest();
        request.setModel(model);
        request.addMessage("user", userMessage);
        request.setTemperature(0.7);
        request.setMaxOutputTokens(1000);

        // Add search tools if enabled
        List<GrokChatRequest.Tool> tools = buildTools();
        if (tools != null) {
            request.setTools(tools);
            LoggerUtil.debug(() -> "Search tools enabled: " + String.join(",", searchSources));
        }

        return request;
    }

    /**
     * Extract the text content from the response.
     * Finds the message output item and extracts the output_text content.
     * Also logs search tool citations if present.
     *
     * @param response The Grok API response
     * @return The generated content text, or null if not available
     */
    public String extractContent(GrokChatResponse response) {
        if (response == null || response.getOutput() == null || response.getOutput().isEmpty()) {
            return null;
        }

        // Find the message output item
        GrokChatResponse.OutputItem messageItem = null;
        int toolCallCount = 0;
        for (GrokChatResponse.OutputItem item : response.getOutput()) {
            if (item.isMessage()) {
                messageItem = item;
            } else {
                toolCallCount++;
            }
        }

        if (toolCallCount > 0) {
            LoggerUtil.info("Search tools used: " + toolCallCount + " tool call(s)");
        }

        if (messageItem == null || messageItem.getContent() == null || messageItem.getContent().isEmpty()) {
            LoggerUtil.warn("No message content in response");
            return null;
        }

        // Extract text from output_text content items
        StringBuilder textBuilder = new StringBuilder();
        int totalAnnotations = 0;

        for (GrokChatResponse.ContentItem contentItem : messageItem.getContent()) {
            if (contentItem.isOutputText() && contentItem.getText() != null) {
                textBuilder.append(contentItem.getText());

                // Count annotations (citations)
                if (contentItem.getAnnotations() != null) {
                    totalAnnotations += contentItem.getAnnotations().size();

                    // Log first few citation URLs for verification
                    int logCount = Math.min(3, contentItem.getAnnotations().size());
                    for (int i = 0; i < logCount; i++) {
                        GrokChatResponse.Annotation annotation = contentItem.getAnnotations().get(i);
                        if (annotation.isUrlCitation()) {
                            final String url = annotation.getUrl();
                            final int num = i + 1;
                            LoggerUtil.debug(() -> "  Citation " + num + ": " + url);
                        }
                    }
                }
            }
        }

        if (totalAnnotations > 0) {
            LoggerUtil.info("Search citations: " + totalAnnotations + " source(s) referenced");
        }

        String rawText = textBuilder.toString();

        // Strip inline citation markdown (e.g., [[1]](url)) for clean text
        String cleanText = CITATION_PATTERN.matcher(rawText).replaceAll("").trim();

        return cleanText;
    }

    /**
     * Build search tools list based on configuration.
     * Returns null if search is disabled.
     *
     * @return List of Tool objects, or null if disabled
     */
    public List<GrokChatRequest.Tool> buildTools() {
        if (!searchEnabled) {
            return null;
        }

        List<GrokChatRequest.Tool> tools = new ArrayList<>();
        for (String source : searchSources) {
            tools.add(new GrokChatRequest.Tool(source.trim()));
        }

        return tools;
    }

    /**
     * Check if search tools are enabled.
     *
     * @return true if search is enabled
     */
    public boolean isSearchEnabled() {
        return searchEnabled;
    }

    /**
     * Get the configured model name.
     *
     * @return The model name
     */
    public String getModel() {
        return model;
    }

    @Override
    public void close() throws IOException {
        LoggerUtil.info("Closing GrokClient");
        httpClient.close();
    }
}
