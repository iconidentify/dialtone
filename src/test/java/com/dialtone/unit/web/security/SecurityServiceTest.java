/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.web.security;

import com.dialtone.web.security.SecurityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for SecurityService.
 * Tests input validation, sanitization, and security features.
 */
class SecurityServiceTest {

    @Nested
    @DisplayName("Screenname Validation Tests")
    class ScreennameValidationTests {

        @Test
        @DisplayName("Should accept valid screennames")
        void shouldAcceptValidScreennames() {
            // Valid screennames
            assertValidScreenname("User123", "User123");
            assertValidScreenname("AOL", "AOL");
            assertValidScreenname("a", "a");
            assertValidScreenname("1234567890", "1234567890");
            assertValidScreenname("TestUser", "TestUser");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"", "   ", "\t", "\n"})
        @DisplayName("Should reject null, empty, or whitespace-only screennames")
        void shouldRejectInvalidScreennames(String screenname) {
            SecurityService.ValidationResult result = SecurityService.validateScreenname(screenname);
            assertFalse(result.isValid());
            assertNotNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("Should reject screennames that are too long")
        void shouldRejectTooLongScreennames() {
            String longScreenname = "ThisIsTooLong"; // 13 characters
            SecurityService.ValidationResult result = SecurityService.validateScreenname(longScreenname);
            assertFalse(result.isValid());
            assertEquals("Screenname must be 10 characters or less", result.getErrorMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"user@aol", "test-name", "user_name", "user.name", "user name"})
        @DisplayName("Should reject screennames with invalid characters")
        void shouldRejectScreennamesWithInvalidCharacters(String screenname) {
            SecurityService.ValidationResult result = SecurityService.validateScreenname(screenname);
            assertFalse(result.isValid());
            assertEquals("Screenname can only contain letters and numbers", result.getErrorMessage());
        }

        @Test
        @DisplayName("Should trim whitespace from valid screennames")
        void shouldTrimWhitespaceFromValidScreennames() {
            SecurityService.ValidationResult result = SecurityService.validateScreenname("  TestUser  ");
            assertTrue(result.isValid());
            assertEquals("TestUser", result.getValue());
        }

        private void assertValidScreenname(String input, String expected) {
            SecurityService.ValidationResult result = SecurityService.validateScreenname(input);
            assertTrue(result.isValid(), "Expected '" + input + "' to be valid");
            assertEquals(expected, result.getValue());
            assertNull(result.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("Password Validation Tests")
    class PasswordValidationTests {

        @Test
        @DisplayName("Should accept valid passwords")
        void shouldAcceptValidPasswords() {
            assertValidPassword("password", "password");
            assertValidPassword("12345678", "12345678");
            assertValidPassword("a", "a");
            assertValidPassword("Test123!", "Test123!");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should reject null or empty passwords")
        void shouldRejectNullOrEmptyPasswords(String password) {
            SecurityService.ValidationResult result = SecurityService.validatePassword(password);
            assertFalse(result.isValid());
            assertNotNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("Should reject passwords that are too long")
        void shouldRejectTooLongPasswords() {
            String longPassword = "ThisPasswordIsTooLong"; // 20 characters
            SecurityService.ValidationResult result = SecurityService.validatePassword(longPassword);
            assertFalse(result.isValid());
            assertEquals("Password must be 8 characters or less", result.getErrorMessage());
        }

        @Test
        @DisplayName("Should reject passwords with null bytes")
        void shouldRejectPasswordsWithNullBytes() {
            String passwordWithNull = "te\0st"; // 5 chars, within limit
            SecurityService.ValidationResult result = SecurityService.validatePassword(passwordWithNull);
            assertFalse(result.isValid());
            assertEquals("Password contains invalid characters", result.getErrorMessage());
        }

        @Test
        @DisplayName("Should reject passwords with control characters")
        void shouldRejectPasswordsWithControlCharacters() {
            String passwordWithControl = "te\u0001st"; // 5 chars, within limit
            SecurityService.ValidationResult result = SecurityService.validatePassword(passwordWithControl);
            assertFalse(result.isValid());
            assertEquals("Password contains invalid characters", result.getErrorMessage());
        }

        private void assertValidPassword(String input, String expected) {
            SecurityService.ValidationResult result = SecurityService.validatePassword(input);
            assertTrue(result.isValid(), "Expected '" + input + "' to be valid");
            assertEquals(expected, result.getValue());
            assertNull(result.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("Text Sanitization Tests")
    class TextSanitizationTests {

        @Test
        @DisplayName("Should sanitize basic text correctly")
        void shouldSanitizeBasicText() {
            assertEquals("Hello World", SecurityService.sanitizeDisplayText("Hello World"));
            assertEquals("Test 123", SecurityService.sanitizeDisplayText("Test 123"));
        }

        @Test
        @DisplayName("Should remove XSS-prone characters")
        void shouldRemoveXssProneCharacters() {
            assertEquals("HelloscriptWorld", SecurityService.sanitizeDisplayText("Hello<script>World"));
            assertEquals("UserName", SecurityService.sanitizeDisplayText("User\"Name"));
            assertEquals("Test", SecurityService.sanitizeDisplayText("Test&"));
            assertEquals("Safe", SecurityService.sanitizeDisplayText("S<af>e"));
        }

        @Test
        @DisplayName("Should truncate long text")
        void shouldTruncateLongText() {
            String longText = "a".repeat(150);
            String sanitized = SecurityService.sanitizeDisplayText(longText);
            assertEquals(100, sanitized.length());
        }

        @Test
        @DisplayName("Should normalize whitespace")
        void shouldNormalizeWhitespace() {
            assertEquals("Hello World", SecurityService.sanitizeDisplayText("Hello   World"));
            assertEquals("TestUser", SecurityService.sanitizeDisplayText("Test\t\nUser"));
            assertEquals("Spaced Text", SecurityService.sanitizeDisplayText("  Spaced  Text  "));
        }

        @Test
        @DisplayName("Should handle null and empty input")
        void shouldHandleNullAndEmptyInput() {
            assertNull(SecurityService.sanitizeDisplayText(null));
            assertEquals("", SecurityService.sanitizeDisplayText(""));
            assertEquals("", SecurityService.sanitizeDisplayText("   "));
        }
    }

    @Nested
    @DisplayName("X Username Validation Tests")
    class XUsernameValidationTests {

        @Test
        @DisplayName("Should accept valid X usernames")
        void shouldAcceptValidXUsernames() {
            assertValidXUsername("testuser", "testuser");
            assertValidXUsername("user_123", "user_123");
            assertValidXUsername("ABC123", "ABC123");
            assertValidXUsername("a", "a");
        }

        @Test
        @DisplayName("Should remove @ symbol if present")
        void shouldRemoveAtSymbol() {
            SecurityService.ValidationResult result = SecurityService.validateXUsername("@testuser");
            assertTrue(result.isValid());
            assertEquals("testuser", result.getValue());
        }

        @Test
        @DisplayName("Should reject usernames that are too long")
        void shouldRejectTooLongXUsernames() {
            String longUsername = "thisusernameistoolong"; // 20 characters
            SecurityService.ValidationResult result = SecurityService.validateXUsername(longUsername);
            assertFalse(result.isValid());
            assertEquals("X username too long", result.getErrorMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"user-name", "user.name", "user name", "user@test"})
        @DisplayName("Should reject usernames with invalid characters")
        void shouldRejectXUsernamesWithInvalidCharacters(String username) {
            SecurityService.ValidationResult result = SecurityService.validateXUsername(username);
            assertFalse(result.isValid());
            assertEquals("X username contains invalid characters", result.getErrorMessage());
        }

        private void assertValidXUsername(String input, String expected) {
            SecurityService.ValidationResult result = SecurityService.validateXUsername(input);
            assertTrue(result.isValid(), "Expected '" + input + "' to be valid");
            assertEquals(expected, result.getValue());
            assertNull(result.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("SQL Safety Tests")
    class SqlSafetyTests {

        @Test
        @DisplayName("Should accept safe strings")
        void shouldAcceptSafeStrings() {
            assertTrue(SecurityService.isSqlSafe("normal text"));
            assertTrue(SecurityService.isSqlSafe("User123"));
            assertTrue(SecurityService.isSqlSafe("test@example.com"));
            assertTrue(SecurityService.isSqlSafe(null));
        }

        @ParameterizedTest
        @ValueSource(strings = {"SELECT * FROM users", "DROP TABLE users", "INSERT INTO", "UPDATE users",
                               "DELETE FROM", "UNION SELECT", "EXEC sp_", "javascript:alert"})
        @DisplayName("Should reject strings with SQL injection patterns")
        void shouldRejectSqlInjectionPatterns(String input) {
            assertFalse(SecurityService.isSqlSafe(input));
        }

        @ParameterizedTest
        @ValueSource(strings = {"-- comment", "/* comment */", "test -- comment"})
        @DisplayName("Should reject strings with SQL comment patterns")
        void shouldRejectSqlCommentPatterns(String input) {
            assertFalse(SecurityService.isSqlSafe(input));
        }
    }

    @Nested
    @DisplayName("Request Size Validation Tests")
    class RequestSizeValidationTests {

        @Test
        @DisplayName("Should accept valid request sizes")
        void shouldAcceptValidRequestSizes() {
            assertTrue(SecurityService.validateRequestSize(0, 1000));
            assertTrue(SecurityService.validateRequestSize(500, 1000));
            assertTrue(SecurityService.validateRequestSize(1000, 1000));
        }

        @Test
        @DisplayName("Should reject oversized requests")
        void shouldRejectOversizedRequests() {
            assertFalse(SecurityService.validateRequestSize(1001, 1000));
            assertFalse(SecurityService.validateRequestSize(Long.MAX_VALUE, 1000));
        }

        @Test
        @DisplayName("Should reject invalid content lengths")
        void shouldRejectInvalidContentLengths() {
            assertFalse(SecurityService.validateRequestSize(-1, 1000));
            assertFalse(SecurityService.validateRequestSize(-100, 1000));
        }
    }

    @Nested
    @DisplayName("ID Validation Tests")
    class IdValidationTests {

        @Test
        @DisplayName("Should accept valid IDs")
        void shouldAcceptValidIds() {
            assertValidId("123", "123");
            assertValidId("1", "1");
            assertValidId("999999", "999999");
            assertValidId(" 123 ", "123"); // Should trim
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"", "   "})
        @DisplayName("Should reject null or empty IDs")
        void shouldRejectNullOrEmptyIds(String id) {
            SecurityService.ValidationResult result = SecurityService.validateId(id, "test_field");
            assertFalse(result.isValid());
            assertTrue(result.getErrorMessage().contains("test_field"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "-999"})
        @DisplayName("Should reject non-positive IDs")
        void shouldRejectNonPositiveIds(String id) {
            SecurityService.ValidationResult result = SecurityService.validateId(id, "test_field");
            assertFalse(result.isValid());
            assertEquals("test_field must be a positive number", result.getErrorMessage());
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "12.5", "12a", "!@#"})
        @DisplayName("Should reject non-numeric IDs")
        void shouldRejectNonNumericIds(String id) {
            SecurityService.ValidationResult result = SecurityService.validateId(id, "test_field");
            assertFalse(result.isValid());
            assertEquals("test_field must be a valid number", result.getErrorMessage());
        }

        private void assertValidId(String input, String expected) {
            SecurityService.ValidationResult result = SecurityService.validateId(input, "test_field");
            assertTrue(result.isValid(), "Expected '" + input + "' to be valid");
            assertEquals(expected, result.getValue());
            assertNull(result.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("Secure Token Generation Tests")
    class SecureTokenGenerationTests {

        @Test
        @DisplayName("Should generate tokens of correct length")
        void shouldGenerateTokensOfCorrectLength() {
            String token32 = SecurityService.generateSecureToken(32);
            assertEquals(32, token32.length());

            String token16 = SecurityService.generateSecureToken(16);
            assertEquals(16, token16.length());
        }

        @Test
        @DisplayName("Should generate unique tokens")
        void shouldGenerateUniqueTokens() {
            String token1 = SecurityService.generateSecureToken(32);
            String token2 = SecurityService.generateSecureToken(32);
            assertNotEquals(token1, token2);
        }

        @Test
        @DisplayName("Should generate tokens with valid characters only")
        void shouldGenerateTokensWithValidCharacters() {
            String token = SecurityService.generateSecureToken(100);
            assertTrue(token.matches("^[a-zA-Z0-9]+$"),
                "Token should contain only alphanumeric characters");
        }

        @Test
        @DisplayName("Should handle edge cases")
        void shouldHandleEdgeCases() {
            String emptyToken = SecurityService.generateSecureToken(0);
            assertEquals("", emptyToken);

            String singleChar = SecurityService.generateSecureToken(1);
            assertEquals(1, singleChar.length());
        }
    }
}