/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.integration;

import com.dialtone.db.DatabaseManager;
import com.dialtone.db.SchemaInitializer;
import com.dialtone.auth.DatabaseUserAuthenticator;
import com.dialtone.web.services.ScreennameService;
import com.dialtone.web.services.AdminSecurityService;
import com.dialtone.web.services.UserService;
import com.dialtone.db.models.User;
import com.dialtone.db.models.Screenname;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the web interface components.
 *
 * Tests the complete flow from database setup to authentication and screenname management.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebInterfaceIntegrationTest {

    private static DatabaseManager databaseManager;
    private static ScreennameService screennameService;
    private static DatabaseUserAuthenticator authenticator;
    private static AdminSecurityService adminSecurityService;
    private static UserService userService;
    private static Path tempDbPath;

    private static User testUser;
    private static final String TEST_X_USER_ID = "test_x_user_123";
    private static final String TEST_X_USERNAME = "testuser";
    private static final String TEST_X_DISPLAY_NAME = "Test User";

    @BeforeAll
    static void setUp() throws IOException, SQLException {
        // Create temporary database
        tempDbPath = Files.createTempFile("dialtone_test", ".db");

        // Initialize database
        databaseManager = DatabaseManager.getInstance(tempDbPath.toString());
        SchemaInitializer.initializeSchema(databaseManager);

        // Initialize services
        Properties testConfig = new Properties();
        testConfig.setProperty("admin.enabled", "false");  // Disable admin for test
        testConfig.setProperty("admin.x.usernames", "");

        userService = new UserService(databaseManager);
        adminSecurityService = new AdminSecurityService(testConfig, databaseManager);
        screennameService = new ScreennameService(databaseManager, adminSecurityService, userService);
        authenticator = new DatabaseUserAuthenticator(databaseManager);

        // Create test user
        testUser = User.createNew(TEST_X_USER_ID, TEST_X_USERNAME, TEST_X_DISPLAY_NAME);
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (tempDbPath != null) {
            Files.deleteIfExists(tempDbPath);
        }
    }

    @Test
    @Order(1)
    void testDatabaseSchemaInitialization() {
        // Test that schema initialization completed successfully
        assertTrue(SchemaInitializer.isSchemaInitialized(databaseManager),
            "Database schema should be properly initialized");

        assertEquals(1, SchemaInitializer.getSchemaVersion(databaseManager),
            "Schema version should be 1");
    }

    @Test
    @Order(2)
    void testUserCreation() throws Exception {
        // Insert test user directly into database for testing
        String sql = "INSERT INTO users (x_user_id, x_username, x_display_name, created_at, is_active) " +
                    "VALUES (?, ?, ?, ?, ?) RETURNING id";

        try (var conn = databaseManager.getDataSource().getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, testUser.xUserId());
            stmt.setString(2, testUser.xUsername());
            stmt.setString(3, testUser.xDisplayName());
            stmt.setObject(4, testUser.createdAt());
            stmt.setBoolean(5, testUser.isActive());

            var rs = stmt.executeQuery();
            assertTrue(rs.next(), "User creation should return an ID");

            int userId = rs.getInt("id");
            testUser = User.fromDatabase(
                userId, testUser.xUserId(), testUser.xUsername(),
                testUser.xDisplayName(), testUser.createdAt(), testUser.isActive()
            );
        }

        assertNotNull(testUser.id(), "Test user should have an ID after creation");
    }

    @Test
    @Order(3)
    void testCreateScreenname() throws Exception {
        // Test creating a screenname
        String screennameText = "TestUser1";
        String password = "testpass";

        Screenname createdScreenname = screennameService.createScreenname(
            testUser.id(), screennameText, password);

        assertNotNull(createdScreenname.id(), "Created screenname should have an ID");
        assertEquals(screennameText, createdScreenname.screenname(), "Screenname should match");
        assertEquals(testUser.id(), createdScreenname.userId(), "User ID should match");
        assertTrue(createdScreenname.isPrimary(), "First screenname should be primary");
        assertNotNull(createdScreenname.passwordHash(), "Password should be hashed");
        assertNotEquals(password, createdScreenname.passwordHash(), "Password should be hashed, not stored as plaintext");
    }

    @Test
    @Order(4)
    void testAuthenticationWithCreatedScreenname() {
        // Test that we can authenticate with the created screenname
        assertTrue(authenticator.authenticate("TestUser1", "testpass"),
            "Should authenticate with correct credentials");

        assertFalse(authenticator.authenticate("TestUser1", "wrongpass"),
            "Should not authenticate with wrong password");

        assertFalse(authenticator.authenticate("NonExistent", "testpass"),
            "Should not authenticate with non-existent screenname");

        // Test case-insensitive lookup
        assertTrue(authenticator.authenticate("testuser1", "testpass"),
            "Should authenticate with case-insensitive screenname");

        assertTrue(authenticator.authenticate("TESTUSER1", "testpass"),
            "Should authenticate with uppercase screenname");
    }

    @Test
    @Order(5)
    void testMultipleScreennameLimit() throws Exception {
        // Service allows up to 2 screennames per normal user
        Screenname secondScreenname = screennameService.createScreenname(testUser.id(), "TestUser2", "pass2");
        assertNotNull(secondScreenname.id(), "Should be able to create second screenname");
        assertFalse(secondScreenname.isPrimary(), "Second screenname should not be primary");

        // Now the 3rd should fail (limit is 2 for normal users)
        ScreennameService.ScreennameServiceException exception =
            assertThrows(ScreennameService.ScreennameServiceException.class, () ->
                screennameService.createScreenname(testUser.id(), "TestUser3", "pass3"));

        assertTrue(exception.getMessage().contains("maximum number of screennames allowed per account (2)"),
            "Should enforce 2 screenname limit per normal account");

        var screennames = screennameService.getScreennamesForUser(testUser.id());
        assertEquals(2, screennames.size(), "User should have exactly two screennames");

        // Verify only one is primary
        assertEquals(1, screennames.stream()
            .mapToInt(s -> s.isPrimary() ? 1 : 0)
            .sum(), "Only one screenname should be primary");
    }

    @Test
    @Order(6)
    void testGetUserScreennames() throws Exception {
        var screennames = screennameService.getScreennamesForUser(testUser.id());

        assertEquals(2, screennames.size(), "User should have two screennames");
        // Find the primary screenname (should be the first one created)
        assertTrue(screennames.stream().anyMatch(s -> s.isPrimary()), "One screenname should be primary");

        // Check that all belong to the test user
        assertTrue(screennames.stream()
            .allMatch(s -> s.userId().equals(testUser.id())),
            "All screennames should belong to the test user");
    }

    @Test
    @Order(7)
    void testSetPrimaryScreennameWithMultipleScreennames() throws Exception {
        var screennames = screennameService.getScreennamesForUser(testUser.id());
        assertEquals(2, screennames.size(), "Precondition: two screennames exist");

        // Find the current primary and a non-primary screenname
        Screenname currentPrimary = screennames.stream()
            .filter(Screenname::isPrimary)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Should have a primary screenname"));

        Screenname nonPrimary = screennames.stream()
            .filter(s -> !s.isPrimary())
            .findFirst()
            .orElseThrow(() -> new AssertionError("Should have a non-primary screenname"));

        // Set the non-primary as primary
        Screenname updated = screennameService.setPrimary(nonPrimary.id(), testUser.id());
        assertTrue(updated.isPrimary(), "Updated screenname should be primary");

        // Verify only one screenname is primary after the operation
        var updatedScreennames = screennameService.getScreennamesForUser(testUser.id());
        assertEquals(1, updatedScreennames.stream()
            .mapToInt(s -> s.isPrimary() ? 1 : 0)
            .sum(), "Only one screenname should be primary");

        // Verify the correct screenname is primary
        assertEquals(nonPrimary.id(),
            updatedScreennames.stream()
                .filter(Screenname::isPrimary)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Should have a primary"))
                .id(),
            "The non-primary screenname should now be the primary one");

        // Verify the old primary is no longer primary
        assertTrue(updatedScreennames.stream()
            .filter(s -> s.id().equals(currentPrimary.id()))
            .findFirst()
            .map(s -> !s.isPrimary())
            .orElse(false),
            "The old primary screenname should no longer be primary");
    }

    @Test
    @Order(8)
    void testUpdatePassword() throws Exception {
        var screennames = screennameService.getScreennamesForUser(testUser.id());
        Screenname screenname = screennames.get(0);

        String oldPasswordHash = screenname.passwordHash();
        String newPassword = "newpass";

        // Update password
        Screenname updatedScreenname = screennameService.updatePassword(
            screenname.id(), testUser.id(), newPassword);

        assertNotEquals(oldPasswordHash, updatedScreenname.passwordHash(),
            "Password hash should change after update");

        // Test authentication with new password
        assertTrue(authenticator.authenticate(screenname.screenname(), newPassword),
            "Should authenticate with new password");

        assertFalse(authenticator.authenticate(screenname.screenname(), "testpass"),
            "Should not authenticate with old password");
    }

    @Test
    @Order(9)
    void testScreennameValidation() throws Exception {
        // Test invalid screennames
        User emptyScreennameUser = createAdditionalUser("empty_screenname");
        assertThrows(ScreennameService.ScreennameServiceException.class, () -> {
            screennameService.createScreenname(emptyScreennameUser.id(), "", "password");
        }, "Should reject empty screenname");

        User longScreennameUser = createAdditionalUser("long_screenname");
        assertThrows(ScreennameService.ScreennameServiceException.class, () -> {
            screennameService.createScreenname(longScreennameUser.id(), "TooLongScreenname", "password");
        }, "Should reject screenname longer than 10 characters");

        User invalidCharsUser = createAdditionalUser("invalid_chars");
        assertThrows(ScreennameService.ScreennameServiceException.class, () -> {
            screennameService.createScreenname(invalidCharsUser.id(), "Invalid-Name", "password");
        }, "Should reject screenname with invalid characters");

        // Test invalid passwords
        User emptyPasswordUser = createAdditionalUser("empty_password");
        assertThrows(ScreennameService.ScreennameServiceException.class, () -> {
            screennameService.createScreenname(emptyPasswordUser.id(), "ValidName", "");
        }, "Should reject empty password");

        User longPasswordUser = createAdditionalUser("long_password");
        assertThrows(ScreennameService.ScreennameServiceException.class, () -> {
            screennameService.createScreenname(longPasswordUser.id(), "ValidName", "TooLongPassword");
        }, "Should reject password longer than 8 characters");
    }

    @Test
    @Order(10)
    void testAuthenticatorDiagnostics() {
        // Test diagnostic methods
        assertTrue(authenticator.hasScreenname("TestUser1"),
            "Should find existing screenname");

        assertFalse(authenticator.hasScreenname("NonExistent"),
            "Should not find non-existent screenname");

        assertEquals(2, authenticator.getScreennameCount(),
            "Should report correct number of active screennames");

        var info = authenticator.getScreennameInfo("TestUser1");
        assertNotNull(info, "Should return info for existing screenname");
        assertEquals("TestUser1", info.screenname());
        assertEquals(testUser.id().intValue(), info.userId());
        assertEquals(TEST_X_USERNAME, info.xUsername());
    }
    private static User createAdditionalUser(String suffix) throws Exception {
        String xUserId = TEST_X_USER_ID + "_" + suffix;
        String xUsername = TEST_X_USERNAME + "_" + suffix;
        String displayName = TEST_X_DISPLAY_NAME + " " + suffix;

        User newUser = User.createNew(xUserId, xUsername, displayName);

        String sql = "INSERT INTO users (x_user_id, x_username, x_display_name, created_at, is_active) " +
                     "VALUES (?, ?, ?, ?, ?) RETURNING id";

        try (var conn = databaseManager.getDataSource().getConnection();
             var stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newUser.xUserId());
            stmt.setString(2, newUser.xUsername());
            stmt.setString(3, newUser.xDisplayName());
            stmt.setObject(4, newUser.createdAt());
            stmt.setBoolean(5, newUser.isActive());

            var rs = stmt.executeQuery();
            assertTrue(rs.next(), "Additional user creation should return an ID");
            int userId = rs.getInt("id");

            return User.fromDatabase(
                userId,
                newUser.xUserId(),
                newUser.xUsername(),
                newUser.xDisplayName(),
                newUser.createdAt(),
                newUser.isActive()
            );
        }
    }
}