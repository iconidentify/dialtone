/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.chat.bot;

import com.dialtone.utils.LoggerUtil;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Singleton registry for managing virtual chat room participants (bots).
 * Thread-safe implementation that routes messages to appropriate bots
 * and manages bot lifecycle.
 */
public class ChatBotRegistry {

    private static ChatBotRegistry INSTANCE = null;
    private static final long DEFAULT_RESPONSE_TIMEOUT_MS = 30000; // 30 seconds for AI responses

    private final ConcurrentHashMap<String, VirtualUser> bots;
    private final long responseTimeoutMs;

    private ChatBotRegistry(long responseTimeoutMs) {
        this.bots = new ConcurrentHashMap<>();
        this.responseTimeoutMs = responseTimeoutMs;
        LoggerUtil.info("ChatBotRegistry initialized with timeout: " + responseTimeoutMs + "ms");
    }

    /**
     * Initialize the singleton instance with configuration.
     * Must be called before getInstance() on server startup.
     *
     * @param properties Application properties containing bot.response.timeout.ms
     */
    public static synchronized void initialize(Properties properties) {
        if (INSTANCE != null) {
            LoggerUtil.warn("ChatBotRegistry already initialized, ignoring re-initialization");
            return;
        }

        long timeoutMs = DEFAULT_RESPONSE_TIMEOUT_MS;
        if (properties != null) {
            String timeoutProperty = properties.getProperty("bot.response.timeout.ms");
            if (timeoutProperty != null) {
                try {
                    timeoutMs = Long.parseLong(timeoutProperty);
                    LoggerUtil.info("Using configured bot response timeout: " + timeoutMs + "ms");
                } catch (NumberFormatException e) {
                    LoggerUtil.warn("Invalid bot.response.timeout.ms value: " + timeoutProperty + ", using default: " + DEFAULT_RESPONSE_TIMEOUT_MS + "ms");
                }
            }
        }

        INSTANCE = new ChatBotRegistry(timeoutMs);
    }

    /**
     * Get the singleton instance.
     * If not initialized via initialize(), creates instance with default timeout.
     *
     * @return The singleton instance
     */
    public static synchronized ChatBotRegistry getInstance() {
        if (INSTANCE == null) {
            LoggerUtil.warn("ChatBotRegistry not initialized, using default timeout: " + DEFAULT_RESPONSE_TIMEOUT_MS + "ms");
            INSTANCE = new ChatBotRegistry(DEFAULT_RESPONSE_TIMEOUT_MS);
        }
        return INSTANCE;
    }

    /**
     * Register a bot in the chat system.
     * If a bot with the same username exists, it will be replaced.
     *
     * @param bot The bot to register
     * @throws IllegalArgumentException if bot is null or has null/empty username
     */
    public void registerBot(VirtualUser bot) {
        if (bot == null) {
            throw new IllegalArgumentException("Bot cannot be null");
        }

        String username = bot.getUsername();
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Bot username cannot be null or empty");
        }

        VirtualUser existing = bots.put(username.toLowerCase(), bot);

