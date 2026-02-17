/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.api;

/**
 * Shared error response DTO for web API endpoints.
 *
 * <p>Replaces the duplicate ErrorResponse records defined in each controller.
 * Provides factory methods for common error types.
 */
public record SharedErrorResponse(String error, String message) {

    public static SharedErrorResponse unauthorized(String message) {
        return new SharedErrorResponse("Unauthorized", message);
    }

    public static SharedErrorResponse forbidden(String message) {
        return new SharedErrorResponse("Forbidden", message);
    }

    public static SharedErrorResponse badRequest(String message) {
        return new SharedErrorResponse("Bad request", message);
    }

    public static SharedErrorResponse notFound(String message) {
        return new SharedErrorResponse("Not found", message);
    }

    public static SharedErrorResponse rateLimited(String message) {
        return new SharedErrorResponse("Rate limit exceeded", message);
    }

    public static SharedErrorResponse csrfFailed(String message) {
        return new SharedErrorResponse("CSRF validation failed", message);
    }

    public static SharedErrorResponse serverError(String message) {
        return new SharedErrorResponse("Server error", message);
    }

    public static SharedErrorResponse conflict(String message) {
        return new SharedErrorResponse("Conflict", message);
    }
}
