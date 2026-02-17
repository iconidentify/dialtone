/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.services;

import com.dialtone.db.DatabaseManager;
import com.dialtone.db.SchemaInitializer;
import com.dialtone.db.models.User;
import com.dialtone.web.services.OAuthBaseService.AuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused test to isolate the timestamp parsing error in XAuthService.
 * This will help us identify exactly where the "Error parsing time stamp" is occurring.
 */
public class XAuthServiceTimestampTest {

    @TempDir
    Path tempDir;

    private DatabaseManager databaseManager;
    private Properties config;

    @BeforeEach
    void setUp() throws Exception {
        // Create test database
        String dbPath = tempDir.resolve("test.db").toString();

        config = new Properties();
        config.setProperty("db.path", dbPath);
        config.setProperty("x.oauth.client.id", "test_client_id");
        config.setProperty("x.oauth.client.secret", "test_client_secret");
        config.setProperty("x.oauth.redirect.uri", "http://localhost:5200/callback");

        databaseManager = DatabaseManager.getInstance(dbPath);
        SchemaInitializer.initializeSchema(databaseManager);
    }

    @Test
    void testDirectDatabaseInsertWithTimestamp() throws SQLException {
        // Test direct SQL insert to isolate the timestamp issue
        String sql = "INSERT INTO users (x_user_id, x_username, x_display_name, created_at, is_active) " +
                    "VALUES (?, ?, ?, ?, ?) RETURNING id";

        try (Connection conn = databaseManager.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            LocalDateTime now = LocalDateTime.now();

            stmt.setString(1, "test_user_id_123");
            stmt.setString(2, "TestUser");
            stmt.setString(3, "Test Display Name");

            // Test the exact method being used in production
            stmt.setObject(4, now);
            stmt.setBoolean(5, true);

            var rs = stmt.executeQuery();
            assertTrue(rs.next(), "Should return generated ID");

            int generatedId = rs.getInt("id");
            assertTrue(generatedId > 0, "Generated ID should be positive");

            System.out.println("✅ Direct database insert with LocalDateTime succeeded. Generated ID: " + generatedId);
        }
    }

    @Test
    void testUserCreateNewMethod() {
        // Test the User.createNew method to see if it generates valid timestamps
        User newUser = User.createNew("test_id_456", "TestUser2", "Test Display 2");

        assertNotNull(newUser.createdAt(), "Created timestamp should not be null");
        assertNotNull(newUser.xUserId(), "User ID should not be null");
        assertTrue(newUser.isActive(), "User should be active by default");

        System.out.println("✅ User.createNew() generated valid timestamp: " + newUser.createdAt());
        System.out.println("   User ID: " + newUser.xUserId());
        System.out.println("   Username: " + newUser.xUsername());
    }

    @Test
    void testFindOrCreateUserFlow() throws Exception {
        // Create a minimal XAuthService to test the exact flow
        XAuthService authService = new XAuthService(config);

        // Create a mock XUserProfile (this is what comes from X API)
        var profileClass = Class.forName("com.dialtone.web.services.XAuthService$XUserProfile");
        var profile = profileClass.getDeclaredConstructor(String.class, String.class, String.class)
            .newInstance("test_x_id_789", "SiliconForested", "IconIdentify");

        // Use reflection to access the private findOrCreateUser method
        var findOrCreateMethod = XAuthService.class.getDeclaredMethod("findOrCreateUser", profileClass);
        findOrCreateMethod.setAccessible(true);

        try {
            User result = (User) findOrCreateMethod.invoke(authService, profile);
            assertNotNull(result, "Should create user successfully");
            assertEquals("SiliconForested", result.xUsername());
            System.out.println("✅ findOrCreateUser succeeded with timestamp: " + result.createdAt());

        } catch (Exception e) {
            System.err.println("❌ findOrCreateUser failed: " + e.getCause().getMessage());
            e.printStackTrace();

            // Re-throw so test fails and shows us the exact error
            throw new AssertionError("findOrCreateUser failed with timestamp error", e.getCause());
        }
    }

    @Test
    void testDifferentTimestampFormats() throws SQLException {
        // Test different ways of inserting timestamps to find what works
        String sql = "INSERT INTO users (x_user_id, x_username, x_display_name, created_at, is_active) " +
                    "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = databaseManager.getDataSource().getConnection()) {
            LocalDateTime now = LocalDateTime.now();

            // Test 1: setObject with LocalDateTime
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, "test1");
                stmt.setString(2, "Test1");
                stmt.setString(3, "Test 1");
                stmt.setObject(4, now);
                stmt.setBoolean(5, true);
                int rows = stmt.executeUpdate();
                assertEquals(1, rows);
                System.out.println("✅ setObject(LocalDateTime) works");
            }

            // Test 2: setTimestamp with Timestamp.valueOf
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, "test2");
                stmt.setString(2, "Test2");
                stmt.setString(3, "Test 2");
                stmt.setTimestamp(4, java.sql.Timestamp.valueOf(now));
                stmt.setBoolean(5, true);
                int rows = stmt.executeUpdate();
                assertEquals(1, rows);
                System.out.println("✅ setTimestamp(Timestamp.valueOf) works");
            }

            // Test 3: setString with formatted timestamp
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, "test3");
                stmt.setString(2, "Test3");
                stmt.setString(3, "Test 3");
                stmt.setString(4, now.toString());
                stmt.setBoolean(5, true);
                int rows = stmt.executeUpdate();
                assertEquals(1, rows);
                System.out.println("✅ setString(LocalDateTime.toString) works");
            }
        }
    }
}