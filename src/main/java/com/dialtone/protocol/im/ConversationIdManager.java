/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.im;

import com.dialtone.utils.LoggerUtil;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages symmetric conversation IDs for instant message conversations.
 *
 * The AOL client protocol uses response IDs for window matching via the "magic table".
 * A critical insight: the SAME response ID is used bidirectionally for a conversation.
 *
 * Example:
 * - Bobby sends IM to TOSAdvisor → server assigns conversation_id = 8417
 * - Server delivers to TOSAdvisor with response_id = 8417
 * - TOSAdvisor replies → client sends response_id = 8417
 * - Server delivers to Bobby with response_id = 8417 (SAME ID!)
 *
 * Each client stores the response_id in their LOCAL magic table:
 * - Bobby's magic table: {8417 → "TOSAdvisor's IM window"}
 * - TOSAdvisor's magic table: {8417 → "Bobby's IM window"}
 *
 * No conflict occurs because magic tables are client-local (separate memory spaces).
 *
 * This manager provides:
 * 1. Symmetric ID generation (Bobby↔TOSAdvisor = TOSAdvisor↔Bobby)
 * 2. Conversation participant lookup (response_id → {user1, user2})
 * 3. Thread-safe concurrent access
 *
 * Thread-safe singleton.
 */
public class ConversationIdManager {

    private static final ConversationIdManager INSTANCE = new ConversationIdManager();

    /**
     * Maps response_id → conversation participants {user1, user2}
     */
    private final Map<Integer, ConversationParticipants> conversationMap;

    /**
     * Maps sorted user pair → response_id for lookup
     */
    private final Map<String, Integer> pairToIdMap;

    /**
     * Counter for generating sequential conversation IDs
     */
    private final AtomicInteger idCounter;

    /**
e     * Maximum response_id value (16-bit unsigned limit).
     * AOL client protocol requires response_id <= 65535 for magic table compatibility.
     */
    private static final int MAX_RESPONSE_ID = 65535;

    /**
     * Minimum response_id value for server-generated IDs.
     * Client IDs are typically small (1-1000), so we start server IDs at 10000
     * to avoid conflicts while staying within the 16-bit constraint.
     */
    private static final int MIN_RESPONSE_ID = 10000;

    /**
     * Starting ID for conversation ID generation.
     * Must be >= MIN_RESPONSE_ID and <= MAX_RESPONSE_ID.
     */
    private static final int STARTING_ID = MIN_RESPONSE_ID;

    private ConversationIdManager() {
        this.conversationMap = new ConcurrentHashMap<>();
        this.pairToIdMap = new ConcurrentHashMap<>();
        this.idCounter = new AtomicInteger(STARTING_ID);
    }

    public static ConversationIdManager getInstance() {
        return INSTANCE;
    }

    /**
     * Gets or creates a conversation ID for the given user pair.
     *
     * This method is SYMMETRIC - the order of users doesn't matter:
     * - getOrCreateConversationId("Bobby", "TOSAdvisor") = 8417
     * - getOrCreateConversationId("TOSAdvisor", "Bobby") = 8417 (same!)
     *
     * @param user1 First participant
     * @param user2 Second participant
     * @return The conversation ID (same regardless of parameter order)
     */
    public int getOrCreateConversationId(String user1, String user2) {
        if (user1 == null || user2 == null) {
            throw new IllegalArgumentException("Users cannot be null");
        }

        if (user1.equals(user2)) {
            throw new IllegalArgumentException("Cannot create conversation with self");
        }

        // Create canonical key (sorted to ensure symmetry)
        String pairKey = createPairKey(user1, user2);

        // Use computeIfAbsent for atomic get-or-create
        return pairToIdMap.computeIfAbsent(pairKey, key -> {
            // This lambda is only called if the key is absent
            int conversationId = idCounter.getAndIncrement();

            // Check if we've exceeded the 16-bit unsigned limit
            if (conversationId > MAX_RESPONSE_ID) {
                LoggerUtil.warn("[ConversationIdManager] response_id reached limit " +
                    MAX_RESPONSE_ID + ", wrapping to " + MIN_RESPONSE_ID +
                    " and clearing " + conversationMap.size() + " existing conversations");

                // Clear all existing conversations to prevent ID collisions
                conversationMap.clear();
                pairToIdMap.clear();

                // Reset counter and generate new ID
                idCounter.set(MIN_RESPONSE_ID);
                conversationId = idCounter.getAndIncrement();
            }

            // Final validation (should never trigger, but defensive programming)
            if (conversationId > MAX_RESPONSE_ID) {
                LoggerUtil.error("[ConversationIdManager] CRITICAL: response_id " + conversationId +
                    " still exceeds limit " + MAX_RESPONSE_ID + " after wrap!");
            }

            // Log ID generation for debugging
            LoggerUtil.debug("[ConversationIdManager] Generated response_id " + conversationId +
                " for conversation: " + user1 + " ↔ " + user2);

            ConversationParticipants participants = new ConversationParticipants(user1, user2);
            conversationMap.put(conversationId, participants);
            return conversationId;
        });
    }

