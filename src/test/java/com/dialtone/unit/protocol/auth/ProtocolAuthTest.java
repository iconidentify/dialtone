/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol.auth;

import com.dialtone.protocol.auth.LoginCredentials;
import com.dialtone.protocol.auth.UserAuthenticator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for protocol.auth classes.
 */
@DisplayName("Protocol Auth")
class ProtocolAuthTest {

    @Nested
    @DisplayName("LoginCredentials")
    class LoginCredentialsTests {

        @Test
        @DisplayName("should store username and password")
        void shouldStoreCredentials() {
            LoginCredentials creds = new LoginCredentials("testuser", "password");
            assertEquals("testuser", creds.username());
            assertEquals("password", creds.password());
        }

        @Test
        @DisplayName("should throw on null username")
        void shouldThrowOnNullUsername() {
            assertThrows(IllegalArgumentException.class,
                () -> new LoginCredentials(null, "password"));
        }

        @Test
        @DisplayName("should throw on null password")
        void shouldThrowOnNullPassword() {
            assertThrows(IllegalArgumentException.class,
                () -> new LoginCredentials("testuser", null));
        }

        @Test
        @DisplayName("should accept empty strings")
        void shouldAcceptEmptyStrings() {
            LoginCredentials creds = new LoginCredentials("", "");
            assertEquals("", creds.username());
            assertEquals("", creds.password());
        }
    }

    @Nested
    @DisplayName("UserAuthenticator interface")
    class UserAuthenticatorTests {

        // Create a simple test implementation
        private final UserAuthenticator authenticator = new UserAuthenticator() {
            @Override
            public boolean authenticate(String username, String password) {
                return "valid".equals(username) && "pass".equals(password);
            }
        };

        @Test
        @DisplayName("isValidUsername should accept valid usernames")
        void isValidUsernameShouldAcceptValid() {
            assertTrue(authenticator.isValidUsername("a"));
            assertTrue(authenticator.isValidUsername("testuser"));
            assertTrue(authenticator.isValidUsername("1234567890123456")); // 16 chars max
        }

        @Test
        @DisplayName("isValidUsername should reject null")
        void isValidUsernameShouldRejectNull() {
            assertFalse(authenticator.isValidUsername(null));
        }

        @Test
        @DisplayName("isValidUsername should reject empty")
        void isValidUsernameShouldRejectEmpty() {
            assertFalse(authenticator.isValidUsername(""));
        }

        @Test
        @DisplayName("isValidUsername should reject too long")
        void isValidUsernameShouldRejectTooLong() {
            assertFalse(authenticator.isValidUsername("12345678901234567")); // 17 chars
        }

        @Test
        @DisplayName("isValidPassword should accept valid passwords")
        void isValidPasswordShouldAcceptValid() {
            assertTrue(authenticator.isValidPassword("a"));
            assertTrue(authenticator.isValidPassword("12345678")); // 8 chars max
        }

        @Test
        @DisplayName("isValidPassword should reject null")
        void isValidPasswordShouldRejectNull() {
            assertFalse(authenticator.isValidPassword(null));
        }

        @Test
        @DisplayName("isValidPassword should reject empty")
        void isValidPasswordShouldRejectEmpty() {
            assertFalse(authenticator.isValidPassword(""));
        }

        @Test
        @DisplayName("isValidPassword should reject too long")
        void isValidPasswordShouldRejectTooLong() {
            assertFalse(authenticator.isValidPassword("123456789")); // 9 chars
        }

        @Test
        @DisplayName("authenticate should work with implementation")
        void authenticateShouldWork() {
            assertTrue(authenticator.authenticate("valid", "pass"));
            assertFalse(authenticator.authenticate("invalid", "pass"));
        }
    }
}
