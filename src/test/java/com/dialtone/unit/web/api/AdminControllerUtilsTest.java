/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.web.api;

import com.dialtone.db.models.User;
import com.dialtone.web.api.AdminControllerUtils;
import com.dialtone.web.api.SharedErrorResponse;
import com.dialtone.web.security.CsrfProtectionService;
import com.dialtone.web.services.AdminAuditService;
import com.dialtone.web.services.AdminSecurityService;
import io.javalin.http.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AdminControllerUtils.
 *
 * <p>Tests the shared utility methods for admin controller operations:
 * <ul>
 *   <li>Admin authentication and authorization</li>
 *   <li>Client IP/User-Agent extraction</li>
 *   <li>Rate limiting checks</li>
 *   <li>CSRF validation</li>
 *   <li>Path parameter parsing</li>
 * </ul>
 */
@DisplayName("Admin Controller Utils Tests")
class AdminControllerUtilsTest {

    @Mock
    private Context ctx;

    @Mock
    private AdminSecurityService adminSecurityService;

    @Mock
    private CsrfProtectionService csrfService;

    @Mock
    private AdminAuditService adminAuditService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // Helper to create test users
    private User createTestUser(int id, String username, boolean isActive) {
        return User.fromDatabase(id, username + "_id", username, username, LocalDateTime.now(), isActive);
    }

    @Nested
    @DisplayName("getAdminUser Tests")
    class GetAdminUserTests {

        @Test
        @DisplayName("Should return admin user when authenticated and authorized")
        void shouldReturnAdminWhenValid() {
            User adminUser = createTestUser(1, "admin", true);
            when(ctx.attribute("user")).thenReturn(adminUser);
            when(adminSecurityService.isAdmin(adminUser)).thenReturn(true);

            Optional<User> result = AdminControllerUtils.getAdminUser(ctx, adminSecurityService);

            assertTrue(result.isPresent());
            assertEquals(adminUser, result.get());
            verify(ctx, never()).status(anyInt());
        }

        @Test
        @DisplayName("Should return empty and 401 when user not authenticated")
        void shouldReturn401WhenNotAuthenticated() {
            when(ctx.attribute("user")).thenReturn(null);
            when(ctx.status(401)).thenReturn(ctx);

            Optional<User> result = AdminControllerUtils.getAdminUser(ctx, adminSecurityService);

            assertTrue(result.isEmpty());
            verify(ctx).status(401);
            verify(ctx).json(any(SharedErrorResponse.class));
        }

        @Test
        @DisplayName("Should return empty and 403 when user not admin")
        void shouldReturn403WhenNotAdmin() {
            User regularUser = createTestUser(1, "user", true);
            when(ctx.attribute("user")).thenReturn(regularUser);
            when(adminSecurityService.isAdmin(regularUser)).thenReturn(false);
            when(ctx.status(403)).thenReturn(ctx);

            Optional<User> result = AdminControllerUtils.getAdminUser(ctx, adminSecurityService);

            assertTrue(result.isEmpty());
            verify(ctx).status(403);
            verify(ctx).json(any(SharedErrorResponse.class));
        }
    }

    @Nested
    @DisplayName("getAuthenticatedUser Tests")
    class GetAuthenticatedUserTests {

        @Test
        @DisplayName("Should return user when authenticated")
        void shouldReturnUserWhenAuthenticated() {
            User user = createTestUser(1, "user", true);
            when(ctx.attribute("user")).thenReturn(user);

            Optional<User> result = AdminControllerUtils.getAuthenticatedUser(ctx);

            assertTrue(result.isPresent());
            assertEquals(user, result.get());
        }

        @Test
        @DisplayName("Should return empty and 401 when not authenticated")
        void shouldReturn401WhenNotAuthenticated() {
            when(ctx.attribute("user")).thenReturn(null);
            when(ctx.status(401)).thenReturn(ctx);

            Optional<User> result = AdminControllerUtils.getAuthenticatedUser(ctx);

            assertTrue(result.isEmpty());
            verify(ctx).status(401);
        }
    }

    @Nested
    @DisplayName("checkRateLimit Tests")
    class CheckRateLimitTests {

        @Test
        @DisplayName("Should return true when rate limit not exceeded")
        void shouldReturnTrueWhenAllowed() {
            User admin = createTestUser(1, "admin", true);
            when(adminSecurityService.canPerformAction(1)).thenReturn(true);

            boolean result = AdminControllerUtils.checkRateLimit(ctx, admin, adminSecurityService);

            assertTrue(result);
            verify(ctx, never()).status(anyInt());
        }

