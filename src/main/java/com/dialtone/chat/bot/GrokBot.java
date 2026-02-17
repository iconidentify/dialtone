/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.chat.bot;

import com.dialtone.ai.ConversationMemoryManager;
import com.dialtone.ai.GrokConversationalService;
import com.dialtone.ai.ResponseFormatter;
import com.dialtone.utils.LoggerUtil;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Grok - An AI-powered chat bot that provides helpful information
 * about the Dialtone project, sports scores, crypto prices, and general knowledge.
 *
 * Now with conversation memory to maintain context across messages!
 */
public class GrokBot implements VirtualUser {

    private static final String BOT_USERNAME = "Grok";
    private static final Pattern MENTION_PATTERN = Pattern.compile(
            "@Grok\\b|@grok\\b|@GROK\\b",
            Pattern.CASE_INSENSITIVE
    );

    private volatile boolean active;
    private final GrokConversationalService grokService;
    private final ResponseFormatter responseFormatter;
    private final boolean aiEnabled;
    private final ConversationMemoryManager conversationMemory;

    public GrokBot() {
        this(null, null);
    }

    public GrokBot(GrokConversationalService grokService) {
        this(grokService, null);
    }

    public GrokBot(GrokConversationalService grokService, ResponseFormatter responseFormatter) {
        this.active = true;
        this.grokService = grokService;
        this.responseFormatter = responseFormatter;
        this.aiEnabled = (grokService != null);

        // Initialize conversation memory (10 turns = 20 messages, 30 min timeout)
        this.conversationMemory = new ConversationMemoryManager(10, 30);

        LoggerUtil.info("Grok bot initialized " +
                (aiEnabled ? "[AI ENABLED with MEMORY]" : "[AI DISABLED - using pattern matching]") +
                (responseFormatter != null ? " [FORMATTER ENABLED]" : ""));
    }

    /**
     * Get the response formatter for this bot.
     * @return The response formatter, or null if not configured
     */
    public ResponseFormatter getResponseFormatter() {
        return responseFormatter;
    }

    @Override
    public String getUsername() {
        return BOT_USERNAME;
    }

    @Override
    public boolean shouldRespondTo(String message, String sender) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        // Don't respond to our own messages
        if (sender != null && sender.equalsIgnoreCase(BOT_USERNAME)) {
            return false;
        }

        // Respond to @mentions
        Matcher matcher = MENTION_PATTERN.matcher(message);
        boolean hasMention = matcher.find();

        if (hasMention) {
            LoggerUtil.debug(() -> "Grok detected mention from " + sender + ": " + message);
        }

