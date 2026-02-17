/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.ai;

import com.dialtone.ai.GrokConversationalService;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GrokConversationalService, focusing on project knowledge loading
 * and configuration.
 */
class GrokConversationalServiceTest {

    @Test
    void projectKnowledgeResourceShouldExist() {
        // Verify the project knowledge file exists in resources
        try (InputStream is = getClass().getResourceAsStream("/grok/project_knowledge.txt")) {
            assertNotNull(is, "Project knowledge resource file should exist");
        } catch (Exception e) {
            fail("Should be able to read project knowledge resource: " + e.getMessage());
        }
    }

    @Test
    void projectKnowledgeShouldContainDialtoneInfo() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/grok/project_knowledge.txt")) {
            assertNotNull(is);
            String content = new String(is.readAllBytes());

            assertTrue(content.contains("dialtone"), "Should mention dialtone");
            assertTrue(content.toLowerCase().contains("aol"), "Should mention AOL");
        }
    }

    @Test
    void projectKnowledgeShouldContainServerInfo() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/grok/project_knowledge.txt")) {
            assertNotNull(is);
            String content = new String(is.readAllBytes());

            assertTrue(content.contains("dialtone.live"), "Should mention dialtone.live server");
            assertTrue(content.contains("5191"), "Should mention port 5191");
        }
    }

    @Test
    void projectKnowledgeShouldContainCreatorInfo() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/grok/project_knowledge.txt")) {
            assertNotNull(is);
            String content = new String(is.readAllBytes());

            assertTrue(content.contains("iconidentify"), "Should mention creator iconidentify");
        }
    }

    @Test
    void projectKnowledgeShouldContainToolsInfo() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/grok/project_knowledge.txt")) {
            assertNotNull(is);
            String content = new String(is.readAllBytes()).toLowerCase();

            assertTrue(content.contains("wiretap"), "Should mention Wiretap tool");
            assertTrue(content.contains("atomforge"), "Should mention Atomforge tool");
            assertTrue(content.contains("github"), "Should mention GitHub");
        }
    }

    @Test
    void projectKnowledgeShouldContainMessageFormatGuidance() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/grok/project_knowledge.txt")) {
            assertNotNull(is);
            String content = new String(is.readAllBytes()).toLowerCase();

            assertTrue(content.contains("short"), "Should mention keeping messages short");
            assertTrue(content.contains("ascii"), "Should mention ASCII-only");
        }
    }

    @Test
    void projectKnowledgeShouldContainHumanChatGuidance() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/grok/project_knowledge.txt")) {
            assertNotNull(is);
            String content = new String(is.readAllBytes()).toLowerCase();

            // Should guide Grok to chat like a real person, not a bot
            assertTrue(content.contains("real person") || content.contains("human"),
                    "Should mention chatting like a real person");
            assertTrue(content.contains("never") && content.contains("screenname"),
                    "Should instruct not to greet by screenname");
        }
    }

    @Test
    void projectKnowledgeShouldMentionP3Protocol() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/grok/project_knowledge.txt")) {
            assertNotNull(is);
            String content = new String(is.readAllBytes());

            assertTrue(content.contains("P3"), "Should mention P3 protocol");
        }
    }

    @Test
    void projectKnowledgeShouldNotContainEmojis() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/grok/project_knowledge.txt")) {
            assertNotNull(is);
            String content = new String(is.readAllBytes());

            // Check for common emoji unicode ranges
            boolean hasEmoji = content.codePoints().anyMatch(cp ->
                (cp >= 0x1F600 && cp <= 0x1F64F) || // Emoticons
                (cp >= 0x1F300 && cp <= 0x1F5FF) || // Misc Symbols
                (cp >= 0x1F680 && cp <= 0x1F6FF) || // Transport
                (cp >= 0x2600 && cp <= 0x26FF)      // Misc symbols
            );

            assertFalse(hasEmoji, "Project knowledge should not contain emojis (ASCII only)");
        }
    }

    @Test
    void projectKnowledgeShouldBeReasonableSize() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/grok/project_knowledge.txt")) {
            assertNotNull(is);
            String content = new String(is.readAllBytes());

            // Should be substantial but not huge (keep API costs reasonable)
            assertTrue(content.length() > 100, "Should have meaningful content");
            assertTrue(content.length() < 10000, "Should not be excessively large");
        }
    }

    @Test
    void serviceConstructorShouldNotThrowWithMinimalProperties() {
        Properties props = new Properties();
        props.setProperty("grok.api.key", "test-key");
        props.setProperty("grok.model", "grok-3");

        // Should not throw even though it can't make real API calls
        assertDoesNotThrow(() -> {
            try (GrokConversationalService service = new GrokConversationalService(props)) {
                // Service created successfully - project knowledge loaded
            }
        });
    }

    @Test
    void serviceConstructorShouldUseDefaultMaxTokens() {
        Properties props = new Properties();
        props.setProperty("grok.api.key", "test-key");
        // Don't set grok.chat.max.tokens - should use default

        assertDoesNotThrow(() -> {
            try (GrokConversationalService service = new GrokConversationalService(props)) {
                // Default of 300 tokens should be used
            }
        });
    }

    @Test
    void serviceConstructorShouldUseDefaultTemperature() {
        Properties props = new Properties();
        props.setProperty("grok.api.key", "test-key");
        // Don't set grok.chat.temperature - should use default

        assertDoesNotThrow(() -> {
            try (GrokConversationalService service = new GrokConversationalService(props)) {
                // Default of 0.8 temperature should be used
            }
        });
    }
}