        @Test
        @DisplayName("Should return false and 429 when rate limited")
        void shouldReturn429WhenRateLimited() {
            User admin = createTestUser(1, "admin", true);
            when(adminSecurityService.canPerformAction(1)).thenReturn(false);
            when(ctx.status(429)).thenReturn(ctx);

            boolean result = AdminControllerUtils.checkRateLimit(ctx, admin, adminSecurityService);

            assertFalse(result);
            verify(ctx).status(429);
            verify(ctx).json(any(SharedErrorResponse.class));
        }
    }

    @Nested
    @DisplayName("validateCsrf Tests")
    class ValidateCsrfTests {

        @Test
        @DisplayName("Should return true when CSRF valid")
        void shouldReturnTrueWhenValid() throws Exception {
            doNothing().when(csrfService).requireValidCsrfToken(ctx);

            boolean result = AdminControllerUtils.validateCsrf(ctx, csrfService, "test operation");

            assertTrue(result);
            verify(ctx, never()).status(anyInt());
        }

        @Test
        @DisplayName("Should return false and 403 when CSRF invalid")
        void shouldReturn403WhenInvalid() throws Exception {
            doThrow(new CsrfProtectionService.CsrfValidationException("Invalid token"))
                .when(csrfService).requireValidCsrfToken(ctx);
            when(ctx.status(403)).thenReturn(ctx);

            boolean result = AdminControllerUtils.validateCsrf(ctx, csrfService, "test operation");

            assertFalse(result);
            verify(ctx).status(403);
            verify(ctx).json(any(SharedErrorResponse.class));
        }
    }

    @Nested
    @DisplayName("getClientIp Tests")
    class GetClientIpTests {

        @Test
        @DisplayName("Should extract IP from X-Forwarded-For header")
        void shouldExtractFromXForwardedFor() {
            when(ctx.header("X-Forwarded-For")).thenReturn("192.168.1.100, 10.0.0.1");

            String ip = AdminControllerUtils.getClientIp(ctx);

            assertEquals("192.168.1.100", ip);
        }

        @Test
        @DisplayName("Should handle single IP in X-Forwarded-For")
        void shouldHandleSingleIpInXForwardedFor() {
            when(ctx.header("X-Forwarded-For")).thenReturn("192.168.1.100");

            String ip = AdminControllerUtils.getClientIp(ctx);

            assertEquals("192.168.1.100", ip);
        }

        @Test
        @DisplayName("Should fall back to direct IP when no X-Forwarded-For")
        void shouldFallBackToDirectIp() {
            when(ctx.header("X-Forwarded-For")).thenReturn(null);
            when(ctx.ip()).thenReturn("10.0.0.1");

            String ip = AdminControllerUtils.getClientIp(ctx);

            assertEquals("10.0.0.1", ip);
        }

        @Test
        @DisplayName("Should fall back to direct IP when X-Forwarded-For is empty")
        void shouldFallBackWhenXForwardedForEmpty() {
            when(ctx.header("X-Forwarded-For")).thenReturn("");
            when(ctx.ip()).thenReturn("10.0.0.1");

            String ip = AdminControllerUtils.getClientIp(ctx);

            assertEquals("10.0.0.1", ip);
        }

        @Test
        @DisplayName("Should trim whitespace from X-Forwarded-For IP")
        void shouldTrimWhitespace() {
            when(ctx.header("X-Forwarded-For")).thenReturn("  192.168.1.100  , 10.0.0.1");

            String ip = AdminControllerUtils.getClientIp(ctx);

            assertEquals("192.168.1.100", ip);
        }
    }

    @Nested
    @DisplayName("getClientUserAgent Tests")
    class GetClientUserAgentTests {

        @Test
        @DisplayName("Should return User-Agent header value")
        void shouldReturnUserAgent() {
            when(ctx.header("User-Agent")).thenReturn("Mozilla/5.0 Test Browser");

            String userAgent = AdminControllerUtils.getClientUserAgent(ctx);

            assertEquals("Mozilla/5.0 Test Browser", userAgent);
        }

