/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.ai;

import com.dialtone.chat.bot.ResponseContext;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.utils.MessageSplitter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Service for generating conversational responses using Grok AI.
 * Designed for real-time chatbot interactions with low latency.
 * Unlike news services, this doesn't cache or persist - responses are generated on-demand.
 */
public class GrokConversationalService implements AutoCloseable {

    private final GrokClient grokClient;
    private final int maxTokens;
    private final double temperature;
    private final String systemPrompt;
    private final String projectKnowledge;

    public GrokConversationalService(Properties properties) {
        this.grokClient = new GrokClient(properties);

        this.maxTokens = Integer.parseInt(
                properties.getProperty("grok.chat.max.tokens", "300"));
        this.temperature = Double.parseDouble(
                properties.getProperty("grok.chat.temperature", "0.8"));

        // Default system prompt for Grok personality - allows complete responses (splitting handled elsewhere)
        this.systemPrompt = properties.getProperty("grok.chat.system.prompt",
                "You are Grok, a friendly AI assistant in a classic AOL-style chat room. " +
                "Be helpful and give complete answers. Use short sentences that flow naturally. " +
                "Avoid using bullet points, numbered lists, or markdown formatting. " +
                "Write like you're chatting - conversational and warm. " +
                "IMPORTANT: Never use emojis or non-ASCII characters. Plain text only.");

        // Load project knowledge from resource file
        this.projectKnowledge = loadProjectKnowledge();

        LoggerUtil.info("GrokConversationalService initialized: maxTokens=" + maxTokens +
                        ", temperature=" + temperature +
                        ", projectKnowledge=" + (projectKnowledge.isEmpty() ? "not loaded" : projectKnowledge.length() + " chars"));
    }

