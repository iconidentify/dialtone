/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.web.services;

import com.dialtone.db.DatabaseManager;
import com.dialtone.web.services.OAuthBaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OAuthBaseService PKCE and state management functionality.
 */
class OAuthBaseServiceTest {

    private TestOAuthService oauthService;

    @BeforeEach
    void setUp() {
        oauthService = new TestOAuthService();
    }

    @Nested
    @DisplayName("PKCE Generation Tests")
    class PkceGenerationTests {

        @Test
        @DisplayName("Should generate unique secure states")
        void shouldGenerateUniqueSecureStates() {
            String state1 = oauthService.testGenerateSecureState();
            String state2 = oauthService.testGenerateSecureState();

            assertNotNull(state1);
            assertNotNull(state2);
            assertNotEquals(state1, state2, "States should be unique");
            assertTrue(state1.length() >= 32, "State should be sufficiently long for UUID format");
        }

        @Test
        @DisplayName("Should generate valid PKCE code verifier")
        void shouldGenerateValidCodeVerifier() {
            String codeVerifier = oauthService.testGenerateCodeVerifier();

            assertNotNull(codeVerifier);
            // 32 bytes = 43 base64url characters (without padding)
            assertEquals(43, codeVerifier.length(), "Code verifier should be 43 chars (32 bytes base64url)");

            // Should be valid base64url
            assertDoesNotThrow(() -> Base64.getUrlDecoder().decode(codeVerifier));
        }

        @Test
        @DisplayName("Should generate unique code verifiers")
        void shouldGenerateUniqueCodeVerifiers() {
            String verifier1 = oauthService.testGenerateCodeVerifier();
            String verifier2 = oauthService.testGenerateCodeVerifier();

            assertNotNull(verifier1);
            assertNotNull(verifier2);
            assertNotEquals(verifier1, verifier2, "Code verifiers should be unique");
        }

        @Test
        @DisplayName("Should generate valid code challenge from verifier")
        void shouldGenerateValidCodeChallenge() {
            String codeVerifier = oauthService.testGenerateCodeVerifier();
            String codeChallenge = oauthService.testGenerateCodeChallenge(codeVerifier);

            assertNotNull(codeChallenge);
            // SHA256 = 32 bytes = 43 base64url characters
            assertEquals(43, codeChallenge.length(), "Code challenge should be 43 chars (SHA256 base64url)");

            // Should be valid base64url
            assertDoesNotThrow(() -> Base64.getUrlDecoder().decode(codeChallenge));
        }

        @Test
        @DisplayName("Code challenge should be deterministic for same verifier")
        void codeChallengeShoudBeDeterministic() {
            String codeVerifier = "test_verifier_12345678901234567890";
            String challenge1 = oauthService.testGenerateCodeChallenge(codeVerifier);
            String challenge2 = oauthService.testGenerateCodeChallenge(codeVerifier);

            assertEquals(challenge1, challenge2, "Same verifier should produce same challenge");
        }

        @Test
        @DisplayName("Different verifiers should produce different challenges")
        void differentVerifiersShouldProduceDifferentChallenges() {
            String verifier1 = oauthService.testGenerateCodeVerifier();
            String verifier2 = oauthService.testGenerateCodeVerifier();

            String challenge1 = oauthService.testGenerateCodeChallenge(verifier1);
            String challenge2 = oauthService.testGenerateCodeChallenge(verifier2);

            assertNotEquals(challenge1, challenge2, "Different verifiers should produce different challenges");
        }
    }

    @Nested
    @DisplayName("State Management Tests")
    class StateManagementTests {

        @Test
        @DisplayName("Should store and retrieve state and verifier")
        void shouldStoreAndRetrieveStateAndVerifier() {
            String state = "test-state-123";
            String verifier = "test-verifier-456";

            oauthService.testStoreStateAndVerifier(state, verifier);

            assertTrue(oauthService.testValidateState(state), "State should be valid");
            assertEquals(verifier, oauthService.testGetCodeVerifier(state), "Verifier should be retrievable");
        }

        @Test
        @DisplayName("Should reject unknown state")
        void shouldRejectUnknownState() {
            assertFalse(oauthService.testValidateState("unknown-state"), "Unknown state should be invalid");
        }

