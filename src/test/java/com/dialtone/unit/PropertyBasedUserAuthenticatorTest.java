/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit;

import com.dialtone.auth.PropertyBasedUserAuthenticator;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class PropertyBasedUserAuthenticatorTest {

    @Test
    void shouldAuthenticateValidUser() {
        Properties props = new Properties();
        props.setProperty("auth.users", "Steve Case:password");

        PropertyBasedUserAuthenticator auth = new PropertyBasedUserAuthenticator(props);

        assertTrue(auth.authenticate("Steve Case", "password"));
    }

    @Test
    void shouldRejectInvalidPassword() {
        Properties props = new Properties();
        props.setProperty("auth.users", "Steve Case:password");

        PropertyBasedUserAuthenticator auth = new PropertyBasedUserAuthenticator(props);

        assertFalse(auth.authenticate("Steve Case", "wrongpassword"));
    }

    @Test
    void shouldRejectUnknownUser() {
        Properties props = new Properties();
        props.setProperty("auth.users", "Steve Case:password");

        PropertyBasedUserAuthenticator auth = new PropertyBasedUserAuthenticator(props);

        assertFalse(auth.authenticate("Unknown User", "password"));
    }

    @Test
    void shouldSupportMultipleUsers() {
        Properties props = new Properties();
        props.setProperty("auth.users", "Steve Case:password,John Doe:pass123,Jane Smith:secret");

        PropertyBasedUserAuthenticator auth = new PropertyBasedUserAuthenticator(props);

        assertTrue(auth.authenticate("Steve Case", "password"));
        assertTrue(auth.authenticate("John Doe", "pass123"));
        assertTrue(auth.authenticate("Jane Smith", "secret"));

        assertEquals(3, auth.getUserCount());
    }

    @Test
    void shouldBeCaseInsensitiveForUsername() {
        Properties props = new Properties();
        props.setProperty("auth.users", "Steve Case:password");

        PropertyBasedUserAuthenticator auth = new PropertyBasedUserAuthenticator(props);

        assertTrue(auth.authenticate("steve case", "password"));
        assertTrue(auth.authenticate("STEVE CASE", "password"));
        assertTrue(auth.authenticate("StEvE cAsE", "password"));
    }

    @Test
    void shouldBeCaseSensitiveForPassword() {
        Properties props = new Properties();
        props.setProperty("auth.users", "Steve Case:password");

        PropertyBasedUserAuthenticator auth = new PropertyBasedUserAuthenticator(props);

        assertFalse(auth.authenticate("Steve Case", "Password"));
        assertFalse(auth.authenticate("Steve Case", "PASSWORD"));
    }

    @Test
    void shouldHandleEmptyUsersProperty() {
        Properties props = new Properties();
        props.setProperty("auth.users", "");

        PropertyBasedUserAuthenticator auth = new PropertyBasedUserAuthenticator(props);

        assertEquals(0, auth.getUserCount());
        assertFalse(auth.authenticate("Steve Case", "password"));
    }

    @Test
    void shouldHandleMissingUsersProperty() {
        Properties props = new Properties();
        // No auth.users property set

        PropertyBasedUserAuthenticator auth = new PropertyBasedUserAuthenticator(props);

        assertEquals(0, auth.getUserCount());
        assertFalse(auth.authenticate("Steve Case", "password"));
    }

    @Test
    void shouldHandleNullCredentials() {
        Properties props = new Properties();
        props.setProperty("auth.users", "Steve Case:password");

        PropertyBasedUserAuthenticator auth = new PropertyBasedUserAuthenticator(props);

        assertFalse(auth.authenticate(null, "password"));
        assertFalse(auth.authenticate("Steve Case", null));
        assertFalse(auth.authenticate(null, null));
    }

    @Test
    void shouldSkipInvalidEntries() {
        Properties props = new Properties();
        props.setProperty("auth.users", "Steve Case:password,InvalidEntry,John Doe:pass123");

        PropertyBasedUserAuthenticator auth = new PropertyBasedUserAuthenticator(props);

        // Should have 2 valid users (skips InvalidEntry)
        assertEquals(2, auth.getUserCount());
        assertTrue(auth.authenticate("Steve Case", "password"));
        assertTrue(auth.authenticate("John Doe", "pass123"));
    }

    @Test
    void shouldTrimWhitespace() {
        Properties props = new Properties();
        props.setProperty("auth.users", " Steve Case : password , John Doe : pass123 ");

        PropertyBasedUserAuthenticator auth = new PropertyBasedUserAuthenticator(props);

        assertTrue(auth.authenticate("Steve Case", "password"));
        assertTrue(auth.authenticate("John Doe", "pass123"));
        assertEquals(2, auth.getUserCount());
    }

    @Test
    void shouldSkipEmptyUsernameOrPassword() {
        Properties props = new Properties();
        props.setProperty("auth.users", ":password,Steve Case:,Steve Case:validpass");

        PropertyBasedUserAuthenticator auth = new PropertyBasedUserAuthenticator(props);

        // Should only have 1 valid user
        assertEquals(1, auth.getUserCount());
        assertTrue(auth.authenticate("Steve Case", "validpass"));
    }

    @Test
    void shouldCheckIfUserExists() {
        Properties props = new Properties();
        props.setProperty("auth.users", "Steve Case:password,John Doe:pass123");

        PropertyBasedUserAuthenticator auth = new PropertyBasedUserAuthenticator(props);

        assertTrue(auth.hasUser("Steve Case"));
        assertTrue(auth.hasUser("steve case"));  // Case insensitive
        assertTrue(auth.hasUser("John Doe"));
        assertFalse(auth.hasUser("Unknown User"));
        assertFalse(auth.hasUser(null));
    }

    @Test
    void shouldHandlePasswordsWithColons() {
        Properties props = new Properties();
        props.setProperty("auth.users", "Steve Case:pass:word:123");

        PropertyBasedUserAuthenticator auth = new PropertyBasedUserAuthenticator(props);

        // Should parse as username="Steve Case", password="pass:word:123"
        assertTrue(auth.authenticate("Steve Case", "pass:word:123"));
        assertFalse(auth.authenticate("Steve Case", "pass"));
    }
}