    /**
     * Load project knowledge from resource file.
     * This provides Grok with context about Dialtone, AOL 3.0, and related tools.
     */
    private String loadProjectKnowledge() {
        try (InputStream is = getClass().getResourceAsStream("/grok/project_knowledge.txt")) {
            if (is != null) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                LoggerUtil.info("Loaded project knowledge: " + content.length() + " characters");
                return content;
            }
            LoggerUtil.warn("Project knowledge file not found: /grok/project_knowledge.txt");
        } catch (IOException e) {
            LoggerUtil.warn("Could not load project knowledge: " + e.getMessage());
        }
        return "";
    }

    /**
     * Generate a conversational response to a user's message.
     * This is synchronous and blocks until response is received.
     *
     * @param userMessage The message from the user
     * @param contextHint Optional context about the conversation (can be null)
     * @return The AI-generated response
     * @throws IOException if the API request fails
     */
    public String generateResponse(String userMessage, String contextHint) throws IOException {
        return generateResponseWithHistory(userMessage, null, contextHint);
    }

    /**
     * Generate a conversational response with conversation history for context.
     * Includes previous messages to maintain conversational continuity.
     *
     * @param userMessage The message from the user
     * @param history Previous conversation history (can be null)
     * @param contextHint Optional context about the conversation (can be null)
     * @return The AI-generated response
     * @throws IOException if the API request fails
     */
    public String generateResponseWithHistory(String userMessage,
                                              java.util.List<ConversationMemoryManager.Message> history,
                                              String contextHint) throws IOException {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("User message cannot be null or empty");
        }

        long startTime = System.currentTimeMillis();
        int historySize = (history != null) ? history.size() : 0;
        LoggerUtil.info(String.format("Generating Grok response for: %s (history: %d messages)",
                truncate(userMessage, 50), historySize));

        try {
            GrokChatRequest request = buildChatRequest(userMessage, history, contextHint);
            GrokChatResponse response = grokClient.chatCompletion(request);
            String content = grokClient.extractContent(response);

            if (content == null || content.isEmpty()) {
                throw new IOException("Grok returned empty content");
            }

            long elapsedMs = System.currentTimeMillis() - startTime;
            LoggerUtil.info(String.format("Grok response generated in %dms: %s",
                    elapsedMs, truncate(content, 50)));

            return content;

        } catch (IOException e) {
            long elapsedMs = System.currentTimeMillis() - startTime;
            LoggerUtil.error(String.format("Grok response generation failed after %dms: %s",
                    elapsedMs, e.getMessage()));
            throw e;
        }
    }

    /**
     * Generate a conversational response with context-aware character limits.
     * Adds character limit instructions to the prompt based on the response context.
     *
     * @param userMessage The message from the user
     * @param history Previous conversation history (can be null)
     * @param contextHint Optional context about the conversation (can be null)
     * @param responseContext The response context (CHAT_ROOM or INSTANT_MESSAGE)
     * @return The AI-generated response
     * @throws IOException if the API request fails
     */
    public String generateResponseWithContext(String userMessage,
                                              java.util.List<ConversationMemoryManager.Message> history,
                                              String contextHint,
                                              ResponseContext responseContext) throws IOException {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("User message cannot be null or empty");
        }

        long startTime = System.currentTimeMillis();
        int historySize = (history != null) ? history.size() : 0;
        LoggerUtil.info(String.format("Generating Grok response for: %s (history: %d messages, context: %s)",
                truncate(userMessage, 50), historySize, responseContext));

        try {
            GrokChatRequest request = buildChatRequestWithContext(userMessage, history, contextHint, responseContext);
            GrokChatResponse response = grokClient.chatCompletion(request);
            String content = grokClient.extractContent(response);

            if (content == null || content.isEmpty()) {
                throw new IOException("Grok returned empty content");
            }

            long elapsedMs = System.currentTimeMillis() - startTime;
            LoggerUtil.info(String.format("Grok response generated in %dms (%d chars): %s",
                    elapsedMs, content.length(), truncate(content, 50)));

            return content;

        } catch (IOException e) {
            long elapsedMs = System.currentTimeMillis() - startTime;
            LoggerUtil.error(String.format("Grok response generation failed after %dms: %s",
                    elapsedMs, e.getMessage()));
            throw e;
        }
    }

    /**
     * Generate a response with project knowledge context.
     * This version includes information about the Dialtone project to help answer technical questions.
     *
     * @param userMessage The message from the user
     * @param projectContext Context from README.md/CLAUDE.md (can be null)
     * @return The AI-generated response
     * @throws IOException if the API request fails
     */
    public String generateResponseWithProjectKnowledge(String userMessage, String projectContext) throws IOException {
        String enhancedContext = null;

        if (projectContext != null && !projectContext.trim().isEmpty()) {
            enhancedContext = "Here's information about the Dialtone project that may help answer the question:\n\n" +
                    projectContext + "\n\n" +
                    "Use this information to answer questions about Dialtone, but don't mention that you're reading from documentation. " +
                    "Just answer naturally as if you know about the project.";
        }

        return generateResponse(userMessage, enhancedContext);
    }

    /**
     * Build a chat request with system prompt, conversation history, and user message.
     */
    private GrokChatRequest buildChatRequest(String userMessage,
                                            java.util.List<ConversationMemoryManager.Message> history,
                                            String contextHint) {
        GrokChatRequest request = new GrokChatRequest();
        request.setModel(grokClient.getModel());
        request.setTemperature(temperature);
        request.setMaxTokens(maxTokens);

        // Add project knowledge first (provides context about Dialtone, AOL 3.0, tools)
        if (projectKnowledge != null && !projectKnowledge.isEmpty()) {
            request.addMessage("system", projectKnowledge);
        }

        // Add system prompt (personality and communication style)
        request.addMessage("system", systemPrompt);

        // Add context hint if provided (e.g., IM vs chat room context)
        if (contextHint != null && !contextHint.trim().isEmpty()) {
            request.addMessage("system", contextHint);
        }

        // Add conversation history if provided
        if (history != null && !history.isEmpty()) {
            for (ConversationMemoryManager.Message msg : history) {
                request.addMessage(msg.getRole(), msg.getContent());
            }
            LoggerUtil.debug(() -> "Added " + history.size() + " messages from conversation history");
        }

        // Add current user message
        request.addMessage("user", userMessage);

        // Add Live Search parameters if enabled
        SearchParameters searchParams = grokClient.buildSearchParameters();
        if (searchParams != null) {
            request.setSearchParameters(searchParams);
            LoggerUtil.debug(() -> "Live Search enabled for conversational request");
        }

        return request;
    }

    /**
     * Build a chat request with context-aware character limit prompts.
     */
    private GrokChatRequest buildChatRequestWithContext(String userMessage,
                                                        java.util.List<ConversationMemoryManager.Message> history,
                                                        String contextHint,
                                                        ResponseContext responseContext) {
        GrokChatRequest request = new GrokChatRequest();
        request.setModel(grokClient.getModel());
        request.setTemperature(temperature);
        request.setMaxTokens(maxTokens);

        // Add project knowledge first (provides context about Dialtone, AOL 3.0, tools)
        if (projectKnowledge != null && !projectKnowledge.isEmpty()) {
            request.addMessage("system", projectKnowledge);
        }

        // Add system prompt (personality and communication style)
        request.addMessage("system", systemPrompt);

        // Add character limit context based on response context
        if (responseContext != null) {
            String charLimitPrompt = buildCharacterLimitPrompt(responseContext);
            request.addMessage("system", charLimitPrompt);
        }

        // Add context hint if provided (e.g., IM vs chat room context)
        if (contextHint != null && !contextHint.trim().isEmpty()) {
            request.addMessage("system", contextHint);
        }

        // Add conversation history if provided
        if (history != null && !history.isEmpty()) {
            for (ConversationMemoryManager.Message msg : history) {
                request.addMessage(msg.getRole(), msg.getContent());
            }
            LoggerUtil.debug(() -> "Added " + history.size() + " messages from conversation history");
        }

        // Add current user message
        request.addMessage("user", userMessage);

        // Add Live Search parameters if enabled
        SearchParameters searchParams = grokClient.buildSearchParameters();
        if (searchParams != null) {
            request.setSearchParameters(searchParams);
            LoggerUtil.debug(() -> "Live Search enabled for conversational request");
        }

        return request;
    }

    /**
     * Build a formatting hint prompt based on the response context.
     * Tells Grok to provide complete responses - they will be split into multiple messages automatically.
     */
    private String buildCharacterLimitPrompt(ResponseContext context) {
        return switch (context) {
            case CHAT_ROOM ->
                "You are chatting in a 1995 AOL chat room. Your responses will be automatically " +
                "split into multiple short messages (like a person typing in chat). " +
                "Feel free to give complete, helpful answers - lists, explanations, details are all fine. " +
                "Write naturally. Each sentence or thought will become its own message. " +
                "Use short sentences. Avoid bullet points or numbered lists - just write flowing text.";
            case INSTANT_MESSAGE ->
                "You are in a 1995 AOL instant message window. Your responses will be automatically " +
                "split into multiple messages if needed. Feel free to give complete answers. " +
                "Write conversationally. Each sentence or thought will become its own message.";
        };
    }

    /**
     * Truncate a string for logging.
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "null";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength) + "...";
    }

    @Override
    public void close() throws IOException {
        LoggerUtil.info("Closing GrokConversationalService");
        grokClient.close();
    }
}