    /**
     * Checks if a conversation already exists between two users without creating one.
     *
     * This method is SYMMETRIC - the order of users doesn't matter:
     * - hasConversation("Bobby", "TOSAdvisor") = hasConversation("TOSAdvisor", "Bobby")
     *
     * @param user1 First participant
     * @param user2 Second participant
     * @return true if a conversation already exists, false otherwise
     */
    public boolean hasConversation(String user1, String user2) {
        if (user1 == null || user2 == null) {
            return false;
        }

        if (user1.equals(user2)) {
            return false;
        }

        // Create canonical key (sorted to ensure symmetry)
        String pairKey = createPairKey(user1, user2);
        return pairToIdMap.containsKey(pairKey);
    }

    /**
     * Looks up the conversation participants for a given response ID.
     *
     * When a client replies with a response_id, this method determines
     * who the conversation is between.
     *
     * @param conversationId The response ID from the client
     * @return The conversation participants, or null if not found
     */
    public ConversationParticipants getConversationParticipants(int conversationId) {
        return conversationMap.get(conversationId);
    }

    /**
     * Gets the other participant in a conversation.
     *
     * Example:
     * - Conversation 8417 is between Bobby and TOSAdvisor
     * - getOtherParticipant(8417, "Bobby") returns "TOSAdvisor"
     * - getOtherParticipant(8417, "TOSAdvisor") returns "Bobby"
     *
     * @param conversationId The conversation ID
     * @param user One of the participants
     * @return The other participant, or null if not found or user not in conversation
     */
    public String getOtherParticipant(int conversationId, String user) {
        ConversationParticipants participants = conversationMap.get(conversationId);
        if (participants == null) {
            return null;
        }

        if (participants.user1.equals(user)) {
            return participants.user2;
        } else if (participants.user2.equals(user)) {
            return participants.user1;
        } else {
            return null; // User not in this conversation
        }
    }

    /**
     * Removes a conversation (for cleanup when users log out).
     *
     * @param conversationId The conversation ID to remove
     */
    public void removeConversation(int conversationId) {
        ConversationParticipants participants = conversationMap.remove(conversationId);
        if (participants != null) {
            String pairKey = createPairKey(participants.user1, participants.user2);
            pairToIdMap.remove(pairKey);
        }
    }

    /**
     * Removes all conversations involving a specific user (for logout cleanup).
     *
     * @param username The user logging out
     */
    public void removeConversationsForUser(String username) {
        if (username == null) {
            return;
        }

        // Find all conversations involving this user
        conversationMap.entrySet().removeIf(entry -> {
            ConversationParticipants participants = entry.getValue();
            boolean shouldRemove = participants.user1.equals(username) ||
                                 participants.user2.equals(username);

            if (shouldRemove) {
                // Also remove from pair map
                String pairKey = createPairKey(participants.user1, participants.user2);
                pairToIdMap.remove(pairKey);
            }

            return shouldRemove;
        });
    }

    /**
     * Clears all conversations (for testing or server shutdown).
     */
    public void clear() {
        conversationMap.clear();
        pairToIdMap.clear();
        idCounter.set(STARTING_ID);
    }

    /**
     * Gets the number of active conversations (for testing/monitoring).
     */
    public int size() {
        return conversationMap.size();
    }

    /**
     * Creates a canonical pair key by sorting usernames.
     * Ensures symmetry: (A, B) and (B, A) produce the same key.
     */
    private String createPairKey(String user1, String user2) {
        String[] users = {user1, user2};
        Arrays.sort(users);
        return users[0] + ":" + users[1];
    }

    /**
     * Represents the two participants in a conversation.
     */
    public static class ConversationParticipants {
        public final String user1;
        public final String user2;

        public ConversationParticipants(String user1, String user2) {
            this.user1 = user1;
            this.user2 = user2;
        }

        /**
         * Checks if a user is a participant in this conversation.
         */
        public boolean contains(String username) {
            return user1.equals(username) || user2.equals(username);
        }

        /**
         * Gets both participants as a set.
         */
        public Set<String> getParticipants() {
            return Set.of(user1, user2);
        }

        @Override
        public String toString() {
            return String.format("(%s ↔ %s)", user1, user2);
        }
    }
}