        if (existing != null) {
            LoggerUtil.warn("Bot '" + username + "' was already registered, replacing existing bot");
        } else {
            LoggerUtil.info("Bot '" + username + "' registered successfully");
        }
    }

    /**
     * Unregister a bot from the chat system.
     *
     * @param username The username of the bot to unregister
     * @return true if the bot was removed, false if not found
     */
    public boolean unregisterBot(String username) {
        if (username == null) {
            return false;
        }

        VirtualUser removed = bots.remove(username.toLowerCase());

        if (removed != null) {
            LoggerUtil.info("Bot '" + username + "' unregistered");
            return true;
        }

        return false;
    }

    /**
     * Get a specific bot by username.
     *
     * @param username The bot's username (case-insensitive)
     * @return The bot, or null if not found
     */
    public VirtualUser getBot(String username) {
        if (username == null) {
            return null;
        }
        return bots.get(username.toLowerCase());
    }

    /**
     * Get all registered bots.
     *
     * @return List of all registered bots (original username casing preserved)
     */
    public List<VirtualUser> getAllBots() {
        return List.copyOf(bots.values());
    }

    /**
     * Get all active bots (operational and ready to respond).
     *
     * @return List of active bots
     */
    public List<VirtualUser> getActiveBots() {
        return bots.values().stream()
                .filter(VirtualUser::isActive)
                .collect(Collectors.toList());
    }

    /**
     * Check if a username belongs to a registered bot.
     *
     * @param username The username to check (case-insensitive)
     * @return true if this is a bot's username
     */
    public boolean isBot(String username) {
        if (username == null) {
            return false;
        }
        return bots.containsKey(username.toLowerCase());
    }

    /**
     * Process a chat message and generate bot responses asynchronously.
     * All bots that should respond to the message will generate responses.
     *
     * @param message The chat message content
     * @param sender The username of the sender
     * @param context Context about the conversation
     * @return CompletableFuture that completes with list of bot responses
     */
    public CompletableFuture<List<BotResponse>> processMessage(String message, String sender, ChatContext context) {
        // Don't respond to messages from bots to prevent loops
        if (isBot(sender)) {
            return CompletableFuture.completedFuture(List.of());
        }

        // Find all bots that should respond
        List<VirtualUser> respondingBots = bots.values().stream()
                .filter(bot -> bot.isActive() && bot.shouldRespondTo(message, sender))
                .collect(Collectors.toList());

        if (respondingBots.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        // Generate responses asynchronously with timeout
        List<CompletableFuture<BotResponse>> responseFutures = respondingBots.stream()
                .map(bot -> generateResponseAsync(bot, message, sender, context))
                .collect(Collectors.toList());

        // Wait for all responses
        return CompletableFuture.allOf(responseFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> responseFutures.stream()
                        .map(CompletableFuture::join)
                        .filter(response -> response.content() != null)
                        .collect(Collectors.toList()));
    }

    /**
     * Generate a single bot response asynchronously with timeout.
     */
    private CompletableFuture<BotResponse> generateResponseAsync(VirtualUser bot, String message,
                                                                   String sender, ChatContext context) {
        long startTime = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            try {
                String response = bot.generateResponse(message, sender, context);
                long elapsedMs = System.currentTimeMillis() - startTime;

                LoggerUtil.info(String.format("Bot '%s' generated response in %dms", bot.getUsername(), elapsedMs));

                return new BotResponse(bot.getUsername(), response);

            } catch (Exception e) {
                LoggerUtil.error("Bot '" + bot.getUsername() + "' failed to generate response: " + e.getMessage());
                return new BotResponse(bot.getUsername(), null);
            }
        }).orTimeout(responseTimeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    LoggerUtil.warn("Bot '" + bot.getUsername() + "' response timed out after " + responseTimeoutMs + "ms");
                    return new BotResponse(bot.getUsername(), null);
                });
    }

    /**
     * Process a chat message and generate formatted bot responses asynchronously.
     * Uses GrokBot's formatted response methods to handle message splitting.
     *
     * @param message The chat message content
     * @param sender The username of the sender
     * @param context Context about the conversation
     * @return CompletableFuture that completes with list of formatted bot responses
     */
    public CompletableFuture<List<FormattedBotResponse>> processMessageFormatted(
            String message, String sender, ChatContext context) {
        // Don't respond to messages from bots to prevent loops
        if (isBot(sender)) {
            return CompletableFuture.completedFuture(List.of());
        }

        // Find all bots that should respond
        List<VirtualUser> respondingBots = bots.values().stream()
                .filter(bot -> bot.isActive() && bot.shouldRespondTo(message, sender))
                .collect(Collectors.toList());

        if (respondingBots.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        LoggerUtil.info(String.format("Processing message for %d responding bot(s)", respondingBots.size()));

        // Generate formatted responses asynchronously with timeout
        List<CompletableFuture<FormattedBotResponse>> responseFutures = respondingBots.stream()
                .map(bot -> generateFormattedResponseAsync(bot, message, sender, context))
                .collect(Collectors.toList());

        // Wait for all responses
        return CompletableFuture.allOf(responseFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> responseFutures.stream()
                        .map(CompletableFuture::join)
                        .filter(FormattedBotResponse::hasContent)
                        .collect(Collectors.toList()));
    }

    /**
     * Generate a formatted bot response asynchronously with timeout.
     * If the bot is a GrokBot, uses its formatted response method for message splitting.
     */
    private CompletableFuture<FormattedBotResponse> generateFormattedResponseAsync(
            VirtualUser bot, String message, String sender, ChatContext context) {
        long startTime = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            try {
                List<String> messageParts;
                long delayBetweenMs = 0;

                // Use formatted response methods if available (GrokBot)
                if (bot instanceof GrokBot grokBot) {
                    messageParts = grokBot.generateFormattedChatResponse(message, sender, context);
                    // Get delay from formatter if available
                    if (grokBot.getResponseFormatter() != null) {
                        delayBetweenMs = grokBot.getResponseFormatter().getSplitDelayMs();
                    }
                } else {
                    // Fallback for non-GrokBot: wrap single response
                    String response = bot.generateResponse(message, sender, context);
                    messageParts = (response != null) ? List.of(response) : List.of();
                }

                long elapsedMs = System.currentTimeMillis() - startTime;
                LoggerUtil.info(String.format("Bot '%s' generated %d message part(s) in %dms",
                        bot.getUsername(), messageParts.size(), elapsedMs));

                return new FormattedBotResponse(bot.getUsername(), messageParts, delayBetweenMs);

            } catch (Exception e) {
                LoggerUtil.error("Bot '" + bot.getUsername() + "' failed to generate formatted response: " + e.getMessage());
                return new FormattedBotResponse(bot.getUsername(), List.of(), 0);
            }
        }).orTimeout(responseTimeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    LoggerUtil.warn("Bot '" + bot.getUsername() + "' formatted response timed out after " + responseTimeoutMs + "ms");
                    return new FormattedBotResponse(bot.getUsername(), List.of(), 0);
                });
    }

    /**
     * Clear all registered bots.
     * Primarily for testing purposes.
     */
    public void clear() {
        int count = bots.size();
        bots.clear();
        LoggerUtil.info("ChatBotRegistry cleared (" + count + " bots removed)");
    }

    /**
     * Get the count of registered bots.
     *
     * @return Number of bots in the registry
     */
    public int getBotCount() {
        return bots.size();
    }

    /**
     * Represents a response from a bot.
     */
    public record BotResponse(String botUsername, String content) {
        /**
         * Check if this response contains actual content.
         *
         * @return true if the bot provided a non-null, non-empty response
         */
        public boolean hasContent() {
            return content != null && !content.trim().isEmpty();
        }
    }

    /**
     * Represents a formatted response from a bot with multiple message parts.
     * Used when responses need to be split into multiple messages.
     *
     * @param botUsername The username of the bot that generated the response
     * @param messageParts List of message parts (may be single element if no split)
     * @param delayBetweenMs Recommended delay between sending parts (in milliseconds)
     */
    public record FormattedBotResponse(String botUsername, List<String> messageParts, long delayBetweenMs) {
        /**
         * Check if this response contains actual content.
         *
         * @return true if the bot provided at least one non-empty message part
         */
        public boolean hasContent() {
            return messageParts != null && !messageParts.isEmpty() &&
                   messageParts.stream().anyMatch(s -> s != null && !s.trim().isEmpty());
        }

        /**
         * Check if this is a single-message response (no splitting occurred).
         *
         * @return true if there is only one message part
         */
        public boolean isSingleMessage() {
            return messageParts != null && messageParts.size() == 1;
        }
    }
}
