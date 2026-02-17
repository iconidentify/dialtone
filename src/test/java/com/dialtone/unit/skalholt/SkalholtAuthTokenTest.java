/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.skalholt;

import com.dialtone.skalholt.SkalholtAuthToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkalholtAuthToken")
class SkalholtAuthTokenTest {

    @Nested
    @DisplayName("Constructor")
    class Constructor {

        @Test
        @DisplayName("Should create token from valid base64")
        void shouldCreateFromValidBase64() {
            String credentials = "ZeroCool:password123";
            String base64 = Base64.getEncoder().encodeToString(credentials.getBytes());

            SkalholtAuthToken token = new SkalholtAuthToken(base64);

            assertEquals(base64, token.getBase64Token());
            assertEquals("ZeroCool", token.getUsername());
            assertTrue(token.getCapturedAtMs() > 0);
        }

        @Test
        @DisplayName("Should extract username correctly")
        void shouldExtractUsername() {
            String credentials = "TestUser:securepass";
            String base64 = Base64.getEncoder().encodeToString(credentials.getBytes());

            SkalholtAuthToken token = new SkalholtAuthToken(base64);

            assertEquals("TestUser", token.getUsername());
        }

        @Test
        @DisplayName("Should reject null token")
        void shouldRejectNullToken() {
            assertThrows(IllegalArgumentException.class, () -> new SkalholtAuthToken(null));
        }

        @Test
        @DisplayName("Should reject empty token")
        void shouldRejectEmptyToken() {
            assertThrows(IllegalArgumentException.class, () -> new SkalholtAuthToken(""));
        }

        @Test
        @DisplayName("Should reject blank token")
        void shouldRejectBlankToken() {
            assertThrows(IllegalArgumentException.class, () -> new SkalholtAuthToken("   "));
        }

        @Test
        @DisplayName("Should reject invalid base64")
        void shouldRejectInvalidBase64() {
            assertThrows(IllegalArgumentException.class, () -> new SkalholtAuthToken("not-valid-base64!!!"));
        }

        @Test
        @DisplayName("Should trim whitespace from token")
        void shouldTrimWhitespace() {
            String credentials = "User:pass";
            String base64 = Base64.getEncoder().encodeToString(credentials.getBytes());

            SkalholtAuthToken token = new SkalholtAuthToken("  " + base64 + "  ");

            assertEquals(base64, token.getBase64Token());
        }
    }

    @Nested
    @DisplayName("fromTelnetLine")
    class FromTelnetLine {

        @Test
        @DisplayName("Should parse valid AUTH line")
        void shouldParseValidAuthLine() {
            String credentials = "Player1:gamepass";
            String base64 = Base64.getEncoder().encodeToString(credentials.getBytes());
            String line = "AUTH - " + base64;

            SkalholtAuthToken token = SkalholtAuthToken.fromTelnetLine(line);

            assertNotNull(token);
            assertEquals("Player1", token.getUsername());
        }

        @Test
        @DisplayName("Should return null for null line")
        void shouldReturnNullForNullLine() {
            assertNull(SkalholtAuthToken.fromTelnetLine(null));
        }

        @Test
        @DisplayName("Should return null for non-AUTH line")
        void shouldReturnNullForNonAuthLine() {
            assertNull(SkalholtAuthToken.fromTelnetLine("Welcome to the game!"));
        }

        @Test
        @DisplayName("Should return null for empty AUTH value")
        void shouldReturnNullForEmptyAuthValue() {
            assertNull(SkalholtAuthToken.fromTelnetLine("AUTH - "));
        }

        @Test
        @DisplayName("Should return null for invalid base64 in AUTH line")
        void shouldReturnNullForInvalidBase64() {
            assertNull(SkalholtAuthToken.fromTelnetLine("AUTH - not-valid!!!"));
        }
    }

    @Nested
    @DisplayName("isAuthLine")
    class IsAuthLine {

        @Test
        @DisplayName("Should return true for AUTH line")
        void shouldReturnTrueForAuthLine() {
            assertTrue(SkalholtAuthToken.isAuthLine("AUTH - sometoken"));
        }

        @Test
        @DisplayName("Should return false for null")
        void shouldReturnFalseForNull() {
            assertFalse(SkalholtAuthToken.isAuthLine(null));
        }

        @Test
        @DisplayName("Should return false for non-AUTH line")
        void shouldReturnFalseForNonAuthLine() {
            assertFalse(SkalholtAuthToken.isAuthLine("You have entered the tavern."));
        }

        @Test
        @DisplayName("Should return false for similar but different prefix")
        void shouldReturnFalseForSimilarPrefix() {
            assertFalse(SkalholtAuthToken.isAuthLine("AUTHENTICATE - token"));
        }
    }

    @Nested
    @DisplayName("getBasicAuthHeader")
    class GetBasicAuthHeader {

        @Test
        @DisplayName("Should return Basic auth header format")
        void shouldReturnBasicAuthHeader() {
            String credentials = "user:pass";
            String base64 = Base64.getEncoder().encodeToString(credentials.getBytes());

            SkalholtAuthToken token = new SkalholtAuthToken(base64);

            assertEquals("Basic " + base64, token.getBasicAuthHeader());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("Equal tokens should be equal")
        void equalTokensShouldBeEqual() {
            String base64 = Base64.getEncoder().encodeToString("user:pass".getBytes());

            SkalholtAuthToken token1 = new SkalholtAuthToken(base64);
            SkalholtAuthToken token2 = new SkalholtAuthToken(base64);

            assertEquals(token1, token2);
            assertEquals(token1.hashCode(), token2.hashCode());
        }

        @Test
        @DisplayName("Different tokens should not be equal")
        void differentTokensShouldNotBeEqual() {
            String base64a = Base64.getEncoder().encodeToString("user1:pass".getBytes());
            String base64b = Base64.getEncoder().encodeToString("user2:pass".getBytes());

            SkalholtAuthToken token1 = new SkalholtAuthToken(base64a);
            SkalholtAuthToken token2 = new SkalholtAuthToken(base64b);

            assertNotEquals(token1, token2);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("Should include username but not password")
        void shouldIncludeUsernameButNotPassword() {
            String credentials = "SecretUser:SecretPass";
            String base64 = Base64.getEncoder().encodeToString(credentials.getBytes());

            SkalholtAuthToken token = new SkalholtAuthToken(base64);
            String str = token.toString();

            assertTrue(str.contains("SecretUser"));
            assertFalse(str.contains("SecretPass"));
            assertFalse(str.contains(base64));
        }
    }
}