        @Test
        @DisplayName("Should cleanup state and verifier")
        void shouldCleanupStateAndVerifier() {
            String state = "test-state-cleanup";
            String verifier = "test-verifier-cleanup";

            oauthService.testStoreStateAndVerifier(state, verifier);
            assertTrue(oauthService.testValidateState(state));

            oauthService.testCleanupStateAndVerifier(state);

            assertFalse(oauthService.testValidateState(state), "State should be invalid after cleanup");
            assertNull(oauthService.testGetCodeVerifier(state), "Verifier should be null after cleanup");
        }

        @Test
        @DisplayName("Should reject expired states")
        void shouldRejectExpiredStates() throws Exception {
            String state = "expired-state";
            String verifier = "expired-verifier";

            // Access the private pendingStates map and add an old entry
            Field pendingStatesField = OAuthBaseService.class.getDeclaredField("pendingStates");
            pendingStatesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, Long> pendingStates =
                (ConcurrentHashMap<String, Long>) pendingStatesField.get(oauthService);

            // Store a state with timestamp 11 minutes ago (past the 10 minute timeout)
            long elevenMinutesAgo = System.currentTimeMillis() - (11 * 60 * 1000);
            pendingStates.put(state, elevenMinutesAgo);

            assertFalse(oauthService.testValidateState(state), "Expired state should be invalid");
        }

        @Test
        @DisplayName("Should cleanup expired states during store")
        void shouldCleanupExpiredStatesDuringStore() throws Exception {
            Field pendingStatesField = OAuthBaseService.class.getDeclaredField("pendingStates");
            pendingStatesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, Long> pendingStates =
                (ConcurrentHashMap<String, Long>) pendingStatesField.get(oauthService);

            // Add an expired state
            String expiredState = "expired-state";
            long elevenMinutesAgo = System.currentTimeMillis() - (11 * 60 * 1000);
            pendingStates.put(expiredState, elevenMinutesAgo);

            Field pendingVerifiersField = OAuthBaseService.class.getDeclaredField("pendingCodeVerifiers");
            pendingVerifiersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, String> pendingVerifiers =
                (ConcurrentHashMap<String, String>) pendingVerifiersField.get(oauthService);
            pendingVerifiers.put(expiredState, "expired-verifier");

            // Store a new state (which triggers cleanup)
            oauthService.testStoreStateAndVerifier("new-state", "new-verifier");

            // The expired state should have been cleaned up
            assertFalse(pendingStates.containsKey(expiredState), "Expired state should be cleaned up");
            assertFalse(pendingVerifiers.containsKey(expiredState), "Expired verifier should be cleaned up");
        }
    }

    @Nested
    @DisplayName("Utility Method Tests")
    class UtilityMethodTests {

        @Test
        @DisplayName("nullSafeEquals should handle null comparisons correctly")
        void nullSafeEqualsShouldHandleNulls() {
            assertTrue(oauthService.testNullSafeEquals(null, null), "null == null");
            assertFalse(oauthService.testNullSafeEquals(null, "a"), "null != 'a'");
            assertFalse(oauthService.testNullSafeEquals("a", null), "'a' != null");
            assertTrue(oauthService.testNullSafeEquals("a", "a"), "'a' == 'a'");
            assertFalse(oauthService.testNullSafeEquals("a", "b"), "'a' != 'b'");
        }
    }

    /**
     * Test subclass that exposes protected methods for testing.
     */
    private static class TestOAuthService extends OAuthBaseService {
        TestOAuthService() {
            super(null, "http://localhost/callback");
        }

        // Expose protected methods for testing
        String testGenerateSecureState() {
            return generateSecureState();
        }

        String testGenerateCodeVerifier() {
            return generateCodeVerifier();
        }

        String testGenerateCodeChallenge(String verifier) {
            return generateCodeChallenge(verifier);
        }

        void testStoreStateAndVerifier(String state, String verifier) {
            storeStateAndVerifier(state, verifier);
        }

        boolean testValidateState(String state) {
            return validateState(state);
        }

        String testGetCodeVerifier(String state) {
            return getCodeVerifier(state);
        }

        void testCleanupStateAndVerifier(String state) {
            cleanupStateAndVerifier(state);
        }

        boolean testNullSafeEquals(String a, String b) {
            return nullSafeEquals(a, b);
        }
    }
}
