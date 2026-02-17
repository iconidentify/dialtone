/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.web.security;

import com.dialtone.web.security.CsrfProtectionService;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for CsrfProtectionService.
 * Tests CSRF token generation, validation, and cleanup.
 */
class CsrfProtectionServiceTest {

    private CsrfProtectionService csrfService;

    @Mock
    private Context context;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        csrfService = new CsrfProtectionService();

        // Setup default mock behavior
        when(context.ip()).thenReturn("192.168.1.100");
        when(context.userAgent()).thenReturn("Mozilla/5.0 Test Browser");
        when(context.header("X-Forwarded-For")).thenReturn(null);
        when(context.header("X-Real-IP")).thenReturn(null);
    }

    @Nested
    @DisplayName("Token Generation Tests")
    class TokenGenerationTests {

        @Test
        @DisplayName("Should generate valid CSRF tokens")
        void shouldGenerateValidTokens() {
            String token = csrfService.generateCsrfToken(context);

            assertNotNull(token);
            assertEquals(32, token.length());
            assertTrue(token.matches("^[a-zA-Z0-9]+$"));
        }

        @RepeatedTest(5)
        @DisplayName("Should generate unique tokens")
        void shouldGenerateUniqueTokens() {
            String token1 = csrfService.generateCsrfToken(context);
            String token2 = csrfService.generateCsrfToken(context);

            assertNotEquals(token1, token2);
        }

        @Test
        @DisplayName("Should handle different client identifiers")
        void shouldHandleDifferentClientIdentifiers() {
            // First client
            when(context.ip()).thenReturn("192.168.1.100");
            when(context.userAgent()).thenReturn("Chrome Browser");
            String token1 = csrfService.generateCsrfToken(context);

            // Second client
            when(context.ip()).thenReturn("192.168.1.101");
            when(context.userAgent()).thenReturn("Firefox Browser");
            String token2 = csrfService.generateCsrfToken(context);

            assertNotNull(token1);
            assertNotNull(token2);
            assertNotEquals(token1, token2);
        }
    }

    @Nested
    @DisplayName("Token Validation Tests")
    class TokenValidationTests {

        @Test
        @DisplayName("Should validate correct tokens")
        void shouldValidateCorrectTokens() {
            String token = csrfService.generateCsrfToken(context);

            boolean isValid = csrfService.validateCsrfToken(context, token);

            assertTrue(isValid);
        }

        @Test
        @DisplayName("Should reject null tokens")
        void shouldRejectNullTokens() {
            boolean isValid = csrfService.validateCsrfToken(context, null);

            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should reject empty tokens")
        void shouldRejectEmptyTokens() {
            boolean isValid1 = csrfService.validateCsrfToken(context, "");
            boolean isValid2 = csrfService.validateCsrfToken(context, "   ");

            assertFalse(isValid1);
            assertFalse(isValid2);
        }

        @Test
        @DisplayName("Should reject non-existent tokens")
        void shouldRejectNonExistentTokens() {
            String fakeToken = "NonExistentToken123456789012345";

            boolean isValid = csrfService.validateCsrfToken(context, fakeToken);

            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should reject tokens from different clients")
        void shouldRejectTokensFromDifferentClients() {
            // Generate token for first client
            when(context.ip()).thenReturn("192.168.1.100");
            when(context.userAgent()).thenReturn("Chrome Browser");
            String token = csrfService.generateCsrfToken(context);

            // Try to validate with different client
            when(context.ip()).thenReturn("192.168.1.101");
            when(context.userAgent()).thenReturn("Firefox Browser");
            boolean isValid = csrfService.validateCsrfToken(context, token);

            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should enforce one-time use of tokens")
        void shouldEnforceOneTimeUse() {
            String token = csrfService.generateCsrfToken(context);

            // First validation should succeed
            assertTrue(csrfService.validateCsrfToken(context, token));

            // Second validation should fail (token consumed)
            assertFalse(csrfService.validateCsrfToken(context, token));
        }
    }

    @Nested
    @DisplayName("Token Extraction Tests")
    class TokenExtractionTests {

        @Test
        @DisplayName("Should extract token from X-CSRF-Token header")
        void shouldExtractTokenFromXCsrfTokenHeader() {
            String expectedToken = "TestToken123456789012345678901";
            when(context.header("X-CSRF-Token")).thenReturn(expectedToken);

            String extractedToken = csrfService.extractCsrfToken(context);

            assertEquals(expectedToken, extractedToken);
        }

        @Test
        @DisplayName("Should extract token from Authorization header")
        void shouldExtractTokenFromAuthorizationHeader() {
            String expectedToken = "TestToken123456789012345678901";
            when(context.header("X-CSRF-Token")).thenReturn(null);
            when(context.header("Authorization")).thenReturn("CSRF " + expectedToken);

            String extractedToken = csrfService.extractCsrfToken(context);

            assertEquals(expectedToken, extractedToken);
        }

        @Test
        @DisplayName("Should extract token from form parameter")
        void shouldExtractTokenFromFormParameter() {
            String expectedToken = "TestToken123456789012345678901";
            when(context.header("X-CSRF-Token")).thenReturn(null);
            when(context.header("Authorization")).thenReturn(null);
            when(context.formParam("_csrf")).thenReturn(expectedToken);

            String extractedToken = csrfService.extractCsrfToken(context);

            assertEquals(expectedToken, extractedToken);
        }

        @Test
        @DisplayName("Should extract token from query parameter")
        void shouldExtractTokenFromQueryParameter() {
            String expectedToken = "TestToken123456789012345678901";
            when(context.header("X-CSRF-Token")).thenReturn(null);
            when(context.header("Authorization")).thenReturn(null);
            when(context.formParam("_csrf")).thenReturn(null);
            when(context.queryParam("_csrf")).thenReturn(expectedToken);

            String extractedToken = csrfService.extractCsrfToken(context);

            assertEquals(expectedToken, extractedToken);
        }

        @Test
        @DisplayName("Should return null when no token found")
        void shouldReturnNullWhenNoTokenFound() {
            when(context.header("X-CSRF-Token")).thenReturn(null);
            when(context.header("Authorization")).thenReturn(null);
            when(context.formParam("_csrf")).thenReturn(null);
            when(context.queryParam("_csrf")).thenReturn(null);

            String extractedToken = csrfService.extractCsrfToken(context);

            assertNull(extractedToken);
        }

        @Test
        @DisplayName("Should trim whitespace from extracted tokens")
        void shouldTrimWhitespaceFromExtractedTokens() {
            String expectedToken = "TestToken123456789012345678901";
            when(context.header("X-CSRF-Token")).thenReturn("  " + expectedToken + "  ");

            String extractedToken = csrfService.extractCsrfToken(context);

            assertEquals(expectedToken, extractedToken);
        }
    }

    @Nested
    @DisplayName("CSRF Protection Enforcement Tests")
    class CsrfProtectionEnforcementTests {

        @Test
        @DisplayName("Should allow safe HTTP methods without CSRF tokens")
        void shouldAllowSafeMethodsWithoutCsrfTokens() {
            when(context.method()).thenReturn(io.javalin.http.HandlerType.GET);

            assertDoesNotThrow(() -> csrfService.requireValidCsrfToken(context));
        }

        @Test
        @DisplayName("Should require CSRF tokens for state-changing methods")
        void shouldRequireCsrfTokensForStateChangingMethods() {
            when(context.method()).thenReturn(io.javalin.http.HandlerType.POST);
            when(context.header("X-CSRF-Token")).thenReturn(null);

            assertThrows(CsrfProtectionService.CsrfValidationException.class,
                () -> csrfService.requireValidCsrfToken(context));
        }

        @Test
        @DisplayName("Should accept valid CSRF tokens for state-changing methods")
        void shouldAcceptValidCsrfTokensForStateChangingMethods() {
            String token = csrfService.generateCsrfToken(context);

            when(context.method()).thenReturn(io.javalin.http.HandlerType.POST);
            when(context.header("X-CSRF-Token")).thenReturn(token);

            assertDoesNotThrow(() -> csrfService.requireValidCsrfToken(context));
        }
    }

    @Nested
    @DisplayName("Client Identification Tests")
    class ClientIdentificationTests {

        @Test
        @DisplayName("Should use X-Forwarded-For header when available")
        void shouldUseXForwardedForWhenAvailable() {
            when(context.header("X-Forwarded-For")).thenReturn("203.0.113.1, 192.168.1.100");
            when(context.ip()).thenReturn("10.0.0.1");

            String token1 = csrfService.generateCsrfToken(context);

            // Same forwarded IP should validate
            boolean isValid = csrfService.validateCsrfToken(context, token1);
            assertTrue(isValid);
        }

        @Test
        @DisplayName("Should use X-Real-IP header when X-Forwarded-For not available")
        void shouldUseXRealIpWhenForwardedForNotAvailable() {
            when(context.header("X-Forwarded-For")).thenReturn(null);
            when(context.header("X-Real-IP")).thenReturn("203.0.113.1");
            when(context.ip()).thenReturn("10.0.0.1");

            String token1 = csrfService.generateCsrfToken(context);

            // Same real IP should validate
            boolean isValid = csrfService.validateCsrfToken(context, token1);
            assertTrue(isValid);
        }

        @Test
        @DisplayName("Should fall back to direct IP when no proxy headers")
        void shouldFallBackToDirectIpWhenNoProxyHeaders() {
            when(context.header("X-Forwarded-For")).thenReturn(null);
            when(context.header("X-Real-IP")).thenReturn(null);
            when(context.ip()).thenReturn("192.168.1.100");

            String token1 = csrfService.generateCsrfToken(context);

            // Same direct IP should validate
            boolean isValid = csrfService.validateCsrfToken(context, token1);
            assertTrue(isValid);
        }
    }

    @Nested
    @DisplayName("Token Management Tests")
    class TokenManagementTests {

        @Test
        @DisplayName("Should track active token count")
        void shouldTrackActiveTokenCount() {
            int initialCount = csrfService.getActiveCsrfTokenCount();

            String token1 = csrfService.generateCsrfToken(context);
            assertEquals(initialCount + 1, csrfService.getActiveCsrfTokenCount());

            String token2 = csrfService.generateCsrfToken(context);
            assertEquals(initialCount + 2, csrfService.getActiveCsrfTokenCount());

            // Validate one token (should be consumed)
            csrfService.validateCsrfToken(context, token1);
            assertEquals(initialCount + 1, csrfService.getActiveCsrfTokenCount());
        }
    }
}