/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.api;

import com.dialtone.db.models.User;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.web.auth.AuthController;
import com.dialtone.web.security.CsrfProtectionService;
import com.dialtone.web.services.AdminSecurityService;
import io.javalin.http.Context;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;

import static com.dialtone.web.api.AdminControllerUtils.checkRateLimit;
import static com.dialtone.web.api.AdminControllerUtils.getAdminUser;
import static com.dialtone.web.api.AdminControllerUtils.validateCsrf;

/**
 * Admin-only proxy for writable default-disk operations.
 *
 * <p>The browser-hosted admin page runs under a strict same-origin CSP. Disk
 * writes are proxied here so they remain admin-authenticated without relying on
 * browser CORS behavior from the emulator origin.
 */
public class AdminDiskController {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String DEFAULT_MAC_ORIGIN = "https://mac.dialtone.live";
    private static final String DEFAULT_DISK_NAME = "default_system7-6-1.dsk";

    private final AdminSecurityService adminSecurityService;
    private final CsrfProtectionService csrfService;
    private final HttpClient httpClient;
    private final String macOrigin;
    private final String defaultDiskName;

    public AdminDiskController(AdminSecurityService adminSecurityService,
                               CsrfProtectionService csrfService,
                               Properties config) {
        this.adminSecurityService = adminSecurityService;
        this.csrfService = csrfService;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
        this.macOrigin = stripTrailingSlash(
            config.getProperty("admin.disk.mac.origin", DEFAULT_MAC_ORIGIN));
        this.defaultDiskName = config.getProperty("admin.disk.default.name", DEFAULT_DISK_NAME);
    }

    /**
     * Snapshots the default disk through the emulator relay.
     * POST /api/admin/disk/snapshot
     */
    public void snapshotDefaultDisk(Context ctx) {
        try {
            if (!validateCsrf(ctx, csrfService, "default disk snapshot")) return;

            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            String bearerToken = resolveBearerToken(ctx);
            if (bearerToken == null) {
                ctx.status(401).json(SharedErrorResponse.unauthorized("Missing admin bearer token"));
                return;
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(snapshotUri())
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + bearerToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String body = response.body();

            if (status >= 200 && status < 300) {
                ctx.status(status);
                ctx.contentType(contentTypeOrJson(response));
                ctx.result(body == null ? "{}" : body);
                LoggerUtil.info(String.format("Admin %s snapshotted default disk %s",
                    admin.xUsername(), defaultDiskName));
                return;
            }

            LoggerUtil.warn(String.format(
                "Default disk snapshot failed for admin %s: upstream=%d body=%s",
                admin.xUsername(), status, abbreviate(body)));

            ctx.status(status == 401 || status == 403 || status == 409 || status == 423 ? status : 502)
                .json(SharedErrorResponse.serverError(
                    "Default disk snapshot failed at emulator relay (" + status + ")"));

        } catch (IllegalArgumentException e) {
            LoggerUtil.warn("Invalid default disk snapshot configuration: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Default disk snapshot is misconfigured"));
        } catch (IOException e) {
            LoggerUtil.error("Could not reach emulator relay for default disk snapshot: " + e.getMessage());
            ctx.status(502).json(SharedErrorResponse.serverError(
                "Could not reach the emulator relay for the snapshot"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LoggerUtil.error("Default disk snapshot interrupted: " + e.getMessage());
            ctx.status(502).json(SharedErrorResponse.serverError("Default disk snapshot was interrupted"));
        } catch (Exception e) {
            LoggerUtil.error("Unexpected default disk snapshot error: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Default disk snapshot failed"));
        }
    }

    /**
     * Returns writable disk editor configuration.
     * GET /api/admin/disk/config
     */
    public void getConfig(Context ctx) {
        try {
            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            ctx.json(new DiskConfigResponse(macOrigin, defaultDiskName));
        } catch (Exception e) {
            LoggerUtil.error("Failed to get default disk config: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to load default disk config"));
        }
    }

    private URI snapshotUri() {
        if (defaultDiskName == null || defaultDiskName.isBlank()) {
            throw new IllegalArgumentException("admin.disk.default.name is blank");
        }
        String encodedDisk = URLEncoder.encode(defaultDiskName, StandardCharsets.UTF_8);
        return URI.create(macOrigin + "/disk/snapshot?name=" + encodedDisk);
    }

    private static String resolveBearerToken(Context ctx) {
        String authHeader = ctx.header("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        String cookieToken = ctx.cookie(AuthController.AUTH_COOKIE_NAME);
        if (cookieToken != null && !cookieToken.isBlank()) {
            return cookieToken;
        }

        return null;
    }

    private static String stripTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? DEFAULT_MAC_ORIGIN : trimmed;
    }

    private static String contentTypeOrJson(HttpResponse<String> response) {
        return response.headers()
            .firstValue("content-type")
            .orElse("application/json");
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }

    public record DiskConfigResponse(String macOrigin, String defaultDiskName) {
    }
}
