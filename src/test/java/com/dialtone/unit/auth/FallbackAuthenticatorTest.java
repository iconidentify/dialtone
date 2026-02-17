/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.auth;

import com.dialtone.auth.AuthResult;
import com.dialtone.auth.DatabaseUserAuthenticator;
import com.dialtone.auth.EphemeralUserManager;
import com.dialtone.auth.FallbackAuthenticator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FallbackAuthenticator.
 * Tests the authentication flow with ephemeral fallback.
 */
@DisplayName("FallbackAuthenticator")
class FallbackAuthenticatorTest {

    private DatabaseUserAuthenticator mockDbAuth;
    private EphemeralUserManager ephemeralManager;
    private FallbackAuthenticator authWithFallback;
    private FallbackAuthenticator authWithoutFallback;

    @BeforeEach
    void setUp() {
        mockDbAuth = mock(DatabaseUserAuthenticator.class);
        ephemeralManager = new EphemeralUserManager();
        authWithFallback = new FallbackAuthenticator(mockDbAuth, ephemeralManager, true);
        authWithoutFallback = new FallbackAuthenticator(mockDbAuth, ephemeralManager, false);
    }

    @Nested
    @DisplayName("authenticateWithResult() - Fallback Enabled")
    class AuthenticateWithFallbackEnabled {

        @Test
        @DisplayName("should return success for valid DB credentials")
        void shouldReturnSuccessForValidCredentials() {
            when(mockDbAuth.authenticate("testuser", "password123")).thenReturn(true);

            AuthResult result = authWithFallback.authenticateWithResult("testuser", "password123");

            assertTrue(result.isSuccess());
            assertEquals("testuser", result.getScreenname());
            assertFalse(result.isEphemeral());
            assertNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("should create ephemeral session for invalid DB credentials")
        void shouldCreateEphemeralForInvalidCredentials() {
            when(mockDbAuth.authenticate("testuser", "wrongpass")).thenReturn(false);

            AuthResult result = authWithFallback.authenticateWithResult("testuser", "wrongpass");

            assertTrue(result.isSuccess());
            assertTrue(result.isEphemeral());
            assertTrue(result.getScreenname().startsWith(EphemeralUserManager.GUEST_PREFIX));
            assertNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("should create ephemeral session for empty username")
        void shouldCreateEphemeralForEmptyUsername() {
            AuthResult result = authWithFallback.authenticateWithResult("", "password");

            assertTrue(result.isSuccess());
            assertTrue(result.isEphemeral());
            assertTrue(result.getScreenname().startsWith(EphemeralUserManager.GUEST_PREFIX));
        }

        @Test
        @DisplayName("should create ephemeral session for null password")
        void shouldCreateEphemeralForNullPassword() {
            AuthResult result = authWithFallback.authenticateWithResult("testuser", null);

            assertTrue(result.isSuccess());
            assertTrue(result.isEphemeral());
            assertTrue(result.getScreenname().startsWith(EphemeralUserManager.GUEST_PREFIX));
        }

        @Test
        @DisplayName("should track ephemeral sessions in manager")
        void shouldTrackEphemeralSessions() {
            when(mockDbAuth.authenticate(anyString(), anyString())).thenReturn(false);
            assertEquals(0, ephemeralManager.getActiveGuestCount());

            authWithFallback.authenticateWithResult("user1", "pass");
            assertEquals(1, ephemeralManager.getActiveGuestCount());

            authWithFallback.authenticateWithResult("user2", "pass");
            assertEquals(2, ephemeralManager.getActiveGuestCount());
        }
    }

    @Nested
    @DisplayName("authenticateWithResult() - Fallback Disabled")
    class AuthenticateWithFallbackDisabled {

        @Test
        @DisplayName("should return success for valid DB credentials")
        void shouldReturnSuccessForValidCredentials() {
            when(mockDbAuth.authenticate("testuser", "password123")).thenReturn(true);

            AuthResult result = authWithoutFallback.authenticateWithResult("testuser", "password123");

            assertTrue(result.isSuccess());
            assertEquals("testuser", result.getScreenname());
            assertFalse(result.isEphemeral());
        }

        @Test
        @DisplayName("should return failure for invalid DB credentials")
        void shouldReturnFailureForInvalidCredentials() {
            when(mockDbAuth.authenticate("testuser", "wrongpass")).thenReturn(false);

            AuthResult result = authWithoutFallback.authenticateWithResult("testuser", "wrongpass");

            assertFalse(result.isSuccess());
            assertNull(result.getScreenname());
            assertFalse(result.isEphemeral());
            assertEquals("Invalid screenname or password", result.getErrorMessage());
        }

        @Test
        @DisplayName("should return failure for empty username")
        void shouldReturnFailureForEmptyUsername() {
            AuthResult result = authWithoutFallback.authenticateWithResult("", "password");

            assertFalse(result.isSuccess());
            assertEquals("Username is required", result.getErrorMessage());
        }

        @Test
        @DisplayName("should return failure for null password")
        void shouldReturnFailureForNullPassword() {
            AuthResult result = authWithoutFallback.authenticateWithResult("testuser", null);

            assertFalse(result.isSuccess());
            assertEquals("Password is required", result.getErrorMessage());
        }

        @Test
        @DisplayName("should not create ephemeral sessions")
        void shouldNotCreateEphemeralSessions() {
            when(mockDbAuth.authenticate(anyString(), anyString())).thenReturn(false);

            authWithoutFallback.authenticateWithResult("user1", "pass");
            authWithoutFallback.authenticateWithResult("user2", "pass");

            assertEquals(0, ephemeralManager.getActiveGuestCount());
        }
    }

    @Nested
    @DisplayName("authenticate() - Legacy Interface")
    class AuthenticateLegacy {

        @Test
        @DisplayName("should return true for valid credentials")
        void shouldReturnTrueForValidCredentials() {
            when(mockDbAuth.authenticate("testuser", "password")).thenReturn(true);

            assertTrue(authWithFallback.authenticate("testuser", "password"));
            assertTrue(authWithoutFallback.authenticate("testuser", "password"));
        }

        @Test
        @DisplayName("should return true with fallback enabled (ephemeral created)")
        void shouldReturnTrueWithFallbackEnabled() {
            when(mockDbAuth.authenticate("testuser", "wrongpass")).thenReturn(false);

            assertTrue(authWithFallback.authenticate("testuser", "wrongpass"));
        }

        @Test
        @DisplayName("should return false with fallback disabled")
        void shouldReturnFalseWithFallbackDisabled() {
            when(mockDbAuth.authenticate("testuser", "wrongpass")).thenReturn(false);

            assertFalse(authWithoutFallback.authenticate("testuser", "wrongpass"));
        }
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("isEphemeralFallbackEnabled should reflect config")
        void isEphemeralFallbackEnabledShouldReflectConfig() {
            assertTrue(authWithFallback.isEphemeralFallbackEnabled());
            assertFalse(authWithoutFallback.isEphemeralFallbackEnabled());
        }

        @Test
        @DisplayName("getEphemeralManager should return manager")
        void getEphemeralManagerShouldReturnManager() {
            assertSame(ephemeralManager, authWithFallback.getEphemeralManager());
            assertSame(ephemeralManager, authWithoutFallback.getEphemeralManager());
        }
    }
}
