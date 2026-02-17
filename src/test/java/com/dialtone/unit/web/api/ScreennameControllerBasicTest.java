/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.web.api;

import com.dialtone.db.models.User;
import com.dialtone.web.api.ScreennameController;
import com.dialtone.web.api.SharedErrorResponse;
import com.dialtone.web.security.CsrfProtectionService;
import com.dialtone.web.services.ScreennameService;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Basic unit tests for ScreennameController focusing on authentication
 * and request validation logic without complex service interactions.
 */
class ScreennameControllerBasicTest {

    private ScreennameController screennameController;

    private TestScreennameService screennameService;

    @Mock
    private CsrfProtectionService csrfService;

    @Mock
    private Context context;

    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        screennameService = new TestScreennameService();
        screennameController = new ScreennameController(screennameService, csrfService);

        when(context.status(anyInt())).thenReturn(context);
        when(context.json(any())).thenReturn(context);

        testUser = User.fromDatabase(
            1,
            User.PROVIDER_X,
            "12345",
            "testuser",
            "Test User",
            null, // discordUserId
            null, // discordUsername
            null, // discordDisplayName
            LocalDateTime.now(),
            true
        );
    }

    @Nested
    @DisplayName("Authentication Tests")
    class AuthenticationTests {

        @Test
        @DisplayName("Should reject unauthenticated requests for getScreennames")
        void shouldRejectUnauthenticatedGetScreennames() {
            // Setup
            when(context.attribute("user")).thenReturn(null);

            // Execute
            screennameController.getScreennames(context);

            // Verify
            verify(context).status(401);
            verify(context).json(any(SharedErrorResponse.class));
            screennameService.assertNoInteractions();
        }

        @Test
        @DisplayName("Should reject unauthenticated requests for createScreenname")
        void shouldRejectUnauthenticatedCreateScreenname() {
            // Setup
            when(context.attribute("user")).thenReturn(null);

            // Execute
            screennameController.createScreenname(context);

            // Verify
            verify(context).status(401);
            verify(context).json(any(SharedErrorResponse.class));
            screennameService.assertNoInteractions();
        }

        @Test
        @DisplayName("Should reject unauthenticated requests for updateScreenname")
        void shouldRejectUnauthenticatedUpdateScreenname() {
            // Setup
            when(context.attribute("user")).thenReturn(null);

            // Execute
            screennameController.updateScreenname(context);

            // Verify
            verify(context).status(401);
            verify(context).json(any(SharedErrorResponse.class));
            screennameService.assertNoInteractions();
        }

        @Test
        @DisplayName("Should reject unauthenticated requests for updatePassword")
        void shouldRejectUnauthenticatedUpdatePassword() {
            // Setup
            when(context.attribute("user")).thenReturn(null);

            // Execute
            screennameController.updatePassword(context);

            // Verify
            verify(context).status(401);
            verify(context).json(any(SharedErrorResponse.class));
            screennameService.assertNoInteractions();
        }

        @Test
        @DisplayName("Should reject unauthenticated requests for setPrimary")
        void shouldRejectUnauthenticatedSetPrimary() {
            // Setup
            when(context.attribute("user")).thenReturn(null);

            // Execute
            screennameController.setPrimary(context);

            // Verify
            verify(context).status(401);
            verify(context).json(any(SharedErrorResponse.class));
            screennameService.assertNoInteractions();
        }

        @Test
        @DisplayName("Should reject unauthenticated requests for deleteScreenname")
        void shouldRejectUnauthenticatedDeleteScreenname() {
            // Setup
            when(context.attribute("user")).thenReturn(null);

            // Execute
            screennameController.deleteScreenname(context);

            // Verify
            verify(context).status(401);
            verify(context).json(any(SharedErrorResponse.class));
            screennameService.assertNoInteractions();
        }
    }

    @Nested
    @DisplayName("CSRF Protection Tests")
    class CsrfProtectionTests {

        @Test
        @DisplayName("Should enforce CSRF protection for createScreenname")
        void shouldEnforceCsrfForCreateScreenname() throws Exception {
            // Setup
            when(context.attribute("user")).thenReturn(testUser);
            doThrow(new CsrfProtectionService.CsrfValidationException("Invalid CSRF token"))
                .when(csrfService).requireValidCsrfToken(context);

            // Execute
            screennameController.createScreenname(context);

            // Verify
            verify(csrfService).requireValidCsrfToken(context);
            verify(context).status(403);
            verify(context).json(any(SharedErrorResponse.class));
            screennameService.assertNoInteractions();
        }

        @Test
        @DisplayName("Should allow createScreenname with valid CSRF token")
        void shouldAllowCreateScreennameWithValidCsrf() throws Exception {
            // Setup
            when(context.attribute("user")).thenReturn(testUser);

            ScreennameController.CreateScreennameRequest request =
                new ScreennameController.CreateScreennameRequest();
            request.screenname = null; // This will trigger validation error before service call
            request.password = "pass";

            when(context.bodyAsClass(ScreennameController.CreateScreennameRequest.class))
                .thenReturn(request);

            // Execute
            screennameController.createScreenname(context);

            // Verify CSRF was checked first
            verify(csrfService).requireValidCsrfToken(context);

            // Should get validation error before service call
            verify(context).status(400);
            verify(context).json(any(SharedErrorResponse.class));
            screennameService.assertNoInteractions();
        }
    }

    @Nested
    @DisplayName("Request Validation Tests")
    class RequestValidationTests {

        @Test
        @DisplayName("Should reject createScreenname with null screenname")
        void shouldRejectCreateScreennameWithNullScreenname() throws Exception {
            // Setup
            when(context.attribute("user")).thenReturn(testUser);

            ScreennameController.CreateScreennameRequest request =
                new ScreennameController.CreateScreennameRequest();
            request.screenname = null;
            request.password = "pass123";

            when(context.bodyAsClass(ScreennameController.CreateScreennameRequest.class))
                .thenReturn(request);

            // Execute
            screennameController.createScreenname(context);

            // Verify
            verify(context).status(400);
            verify(context).json(any(SharedErrorResponse.class));
            screennameService.assertNoInteractions();
        }

        @Test
        @DisplayName("Should reject createScreenname with null password")
        void shouldRejectCreateScreennameWithNullPassword() throws Exception {
            // Setup
            when(context.attribute("user")).thenReturn(testUser);

            ScreennameController.CreateScreennameRequest request =
                new ScreennameController.CreateScreennameRequest();
            request.screenname = "TestName";
            request.password = null;

            when(context.bodyAsClass(ScreennameController.CreateScreennameRequest.class))
                .thenReturn(request);

            // Execute
            screennameController.createScreenname(context);

            // Verify
            verify(context).status(400);
            verify(context).json(any(SharedErrorResponse.class));
            screennameService.assertNoInteractions();
        }

        @Test
        @DisplayName("Should reject updateScreenname with null screenname")
        void shouldRejectUpdateScreennameWithNullScreenname() {
            // Setup
            when(context.attribute("user")).thenReturn(testUser);
            when(context.pathParam("id")).thenReturn("1");

            ScreennameController.UpdateScreennameRequest request =
                new ScreennameController.UpdateScreennameRequest();
            request.screenname = null;

            when(context.bodyAsClass(ScreennameController.UpdateScreennameRequest.class))
                .thenReturn(request);

            // Execute
            screennameController.updateScreenname(context);

            // Verify
            verify(context).status(400);
            verify(context).json(any(SharedErrorResponse.class));
            screennameService.assertNoInteractions();
        }

        @Test
        @DisplayName("Should reject updatePassword with null password")
        void shouldRejectUpdatePasswordWithNullPassword() {
            // Setup
            when(context.attribute("user")).thenReturn(testUser);
            when(context.pathParam("id")).thenReturn("1");

            ScreennameController.UpdatePasswordRequest request =
                new ScreennameController.UpdatePasswordRequest();
            request.password = null;

            when(context.bodyAsClass(ScreennameController.UpdatePasswordRequest.class))
                .thenReturn(request);

            // Execute
            screennameController.updatePassword(context);

            // Verify
            verify(context).status(400);
            verify(context).json(any(SharedErrorResponse.class));
            screennameService.assertNoInteractions();
        }

        @Test
        @DisplayName("Should reject requests with invalid ID format")
        void shouldRejectRequestsWithInvalidIdFormat() {
            // Setup
            when(context.attribute("user")).thenReturn(testUser);
            when(context.pathParam("id")).thenReturn("invalid");

            // Execute
            screennameController.updateScreenname(context);

            // Verify
            verify(context).status(400);
            verify(context).json(any(SharedErrorResponse.class));
            screennameService.assertNoInteractions();
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle generic exceptions gracefully in getScreennames")
        void shouldHandleGenericExceptionsInGetScreennames() throws Exception {
            // Setup
            when(context.attribute("user")).thenReturn(testUser);
            screennameService.setGetScreennamesException(
                new RuntimeException("Database connection error"));

            // Execute
            screennameController.getScreennames(context);

            // Verify
            verify(context).status(500);
            verify(context).json(any(SharedErrorResponse.class));
        }
    }

    /**
     * Simple test double for ScreennameService that tracks method usage
     * without requiring a real DatabaseManager.
     */
    private static class TestScreennameService extends ScreennameService {
        private final List<String> calls = new ArrayList<>();
        private RuntimeException getScreennamesException;

        TestScreennameService() {
            super(null, null, null);
        }

        void assertNoInteractions() {
            assertTrue(calls.isEmpty(), "Expected no service interactions but saw: " + calls);
        }

        void setGetScreennamesException(RuntimeException ex) {
            this.getScreennamesException = ex;
        }

        @Override
        public List<com.dialtone.db.models.Screenname> getScreennamesForUser(int userId) {
            calls.add("getScreennamesForUser");
            if (getScreennamesException != null) {
                throw getScreennamesException;
            }
            return List.of();
        }

        @Override
        public com.dialtone.db.models.Screenname createScreenname(int userId, String screenname, String password) {
            return unexpected("createScreenname");
        }

        @Override
        public com.dialtone.db.models.Screenname updateScreenname(int screennameId, int userId, String newScreenname) {
            return unexpected("updateScreenname");
        }

        @Override
        public com.dialtone.db.models.Screenname updatePassword(int screennameId, int userId, String newPassword) {
            return unexpected("updatePassword");
        }

        @Override
        public com.dialtone.db.models.Screenname setPrimary(int screennameId, int userId) {
            return unexpected("setPrimary");
        }

        @Override
        public void deleteScreenname(int screennameId, int userId) {
            unexpectedVoid("deleteScreenname");
        }

        private com.dialtone.db.models.Screenname unexpected(String method) {
            calls.add(method);
            throw new AssertionError("Service method should not be called during this test: " + method);
        }

        private void unexpectedVoid(String method) {
            calls.add(method);
            throw new AssertionError("Service method should not be called during this test: " + method);
        }
    }
}