        @Test
        @DisplayName("Should return null when User-Agent not present")
        void shouldReturnNullWhenNotPresent() {
            when(ctx.header("User-Agent")).thenReturn(null);

            String userAgent = AdminControllerUtils.getClientUserAgent(ctx);

            assertNull(userAgent);
        }
    }

    @Nested
    @DisplayName("getClientInfo Tests")
    class GetClientInfoTests {

        @Test
        @DisplayName("Should return ClientInfo with IP and User-Agent")
        void shouldReturnClientInfo() {
            when(ctx.header("X-Forwarded-For")).thenReturn("192.168.1.100");
            when(ctx.header("User-Agent")).thenReturn("Test Browser");

            AdminControllerUtils.ClientInfo info = AdminControllerUtils.getClientInfo(ctx);

            assertEquals("192.168.1.100", info.ip());
            assertEquals("Test Browser", info.userAgent());
        }
    }

    @Nested
    @DisplayName("parsePathParamId Tests")
    class ParsePathParamIdTests {

        @Test
        @DisplayName("Should parse valid integer ID")
        void shouldParseValidId() {
            when(ctx.pathParam("id")).thenReturn("123");

            Optional<Integer> result = AdminControllerUtils.parsePathParamId(ctx, "id", "user");

            assertTrue(result.isPresent());
            assertEquals(123, result.get());
        }

        @Test
        @DisplayName("Should parse zero ID")
        void shouldParseZeroId() {
            when(ctx.pathParam("id")).thenReturn("0");

            Optional<Integer> result = AdminControllerUtils.parsePathParamId(ctx, "id", "user");

            assertTrue(result.isPresent());
            assertEquals(0, result.get());
        }

        @Test
        @DisplayName("Should parse negative ID")
        void shouldParseNegativeId() {
            when(ctx.pathParam("id")).thenReturn("-1");

            Optional<Integer> result = AdminControllerUtils.parsePathParamId(ctx, "id", "user");

            assertTrue(result.isPresent());
            assertEquals(-1, result.get());
        }

        @Test
        @DisplayName("Should return empty and 400 for non-numeric ID")
        void shouldReturn400ForNonNumericId() {
            when(ctx.pathParam("id")).thenReturn("abc");
            when(ctx.status(400)).thenReturn(ctx);

            Optional<Integer> result = AdminControllerUtils.parsePathParamId(ctx, "id", "user");

            assertTrue(result.isEmpty());
            verify(ctx).status(400);
            verify(ctx).json(any(SharedErrorResponse.class));
        }

        @Test
        @DisplayName("Should return empty and 400 for decimal ID")
        void shouldReturn400ForDecimalId() {
            when(ctx.pathParam("id")).thenReturn("12.5");
            when(ctx.status(400)).thenReturn(ctx);

            Optional<Integer> result = AdminControllerUtils.parsePathParamId(ctx, "id", "user");

            assertTrue(result.isEmpty());
            verify(ctx).status(400);
        }

        @Test
        @DisplayName("Should include entity name in error message")
        void shouldIncludeEntityNameInError() {
            when(ctx.pathParam("screennameId")).thenReturn("abc");
            when(ctx.status(400)).thenReturn(ctx);

            AdminControllerUtils.parsePathParamId(ctx, "screennameId", "screenname");

            ArgumentCaptor<SharedErrorResponse> captor = ArgumentCaptor.forClass(SharedErrorResponse.class);
            verify(ctx).json(captor.capture());
            assertTrue(captor.getValue().message().contains("screenname"));
        }
    }

    @Nested
    @DisplayName("logAdminAction Tests")
    class LogAdminActionTests {

        @Test
        @DisplayName("Should log admin action with all parameters")
        void shouldLogActionWithAllParams() {
            User admin = createTestUser(1, "admin", true);
            Map<String, Object> details = Map.of("key", "value");
            when(ctx.header("X-Forwarded-For")).thenReturn("192.168.1.100");
            when(ctx.header("User-Agent")).thenReturn("Test Browser");

            AdminControllerUtils.logAdminAction(
                adminAuditService, admin, "DELETE_USER", 2, 3, details, ctx);

            verify(adminAuditService).logAction(
                eq(admin),
                eq("DELETE_USER"),
                eq(2),
                eq(3),
                eq(details),
                eq("192.168.1.100"),
                eq("Test Browser")
            );
        }

