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
import java.util.Properties;

/**
 * Client for xAI Grok API.
 * Handles HTTP communication with Grok's chat completions endpoint.
 */
public class GrokClient implements AutoCloseable {

    private static final String DEFAULT_BASE_URL = "https://api.x.ai/v1";
    private static final String DEFAULT_MODEL = "grok-4-fast-reasoning";
    private static final int DEFAULT_TIMEOUT_MS = 10000;

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int timeoutMs;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Global kill switch
    private final boolean enabled;

    // Live Search configuration
    private final boolean searchEnabled;
    private final String searchMode;
    private final String[] searchSources;
    private final int maxSearchResults;

    public GrokClient(Properties properties) {
        this.baseUrl = properties.getProperty("grok.base.url", DEFAULT_BASE_URL);
        this.apiKey = properties.getProperty("grok.api.key");
        this.model = properties.getProperty("grok.model", DEFAULT_MODEL);
        this.timeoutMs = Integer.parseInt(properties.getProperty("grok.timeout.ms", String.valueOf(DEFAULT_TIMEOUT_MS)));
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();

        // Global kill switch (default: true/enabled for backwards compatibility)
        this.enabled = Boolean.parseBoolean(properties.getProperty("grok.enabled", "true"));

        // Live Search configuration
        this.searchEnabled = Boolean.parseBoolean(properties.getProperty("grok.search.enabled", "true"));
        this.searchMode = properties.getProperty("grok.search.mode", "auto");
        String sourcesProperty = properties.getProperty("grok.search.sources", "web,news,x");
        this.searchSources = sourcesProperty.split(",");
        this.maxSearchResults = Integer.parseInt(properties.getProperty("grok.search.max_results", "20"));

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("grok.api.key must be configured in application.properties");
        }

        LoggerUtil.info("GrokClient initialized: enabled=" + enabled + ", model=" + model +
                        ", searchEnabled=" + searchEnabled + ", searchMode=" + searchMode +
                        ", sources=" + String.join(",", searchSources));
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

        // Default Live Search configuration for this constructor
        this.searchEnabled = true;
        this.searchMode = "auto";
        this.searchSources = new String[]{"web", "news", "x"};
        this.maxSearchResults = 20;

        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
    }

    /**
     * Send a chat completion request to Grok API.
     *
     * @param request The chat request with messages
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
            String url = baseUrl + "/chat/completions";
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
     * Create a simple chat request with a user message.
     *
     * @param userMessage The message from the user
     * @return A configured chat request
     */
    public GrokChatRequest createRequest(String userMessage) {
        GrokChatRequest request = new GrokChatRequest();
        request.setModel(model);
        request.addMessage("user", userMessage);
        request.setTemperature(0.7);
        request.setMaxTokens(1000);

        // Add Live Search parameters if enabled
        SearchParameters searchParams = buildSearchParameters();
        if (searchParams != null) {
            request.setSearchParameters(searchParams);
            LoggerUtil.debug(() -> "Live Search enabled: mode=" + searchMode +
                                   ", sources=" + String.join(",", searchSources));
        }

        return request;
    }

    /**
     * Extract the content from the first choice in the response.
     * Also logs Live Search citations if present.
     *
     * @param response The Grok API response
     * @return The generated content text, or null if not available
     */
    public String extractContent(GrokChatResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return null;
        }

        GrokChatResponse.Choice firstChoice = response.getChoices().get(0);
        if (firstChoice.getMessage() == null) {
            return null;
        }

        // Log Live Search citations if present
        GrokChatResponse.Message message = firstChoice.getMessage();
        if (message.getCitations() != null && !message.getCitations().isEmpty()) {
            int citationCount = message.getCitations().size();
            LoggerUtil.info("Live Search ACTIVE: " + citationCount + " sources used");

            // Log source types breakdown
            java.util.Map<String, Long> sourceTypeCounts = message.getCitations().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    c -> c.getSourceType() != null ? c.getSourceType() : "unknown",
                    java.util.stream.Collectors.counting()
                ));

            sourceTypeCounts.forEach((type, count) ->
                LoggerUtil.info("  - " + type + ": " + count + " sources")
            );

            // Log first few citation titles for verification
            int logCount = Math.min(3, citationCount);
            for (int i = 0; i < logCount; i++) {
                GrokChatResponse.Citation citation = message.getCitations().get(i);
                String title = citation.getTitle() != null ? citation.getTitle() : "No title";
                final int citationNum = i + 1;
                final String citationTitle = title;
                LoggerUtil.debug(() -> "  Citation " + citationNum + ": " + citationTitle);
            }
        } else {
            LoggerUtil.warn("Live Search NOT used - no citations returned (check search_parameters configuration)");
        }

        return message.getContent();
    }

    /**
     * Build SearchParameters based on configuration.
     * Returns null if search is disabled.
     *
     * @return Configured SearchParameters, or null if disabled
     */
    public SearchParameters buildSearchParameters() {
        if (!searchEnabled) {
            return null;
        }

        SearchParameters searchParams = new SearchParameters.Builder()
                .mode(searchMode)
                .returnCitations(true)
                .maxSearchResults(maxSearchResults)
                .build();

        // Add configured sources
        for (String source : searchSources) {
            searchParams.addSource(source.trim());
        }

        return searchParams;
    }

    /**
     * Check if live search is enabled.
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
