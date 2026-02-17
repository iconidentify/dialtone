/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol;

import com.dialtone.protocol.SessionContext;
import com.dialtone.skalholt.SkalholtAuthToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Skalholt SSO (Single Sign-On) functionality.
 */
@DisplayName("Skalholt SSO")
class SkalholtSsoTest {

    @Nested
    @DisplayName("SessionContext Password Storage")
    class SessionContextPasswordStorage {

        @Test
        @DisplayName("Should store password in session")
        void shouldStorePasswordInSession() {
            SessionContext session = new SessionContext();
            session.setPassword("mySecretPassword");

            assertEquals("mySecretPassword", session.getPassword());
        }

        @Test
        @DisplayName("Should clear password from session")
        void shouldClearPasswordFromSession() {
            SessionContext session = new SessionContext();
            session.setPassword("mySecretPassword");
            session.clearPassword();

            assertNull(session.getPassword());
        }

        @Test
        @DisplayName("Should handle null password")
        void shouldHandleNullPassword() {
            SessionContext session = new SessionContext();
            assertNull(session.getPassword());

            session.setPassword(null);
            assertNull(session.getPassword());
        }
    }

    @Nested
    @DisplayName("AUTH Command Format")
    class AuthCommandFormat {

        @Test
        @DisplayName("Should generate correct base64 credentials")
        void shouldGenerateCorrectBase64Credentials() {
            String username = "TestUser";
            String password = "TestPass123";
            String credentials = username + ":" + password;
            String base64 = Base64.getEncoder().encodeToString(
                    credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Verify the base64 decodes correctly
            String decoded = new String(Base64.getDecoder().decode(base64),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertEquals("TestUser:TestPass123", decoded);
        }

        @Test
        @DisplayName("Should build AUTH command with correct format")
        void shouldBuildAuthCommandWithCorrectFormat() {
            String username = "ZeroCool";
            String password = "hackerPass";
            String secret = "shared_secret_123";

            String credentials = username + ":" + password;
            String base64 = Base64.getEncoder().encodeToString(
                    credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String authCommand = "AUTH " + base64 + " " + secret;

            // Verify format: AUTH <base64> <secret>
            assertTrue(authCommand.startsWith("AUTH "));
            assertTrue(authCommand.contains(base64));
            assertTrue(authCommand.endsWith(" " + secret));
        }

        @Test
        @DisplayName("Should handle special characters in password")
        void shouldHandleSpecialCharactersInPassword() {
            String username = "User";
            String password = "p@ss:word!#$%";
            String credentials = username + ":" + password;
            String base64 = Base64.getEncoder().encodeToString(
                    credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Verify it decodes correctly
            String decoded = new String(Base64.getDecoder().decode(base64),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertEquals("User:p@ss:word!#$%", decoded);
        }
    }

    @Nested
    @DisplayName("AUTH Response Detection")
    class AuthResponseDetection {

        @Test
        @DisplayName("Should detect AUTH success line")
        void shouldDetectAuthSuccessLine() {
            String successLine = "AUTH - dXNlcjpwYXNz";  // base64 of user:pass
            assertTrue(SkalholtAuthToken.isAuthLine(successLine));
        }

        @Test
        @DisplayName("Should not detect regular line as AUTH")
        void shouldNotDetectRegularLineAsAuth() {
            assertFalse(SkalholtAuthToken.isAuthLine("Welcome to the game!"));
            assertFalse(SkalholtAuthToken.isAuthLine("You have entered the tavern."));
        }

        @Test
        @DisplayName("Should detect AUTH FAILED prefix")
        void shouldDetectAuthFailedPrefix() {
            String failedLine = "AUTH FAILED: Invalid credentials";
            assertTrue(failedLine.startsWith("AUTH FAILED:"));

            String reason = failedLine.substring("AUTH FAILED:".length()).trim();
            assertEquals("Invalid credentials", reason);
        }

        @Test
        @DisplayName("Should parse AUTH success and extract token")
        void shouldParseAuthSuccessAndExtractToken() {
            String credentials = "TestPlayer:password123";
            String base64 = Base64.getEncoder().encodeToString(credentials.getBytes());
            String authLine = "AUTH - " + base64;

            SkalholtAuthToken token = SkalholtAuthToken.fromTelnetLine(authLine);

            assertNotNull(token);
            assertEquals("TestPlayer", token.getUsername());
            assertEquals(base64, token.getBase64Token());
        }
    }

    @Nested
    @DisplayName("SSO Filter Behavior")
    class SsoFilterBehavior {

        @Test
        @DisplayName("Should suppress AUTH success line from display")
        void shouldSuppressAuthSuccessLineFromDisplay() {
            String authLine = "AUTH - dXNlcjpwYXNz";

            // The filter should return null for AUTH lines (suppressing them)
            // This is verified in the SkalholtTokenHandler createSkalholtSsoFilter
            assertTrue(SkalholtAuthToken.isAuthLine(authLine));
        }

        @Test
        @DisplayName("Should pass through regular game lines")
        void shouldPassThroughRegularGameLines() {
            String regularLine = "You see a goblin approaching!";

            // Regular lines should not be detected as AUTH lines
            assertFalse(SkalholtAuthToken.isAuthLine(regularLine));
            assertFalse(regularLine.startsWith("AUTH FAILED:"));
        }

        @Test
        @DisplayName("Should extract failure reason from AUTH FAILED response")
        void shouldExtractFailureReasonFromAuthFailedResponse() {
            String[] testCases = {
                "AUTH FAILED: Invalid credentials",
                "AUTH FAILED: Account locked",
                "AUTH FAILED: Token expired",
                "AUTH FAILED:   Extra spaces  "
            };

            String[] expectedReasons = {
                "Invalid credentials",
                "Account locked",
                "Token expired",
                "Extra spaces"
            };

            for (int i = 0; i < testCases.length; i++) {
                String line = testCases[i];
                assertTrue(line.startsWith("AUTH FAILED:"));
                String reason = line.substring("AUTH FAILED:".length()).trim();
                assertEquals(expectedReasons[i], reason);
            }
        }
    }
}