        return hasMention;
    }

    @Override
    public String generateResponse(String message, String sender, ChatContext context) {
        if (!active) {
            return null;
        }

        try {
            // Use default sender name if null for conversation tracking
            String effectiveSender = (sender == null || sender.trim().isEmpty()) ? "Unknown" : sender;
            String memoryKey = buildMemoryKey(effectiveSender, ResponseContext.CHAT_ROOM);

            // Extract the actual query by removing the @mention
            String query = extractQuery(message);

            LoggerUtil.info(String.format("Grok processing query from %s: '%s' [AI: %s, History: %d msgs, memKey: %s]",
                    effectiveSender, query, aiEnabled ? "ON" : "OFF",
                    conversationMemory.getHistory(memoryKey).size(), memoryKey));

            // Add user message to conversation history (keyed by user+context)
            conversationMemory.addUserMessage(memoryKey, query);

            String response;

            // Try AI-powered response first if enabled
            if (aiEnabled) {
                response = generateAIResponse(query, memoryKey, context);
            } else {
                // Fallback to pattern-matching responses
                response = generateBasicResponse(query, effectiveSender, context);
            }

            // Add bot response to conversation history
            conversationMemory.addBotMessage(memoryKey, response);

            LoggerUtil.info(String.format("Grok response to %s: '%s'", effectiveSender, response));

            return response;

        } catch (Exception e) {
            LoggerUtil.error("Grok error generating response: " + e.getMessage());
            String effectiveSender = (sender == null || sender.trim().isEmpty()) ? "Unknown" : sender;
            String memoryKey = buildMemoryKey(effectiveSender, ResponseContext.CHAT_ROOM);
            // Fallback to basic response on AI failure
            if (aiEnabled) {
                LoggerUtil.warn("Falling back to pattern-matching due to AI error");
                try {
                    String query = extractQuery(message);
                    String fallbackResponse = generateBasicResponse(query, effectiveSender, context);
                    // Still add to memory even if AI failed
                    conversationMemory.addBotMessage(memoryKey, fallbackResponse);
                    return fallbackResponse;
                } catch (Exception fallbackError) {
                    String errorResponse = "Sorry, I'm having trouble processing that right now. Please try again!";
                    conversationMemory.addBotMessage(memoryKey, errorResponse);
                    return errorResponse;
                }
            }
            String errorResponse = "Sorry, I'm having trouble processing that right now. Please try again!";
            conversationMemory.addBotMessage(memoryKey, errorResponse);
            return errorResponse;
        }
    }

    /**
     * Generate a response for a direct instant message (not chat room).
     * Uses IM-specific context hints and SEPARATE conversation memory from chat room.
     *
     * @param message The message from the user
     * @param sender The username of the person who sent the IM
     * @return The response to send back, or null if bot is inactive
     */
    public String generateIMResponse(String message, String sender) {
        if (!active) {
            return null;
        }

        try {
            String effectiveSender = (sender == null || sender.trim().isEmpty()) ? "Unknown" : sender;
            String memoryKey = buildMemoryKey(effectiveSender, ResponseContext.INSTANT_MESSAGE);

            LoggerUtil.info(String.format("Grok processing IM from %s: '%s' [AI: %s, History: %d msgs, memKey: %s]",
                    effectiveSender, message, aiEnabled ? "ON" : "OFF",
                    conversationMemory.getHistory(memoryKey).size(), memoryKey));

            // Add user message to conversation history (keyed by user+context - separate from chat)
            conversationMemory.addUserMessage(memoryKey, message);

            String response;

            if (aiEnabled) {
                // Get conversation history for IM context only
                List<ConversationMemoryManager.Message> history = conversationMemory.getHistory(memoryKey);
                List<ConversationMemoryManager.Message> previousHistory = history.isEmpty() ?
                        Collections.emptyList() :
                        history.subList(0, Math.max(0, history.size() - 1));

                // Context hint for IM - more personal and direct
                String contextHint = "You are in a private IM conversation with " + effectiveSender +
                        ". This is one-on-one, so be personal and direct.";

                try {
                    response = grokService.generateResponseWithHistory(message, previousHistory, contextHint);
                } catch (java.io.IOException e) {
                    LoggerUtil.warn("Grok API unavailable for IM: " + e.getMessage());
                    response = "Sorry, I'm having connection issues. Try again in a moment!";
                }
            } else {
                // Fallback to pattern-matching responses
                response = generateBasicResponse(message, effectiveSender, null);
            }

            // Add bot response to conversation history
            conversationMemory.addBotMessage(memoryKey, response);

            LoggerUtil.info(String.format("Grok IM response to %s: '%s'", effectiveSender, response));

            return response;

        } catch (Exception e) {
            LoggerUtil.error("Grok error generating IM response: " + e.getMessage());
            return "Sorry, something went wrong. Please try again!";
        }
    }

    /**
     * Generate and format a response for chat room context.
     * Uses ResponseFormatter to split long responses into multiple messages.
     *
     * @param message The message from the user (including @mention)
     * @param sender The username of the sender
     * @param context The chat context
     * @return List of formatted message parts (may be single element if no splitting needed)
     */
    public List<String> generateFormattedChatResponse(String message, String sender, ChatContext context) {
        if (!active) {
            return List.of();
        }

        try {
            String effectiveSender = (sender == null || sender.trim().isEmpty()) ? "Unknown" : sender;
            String query = extractQuery(message);
            String memoryKey = buildMemoryKey(effectiveSender, ResponseContext.CHAT_ROOM);

            LoggerUtil.info(String.format("Grok processing chat query from %s: '%s' [AI: %s, Formatter: %s, memKey: %s]",
                    effectiveSender, query, aiEnabled ? "ON" : "OFF",
                    responseFormatter != null ? "ON" : "OFF", memoryKey));

            // Add user message to conversation history (keyed by user+context)
            conversationMemory.addUserMessage(memoryKey, query);

            String rawResponse;
            if (aiEnabled) {
                rawResponse = generateAIResponseWithContext(query, memoryKey, ResponseContext.CHAT_ROOM);
            } else {
                rawResponse = generateBasicResponse(query, effectiveSender, context);
            }

            // Add bot response to conversation history
            conversationMemory.addBotMessage(memoryKey, rawResponse);

            // Format the response (split if needed)
            if (responseFormatter != null) {
                ResponseFormatter.FormattedResponse formatted =
                    responseFormatter.format(rawResponse, ResponseContext.CHAT_ROOM);
                LoggerUtil.info(String.format("Grok chat response formatted: %d parts, aiSplit=%b, truncated=%b",
                        formatted.messages().size(), formatted.wasAiSplit(), formatted.wasTruncated()));
                return formatted.messages();
            }

            return List.of(rawResponse);

        } catch (Exception e) {
            LoggerUtil.error("Grok error generating formatted chat response: " + e.getMessage());
            return List.of("Sorry, something went wrong. Please try again!");
        }
    }

    /**
     * Generate and format a response for IM context.
     * Uses ResponseFormatter to split long responses into multiple messages.
     *
     * @param message The message from the user
     * @param sender The username of the sender
     * @return List of formatted message parts (may be single element if no splitting needed)
     */
    public List<String> generateFormattedIMResponse(String message, String sender) {
        if (!active) {
            return List.of();
        }

        try {
            String effectiveSender = (sender == null || sender.trim().isEmpty()) ? "Unknown" : sender;
            String memoryKey = buildMemoryKey(effectiveSender, ResponseContext.INSTANT_MESSAGE);

            LoggerUtil.info(String.format("Grok processing IM from %s: '%s' [AI: %s, Formatter: %s, memKey: %s]",
                    effectiveSender, message, aiEnabled ? "ON" : "OFF",
                    responseFormatter != null ? "ON" : "OFF", memoryKey));

            // Add user message to conversation history (keyed by user+context)
            conversationMemory.addUserMessage(memoryKey, message);

            String rawResponse;
            if (aiEnabled) {
                rawResponse = generateAIResponseWithContext(message, memoryKey, ResponseContext.INSTANT_MESSAGE);
            } else {
                rawResponse = generateBasicResponse(message, effectiveSender, null);
            }

            // Add bot response to conversation history
            conversationMemory.addBotMessage(memoryKey, rawResponse);

            // Format the response (split if needed)
            if (responseFormatter != null) {
                ResponseFormatter.FormattedResponse formatted =
                    responseFormatter.format(rawResponse, ResponseContext.INSTANT_MESSAGE);
                LoggerUtil.info(String.format("Grok IM response formatted: %d parts, aiSplit=%b, truncated=%b",
                        formatted.messages().size(), formatted.wasAiSplit(), formatted.wasTruncated()));
                return formatted.messages();
            }

            return List.of(rawResponse);

        } catch (Exception e) {
            LoggerUtil.error("Grok error generating formatted IM response: " + e.getMessage());
            return List.of("Sorry, something went wrong. Please try again!");
        }
    }

    /**
     * Generate an AI-powered response with context-aware character limits.
     *
     * @param query The user's query
     * @param memoryKey The memory key (user:context format, e.g., "bob:CHAT" or "bob:IM")
     * @param context The response context for character limits
     */
    private String generateAIResponseWithContext(String query, String memoryKey, ResponseContext context) {
        try {
            List<ConversationMemoryManager.Message> history = conversationMemory.getHistory(memoryKey);
            List<ConversationMemoryManager.Message> previousHistory = history.isEmpty() ?
                    Collections.emptyList() :
                    history.subList(0, Math.max(0, history.size() - 1));

            // Use the context-aware method that includes character limit prompts
            return grokService.generateResponseWithContext(query, previousHistory, null, context);

        } catch (java.io.IOException e) {
            LoggerUtil.warn("Grok API unavailable: " + e.getMessage());
            return "[SYSTEM ERROR] My neural circuits are temporarily offline.";
        } catch (Exception e) {
            LoggerUtil.error("AI response generation failed: " + e.getMessage());
            throw new RuntimeException("AI generation failed", e);
        }
    }

    /**
     * Generate an AI-powered response using Grok with conversation memory.
     *
     * @param query The user's query
     * @param memoryKey The memory key (user:context format, e.g., "bob:CHAT")
     * @param context The chat context (unused but kept for API compatibility)
     */
    private String generateAIResponse(String query, String memoryKey, ChatContext context) {
        try {
            // Get conversation history (excludes current user message which was already added)
            List<ConversationMemoryManager.Message> history = conversationMemory.getHistory(memoryKey);

            // Remove the last message (current user message) since we'll add it fresh in the API call
            List<ConversationMemoryManager.Message> previousHistory = history.isEmpty() ?
                    java.util.Collections.emptyList() :
                    history.subList(0, Math.max(0, history.size() - 1));

            // Generate AI response with conversation history
            String aiResponse = grokService.generateResponseWithHistory(query, previousHistory, null);

            return aiResponse;

        } catch (java.io.IOException e) {
            // Grok/xAi API is down or unreachable - return robotic error message
            LoggerUtil.warn("Grok API unavailable for Grok bot: " + e.getMessage());
            return "[SYSTEM ERROR] My neural circuits are temporarily offline. Please try again later.";
        } catch (Exception e) {
            LoggerUtil.error("AI response generation failed: " + e.getMessage());
            throw new RuntimeException("AI generation failed", e);
        }
    }

    /**
     * Generate a basic response without AI (Phase 2 implementation with knowledge).
     * This will be replaced with AI-powered responses in Phase 3.
     */
    private String generateBasicResponse(String query, String sender, ChatContext context) {
        String lowerQuery = query.toLowerCase().trim();

        // Greeting detection
        if (lowerQuery.matches(".*\\b(hi|hello|hey|sup|yo)\\b.*")) {
            return String.format("Hey %s! I'm Grok, your AI assistant. Ask me about Dialtone, sports, crypto, or anything else!", sender);
        }

        // Help request
        if (lowerQuery.matches(".*\\b(help|what can you do|commands)\\b.*")) {
            return "I can help you with:\n" +
                   "- Information about the Dialtone project\n" +
                   "- Technical details (architecture, protocols)\n" +
                   "- Build and development commands\n" +
                   "- Sports scores and stats (coming soon)\n" +
                   "- Cryptocurrency prices (coming soon)\n" +
                   "Just @Grok and ask away!";
        }

        // Build/development commands
        if (lowerQuery.matches(".*\\b(build|compile|run|test|mvn|maven)\\b.*")) {
            return "Common build commands:\n" +
                   "- mvn clean package (build project)\n" +
                   "- mvn test (run tests)\n" +
                   "- java -jar target/dialtone-1.0-SNAPSHOT.jar --server (run server)";
        }

        // Features query
        if (lowerQuery.matches(".*\\b(features|capabilities|can do|support)\\b.*")) {
            return "Dialtone key features:\n" +
                   "- FDO UI generation and templating\n" +
                   "- DOD art delivery system\n" +
                   "- Real-time chat and instant messaging\n" +
                   "- AI-powered chat bots";
        }

        // Dialtone project overview
        if (lowerQuery.matches(".*\\b(dialtone|project|what is this|about|overview)\\b.*")) {
            return "Dialtone is a Netty-based v3 protocol server that recreates the classic instant messenger experience! " +
                   "It supports FDO UI generation, DOD art delivery, real-time chat, and AI-powered features like me. " +
                   "Built with Java 17 and Netty for high-performance async networking.";
        }

        // Technical queries - basic responses
        if (lowerQuery.matches(".*\\b(how|what|protocol|architecture|fdo|dod|netty|frame|token)\\b.*")) {
            return "For technical details about Dialtone's architecture, FDO/DOD systems, and protocol implementation, " +
                   "I'd recommend checking the project documentation or asking more specific questions with AI enabled!";
        }

        // Default response (Phase 2 - still learning)
        return String.format("Hey %s! You said: \"%s\". I'm still learning, but soon I'll be powered by AI to give you smart answers!", sender, query);
    }

    /**
     * Extract the actual query from a message by removing @mention prefix.
     */
    private String extractQuery(String message) {
        // Remove @Grok mention and trim
        String query = MENTION_PATTERN.matcher(message).replaceAll("").trim();

        // If the query is empty after removing mention, return the original
        return query.isEmpty() ? message : query;
    }

    /**
     * Build a memory key that separates Chat and IM conversations.
     * This prevents conversation history from bleeding between contexts.
     *
     * @param username The username
     * @param context The response context (CHAT_ROOM or INSTANT_MESSAGE)
     * @return A composite key like "bob:CHAT" or "bob:IM"
     */
    private String buildMemoryKey(String username, ResponseContext context) {
        String suffix = (context == ResponseContext.INSTANT_MESSAGE) ? ":IM" : ":CHAT";
        return username.toLowerCase() + suffix;
    }

    @Override
    public void onJoinChatRoom(String roomName) {
        LoggerUtil.debug("Grok joined room: " + roomName);
    }

    @Override
    public void onLeaveChatRoom(String roomName) {
        LoggerUtil.info("Grok left room: " + roomName);
    }

    @Override
    public String getDescription() {
        return "AI-powered assistant for Dialtone - ask me about the project, sports, crypto, and more!";
    }

    @Override
    public boolean isActive() {
        return active;
    }

    /**
     * Activate or deactivate the bot.
     * When inactive, the bot will not generate responses.
     *
     * @param active true to activate, false to deactivate
     */
    public void setActive(boolean active) {
        this.active = active;
        LoggerUtil.info("Grok " + (active ? "activated" : "deactivated"));
    }

}
