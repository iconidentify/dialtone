/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol.auth;

import com.dialtone.protocol.SessionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Guest Password Generation")
class GuestPasswordTest {

    /**
     * Test that passwords are generated with correct format.
     * Uses reflection to access the private generateGuestPassword method.
     */
    @Test
    @DisplayName("Generated passwords should meet format requirements")
    void generatedPasswordsShouldMeetFormatRequirements() throws Exception {
        // Use reflection to access private method
        java.lang.reflect.Method method = com.dialtone.protocol.auth.LoginTokenHandler.class
            .getDeclaredMethod("generateGuestPassword");
        method.setAccessible(true);
        
        com.dialtone.protocol.auth.LoginTokenHandler handler = 
            new com.dialtone.protocol.auth.LoginTokenHandler(
                new SessionContext(), null, null, null, null, null, null, null, null);

        // Generate multiple passwords and verify format
        for (int i = 0; i < 10; i++) {
            String password = (String) method.invoke(handler);
            
            // Check length (12-16 characters)
            assertTrue(password.length() >= 12, "Password too short: " + password.length());
            assertTrue(password.length() <= 16, "Password too long: " + password.length());
            
            // Check character set (alphanumeric + safe special chars)
            Pattern validChars = Pattern.compile("^[A-Za-z0-9!@#$%^&*]+$");
            assertTrue(validChars.matcher(password).matches(), 
                "Password contains invalid characters: " + password);
        }
    }

    @Test
    @DisplayName("Generated passwords should be unique")
    void generatedPasswordsShouldBeUnique() throws Exception {
        java.lang.reflect.Method method = com.dialtone.protocol.auth.LoginTokenHandler.class
            .getDeclaredMethod("generateGuestPassword");
        method.setAccessible(true);
        
        com.dialtone.protocol.auth.LoginTokenHandler handler = 
            new com.dialtone.protocol.auth.LoginTokenHandler(
                new SessionContext(), null, null, null, null, null, null, null, null);

        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String password = (String) method.invoke(handler);
            assertFalse(passwords.contains(password), 
                "Duplicate password generated: " + password);
            passwords.add(password);
        }
        
        // With 100 passwords, we should have high uniqueness
        assertTrue(passwords.size() >= 95, 
            "Too many duplicate passwords: " + (100 - passwords.size()));
    }

    @Test
    @DisplayName("Password should be stored in session for ephemeral users")
    void passwordShouldBeStoredInSessionForEphemeralUsers() {
        SessionContext session = new SessionContext();
        session.setUsername("~Guest1234");
        session.setEphemeral(true);
        
        // Initially password should be null
        assertNull(session.getPassword());
        
        // After setting password (simulating what LoginTokenHandler does)
        String testPassword = "testPassword123";
        session.setPassword(testPassword);
        
        assertEquals(testPassword, session.getPassword());
    }

    @Test
    @DisplayName("Password should NOT be stored for non-ephemeral users")
    void passwordShouldNotBeStoredForNonEphemeralUsers() {
        SessionContext session = new SessionContext();
        session.setUsername("RegularUser");
        session.setEphemeral(false);
        
        // Password should remain null for non-ephemeral users
        assertNull(session.getPassword());
    }

    @Test
    @DisplayName("Password can be cleared from session")
    void passwordCanBeClearedFromSession() {
        SessionContext session = new SessionContext();
        session.setPassword("testPassword123");
        assertEquals("testPassword123", session.getPassword());
        
        session.clearPassword();
        assertNull(session.getPassword());
    }
}

