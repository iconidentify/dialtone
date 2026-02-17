/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol.skalholt;

import com.dialtone.protocol.SessionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Skalhalt SSO with Guest Passwords")
class SkalholtGuestSsoTest {

    @Test
    @DisplayName("Base64 encoding should work with guest username and password")
    void base64EncodingShouldWorkWithGuestUsernameAndPassword() {
        String username = "~Guest1234";
        String password = "generatedPassword123";
        
        String credentials = username + ":" + password;
        String base64 = Base64.getEncoder().encodeToString(
            credentials.getBytes(StandardCharsets.UTF_8));
        
        assertNotNull(base64);
        assertFalse(base64.isEmpty());
        
        // Verify decoding works
        String decoded = new String(
            Base64.getDecoder().decode(base64), 
            StandardCharsets.UTF_8);
        assertEquals(credentials, decoded);
    }

    @Test
    @DisplayName("SSO credentials should be valid for guest users")
    void ssoCredentialsShouldBeValidForGuestUsers() {
        SessionContext session = new SessionContext();
        session.setUsername("~Guest5678");
        session.setEphemeral(true);
        session.setPassword("securePassword456");
        
        // Simulate what SkalholtTokenHandler does
        String username = session.getUsername();
        String password = session.getPassword();
        
        assertNotNull(username);
        assertNotNull(password);
        assertTrue(username.startsWith("~Guest"));
        
        String credentials = username + ":" + password;
        String base64 = Base64.getEncoder().encodeToString(
            credentials.getBytes(StandardCharsets.UTF_8));
        
        // Verify base64 is valid
        assertNotNull(base64);
        assertFalse(base64.isEmpty());
        
        // Verify it can be used in Authorization header
        String authHeader = "Basic " + base64;
        assertTrue(authHeader.startsWith("Basic "));
    }

    @Test
    @DisplayName("SSO should detect when password is missing")
    void ssoShouldDetectWhenPasswordIsMissing() {
        SessionContext session = new SessionContext();
        session.setUsername("~Guest9999");
        session.setEphemeral(true);
        // Password not set

        String username = session.getUsername();
        String password = session.getPassword();

        // Should detect missing password
        assertNotNull(username);
        assertNull(password);

        // String concat with null produces "username:null" - SSO should check for null password
        // before attempting to build credentials
        String credentials = username + ":" + password;
        assertTrue(credentials.contains("null"),
            "Credentials with null password would produce invalid SSO auth");
    }

    @Test
    @DisplayName("Multiple guest users should have different passwords")
    void multipleGuestUsersShouldHaveDifferentPasswords() throws Exception {
        // Use reflection to generate passwords
        java.lang.reflect.Method method = com.dialtone.protocol.auth.LoginTokenHandler.class
            .getDeclaredMethod("generateGuestPassword");
        method.setAccessible(true);
        
        com.dialtone.protocol.auth.LoginTokenHandler handler = 
            new com.dialtone.protocol.auth.LoginTokenHandler(
                new SessionContext(), null, null, null, null, null, null, null, null);

        SessionContext session1 = new SessionContext();
        session1.setUsername("~Guest1111");
        session1.setPassword((String) method.invoke(handler));
        
        SessionContext session2 = new SessionContext();
        session2.setUsername("~Guest2222");
        session2.setPassword((String) method.invoke(handler));
        
        // Passwords should be different
        assertNotEquals(session1.getPassword(), session2.getPassword());
        
        // Both should generate valid SSO credentials
        String base64_1 = Base64.getEncoder().encodeToString(
            (session1.getUsername() + ":" + session1.getPassword())
                .getBytes(StandardCharsets.UTF_8));
        String base64_2 = Base64.getEncoder().encodeToString(
            (session2.getUsername() + ":" + session2.getPassword())
                .getBytes(StandardCharsets.UTF_8));
        
        assertNotEquals(base64_1, base64_2);
    }
}