        @Test
        @DisplayName("Should handle null target IDs")
        void shouldHandleNullTargetIds() {
            User admin = createTestUser(1, "admin", true);
            when(ctx.header("X-Forwarded-For")).thenReturn(null);
            when(ctx.ip()).thenReturn("10.0.0.1");
            when(ctx.header("User-Agent")).thenReturn(null);

            AdminControllerUtils.logAdminAction(
                adminAuditService, admin, "LIST_USERS", null, null, Map.of(), ctx);

            verify(adminAuditService).logAction(
                eq(admin),
                eq("LIST_USERS"),
                isNull(),
                isNull(),
                eq(Map.of()),
                eq("10.0.0.1"),
                isNull()
            );
        }
    }

    @Nested
    @DisplayName("SharedErrorResponse Tests")
    class SharedErrorResponseTests {

        @Test
        @DisplayName("unauthorized() should create correct error type")
        void unauthorizedShouldWork() {
            SharedErrorResponse response = SharedErrorResponse.unauthorized("test message");
            assertEquals("Unauthorized", response.error());
            assertEquals("test message", response.message());
        }

        @Test
        @DisplayName("forbidden() should create correct error type")
        void forbiddenShouldWork() {
            SharedErrorResponse response = SharedErrorResponse.forbidden("test message");
            assertEquals("Forbidden", response.error());
            assertEquals("test message", response.message());
        }

        @Test
        @DisplayName("badRequest() should create correct error type")
        void badRequestShouldWork() {
            SharedErrorResponse response = SharedErrorResponse.badRequest("test message");
            assertEquals("Bad request", response.error());
            assertEquals("test message", response.message());
        }

        @Test
        @DisplayName("notFound() should create correct error type")
        void notFoundShouldWork() {
            SharedErrorResponse response = SharedErrorResponse.notFound("test message");
            assertEquals("Not found", response.error());
            assertEquals("test message", response.message());
        }

        @Test
        @DisplayName("rateLimited() should create correct error type")
        void rateLimitedShouldWork() {
            SharedErrorResponse response = SharedErrorResponse.rateLimited("test message");
            assertEquals("Rate limit exceeded", response.error());
            assertEquals("test message", response.message());
        }

        @Test
        @DisplayName("csrfFailed() should create correct error type")
        void csrfFailedShouldWork() {
            SharedErrorResponse response = SharedErrorResponse.csrfFailed("test message");
            assertEquals("CSRF validation failed", response.error());
            assertEquals("test message", response.message());
        }

        @Test
        @DisplayName("serverError() should create correct error type")
        void serverErrorShouldWork() {
            SharedErrorResponse response = SharedErrorResponse.serverError("test message");
            assertEquals("Server error", response.error());
            assertEquals("test message", response.message());
        }

        @Test
        @DisplayName("conflict() should create correct error type")
        void conflictShouldWork() {
            SharedErrorResponse response = SharedErrorResponse.conflict("test message");
            assertEquals("Conflict", response.error());
            assertEquals("test message", response.message());
        }

        @Test
        @DisplayName("Record equality should work correctly")
        void recordEqualityShouldWork() {
            SharedErrorResponse a = new SharedErrorResponse("Error", "Message");
            SharedErrorResponse b = new SharedErrorResponse("Error", "Message");

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("Record toString should contain field values")
        void recordToStringShouldContainFields() {
            SharedErrorResponse response = new SharedErrorResponse("TestError", "TestMessage");
            String str = response.toString();

            assertTrue(str.contains("TestError"));
            assertTrue(str.contains("TestMessage"));
        }
    }

    @Nested
    @DisplayName("ClientInfo Record Tests")
    class ClientInfoRecordTests {

        @Test
        @DisplayName("ClientInfo should store IP and User-Agent")
        void shouldStoreIpAndUserAgent() {
            AdminControllerUtils.ClientInfo info =
                new AdminControllerUtils.ClientInfo("192.168.1.1", "Test Browser");

            assertEquals("192.168.1.1", info.ip());
            assertEquals("Test Browser", info.userAgent());
        }

        @Test
        @DisplayName("ClientInfo equality should work correctly")
        void clientInfoEqualityShouldWork() {
            AdminControllerUtils.ClientInfo a =
                new AdminControllerUtils.ClientInfo("192.168.1.1", "Browser");
            AdminControllerUtils.ClientInfo b =
                new AdminControllerUtils.ClientInfo("192.168.1.1", "Browser");

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }
    }
}
