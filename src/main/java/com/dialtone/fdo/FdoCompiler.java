/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo;

import com.dialtone.db.models.ScreennamePreferences;
import com.dialtone.fdo.spi.FdoCompilationException;
import com.dialtone.fdo.spi.FdoCompilationService;
import com.dialtone.fdo.spi.FdoServiceFactory;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.web.services.ScreennamePreferencesService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * High-level FDO compiler facade.
 *
 * <p>This class provides compilation services for FDO (Form Definition Objects).
 * It delegates to the native atomforge-fdo Java library for compilation.</p>
 *
 * <p>For extracting data from FDO binary, use {@link FdoStreamExtractor} which provides
 * type-safe access to atoms via the FdoStream object model API.</p>
 */
public class FdoCompiler {

    /**
     * Sentinel value for stream ID parameter to request auto-generation.
     * When this value is passed as streamId, a random valid stream ID (0x0001-0xFFFE)
     * will be automatically generated.
     */
    public static final int AUTO_GENERATE_STREAM_ID = -1;

    /**
     * Resolve a stream ID, generating a random one if AUTO_GENERATE_STREAM_ID is passed.
     * 
     * @param streamId The stream ID to resolve, or AUTO_GENERATE_STREAM_ID for auto-generation
     * @return The resolved stream ID (random if auto-generation was requested)
     */
    public static int resolveStreamId(int streamId) {
        if (streamId == AUTO_GENERATE_STREAM_ID) {
            // Generate random stream ID in valid range: 0x0001 to 0xFFFE
            return java.util.concurrent.ThreadLocalRandom.current()
                .nextInt(0x0001, 0xFFFF);
        }
        return streamId;
    }

    /**
     * Extract GID (Group ID) from an FDO resource path.
     * 
     * <p>Converts paths like "fdo/noop.fdo.txt" to "noop" for DSL registry lookup.
     * Handles various path formats:
     * <ul>
     *   <li>"fdo/noop.fdo.txt" → "noop"</li>
     *   <li>"fdo/post_login/username_config.fdo.txt" → "post_login/username_config"</li>
     *   <li>"replace_client_fdo/69-420.fdo.txt" → "69-420"</li>
     *   <li>"fdo/receive_im.bw.fdo.txt" → "receive_im"</li>
     * </ul>
     * 
     * @param resourcePath The FDO resource path
     * @return The extracted GID, or null if path is null, or the path as-is if no prefix matches
     */
    public static String extractGidFromPath(String resourcePath) {
        if (resourcePath == null) {
            return null;
        }
        
        if (resourcePath.isEmpty()) {
            return "";
        }

        // Remove .bw.fdo.txt suffix first (before .fdo.txt)
        String withoutSuffix = resourcePath;
        if (resourcePath.endsWith(".bw.fdo.txt")) {
            withoutSuffix = resourcePath.substring(0, resourcePath.length() - 11);
        } else if (resourcePath.endsWith(".fdo.txt")) {
            withoutSuffix = resourcePath.substring(0, resourcePath.length() - 8);
        }

        // Remove common prefixes
        if (withoutSuffix.startsWith("fdo/")) {
            return withoutSuffix.substring(4);
        } else if (withoutSuffix.startsWith("replace_client_fdo/")) {
            return withoutSuffix.substring(19);
        }

        // Return as-is if no prefix matches (for paths without standard prefixes)
        return withoutSuffix;
    }

    private final FdoCompilationService compilationService;
    private final String defaultToken;
    private final int defaultStreamId;

    public FdoCompiler(Properties properties) {
        this.compilationService = FdoServiceFactory.createCompilationService(properties);
        this.defaultToken = properties.getProperty("p3.default.token", "AT");
        this.defaultStreamId = Integer.parseInt(properties.getProperty("p3.default.stream.id", "0"));

        LoggerUtil.info(String.format("[FdoCompiler] Initialized with %s compilation backend",
            compilationService.getBackendName()));
    }

    /**
     * Get the configured backend name.
     *
     * @return backend identifier (e.g., "java")
     */
    public String getBackendName() {
        return compilationService.getBackendName();
    }

    /**
     * Compile FDO source to binary.
     *
     * @param fdoSource FDO source text
     * @return compiled binary data
     * @throws FdoCompilationException if compilation fails
     */
    public byte[] compileFdoScript(String fdoSource) throws FdoCompilationException {
        return compilationService.compile(fdoSource);
    }

    /**
     * Compile FDO file to binary.
     *
     * @param resourcePath path to FDO file in resources
     * @return compiled binary data
     * @throws FdoCompilationException if compilation fails
     * @throws IOException if file cannot be read
     */
    public byte[] compileFdoFile(String resourcePath) throws FdoCompilationException, IOException {
        String fdoSource = loadFdoFromResource(resourcePath);
        return compileFdoScript(fdoSource);
    }

    /**
     * Load FDO source from a resource file.
     *
     * @param resourcePath path to FDO file in resources
     * @return FDO source text
     * @throws IOException if file cannot be read
     */
    public String loadFdoFromResource(String resourcePath) throws IOException {
        if (resourcePath == null) {
            throw new IOException("FDO resource path cannot be null");
        }
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("FDO resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Compile FDO source to P3 protocol chunks using default configuration.
     */
    public List<FdoChunk> compileFdoScriptToP3Chunks(String fdoSource) throws FdoCompilationException {
        return compileFdoScriptToP3Chunks(fdoSource, defaultToken, defaultStreamId);
    }

    /**
     * Compile FDO source to P3 protocol chunks with specified parameters.
     * Tags each chunk with the Stream ID for proper multi-stream handling.
     *
     * @param fdoSource FDO source text to compile
     * @param token Token for P3 chunk generation
     * @param streamId Stream ID for P3 chunk generation (or AUTO_GENERATE_STREAM_ID for auto-generation)
     * @return List of P3 chunks ready for transmission
     * @throws FdoCompilationException if compilation fails
     */
    public List<FdoChunk> compileFdoScriptToP3Chunks(String fdoSource, String token, int streamId) throws FdoCompilationException {
        // Resolve stream ID (auto-generate if requested)
        int actualStreamId = resolveStreamId(streamId);
        if (streamId == AUTO_GENERATE_STREAM_ID) {
            LoggerUtil.debug(() -> String.format(
                "[FdoCompiler] Auto-generated stream ID: 0x%04X", actualStreamId));
        }

        String displayName = extractDisplayName(fdoSource);
        String backendName = compilationService.getBackendName();

        LoggerUtil.debug(String.format("[%s] compile start | source:%s | token:%s | streamId:0x%04X",
            backendName, displayName, token, actualStreamId));

        LoggerUtil.debug(String.format("source: %s",
                fdoSource));

        long startTime = System.currentTimeMillis();

        try {
            List<FdoChunk> chunks = compilationService.compileToChunks(fdoSource, token, actualStreamId);

            long duration = System.currentTimeMillis() - startTime;
            int totalBytes = chunks.stream().mapToInt(c -> c.getBinaryData().length).sum();

            LoggerUtil.info(String.format("[%s] compile complete | source:%s | duration:%dms | chunks:%d | bytes:%d",
                backendName, displayName, duration, chunks.size(), totalBytes));

            return chunks;

        } catch (FdoCompilationException e) {
            long duration = System.currentTimeMillis() - startTime;
            LoggerUtil.error(String.format("[%s] compile error | source:%s | duration:%dms | error:%s",
                backendName, displayName, duration, e.getMessage()));
            throw e;
        }
    }

    /**
     * Extract readable display name from FDO source.
     * If source is short (likely a file path), return it.
     * If source is long (inline FDO), return first 40 chars.
     */
    private String extractDisplayName(String fdoSource) {
        if (fdoSource == null) return "null";
        if (fdoSource.length() < 100 && !fdoSource.contains("\n")) {
            return fdoSource;  // Short string, probably a file name
        }
        return "inline:" + fdoSource.substring(0, Math.min(40, fdoSource.length())).replace("\n", " ") + "...";
    }

    /**
     * Compile FDO file to P3 protocol chunks using default configuration.
     */
    public List<FdoChunk> compileFdoFileToP3Chunks(String resourcePath) throws FdoCompilationException, IOException {
        String fdoSource = loadFdoFromResource(resourcePath);
        return compileFdoScriptToP3Chunks(fdoSource);
    }

    /**
     * Compile FDO file to P3 protocol chunks with specified parameters.
     */
    public List<FdoChunk> compileFdoFileToP3Chunks(String resourcePath, String token, int streamId) throws FdoCompilationException, IOException {
        String fdoSource = loadFdoFromResource(resourcePath);
        return compileFdoScriptToP3Chunks(fdoSource, token, streamId);
    }

    /**
     * Compile FDO template file to P3 protocol chunks with specified parameters.
     *
     * @param templatePath Path to template file in resources (e.g., "fdo/motd.fdo.txt")
     * @param variables Map of variable names to values for substitution
     * @param token Token for P3 chunk generation
     * @param streamId Stream ID for P3 chunk generation (or AUTO_GENERATE_STREAM_ID for auto-generation)
     * @return List of P3 chunks ready for transmission
     */
    public List<FdoChunk> compileFdoTemplateToP3Chunks(String templatePath, Map<String, ? extends Object> variables, String token, int streamId) throws FdoCompilationException, IOException {
        return compileFdoTemplateToP3Chunks(templatePath, variables, token, streamId, false);
    }

    /**
     * Compile FDO template file to P3 protocol chunks with variant resolution support.
     * When lowColorMode is true, attempts to load a .bw.fdo.txt variant first.
     *
     * <p>Supports both String and byte[] variable values. Variables ending with "_DATA"
     * that contain byte[] values are automatically converted to FDO hex format.
     *
     * @param templatePath Path to template file in resources (e.g., "fdo/motd.fdo.txt")
     * @param variables Map of variable names to values for substitution (String or byte[])
     * @param token Token for P3 chunk generation
     * @param streamId Stream ID for P3 chunk generation (or AUTO_GENERATE_STREAM_ID for auto-generation)
     * @param lowColorMode If true, prefer .bw.fdo.txt variant when available
     * @return List of P3 chunks ready for transmission
     */
    @SuppressWarnings("unchecked")
    public List<FdoChunk> compileFdoTemplateToP3Chunks(String templatePath, Map<String, ? extends Object> variables, String token, int streamId, boolean lowColorMode) throws FdoCompilationException, IOException {
        // Resolve stream ID (auto-generate if requested)
        int actualStreamId = resolveStreamId(streamId);
        if (streamId == AUTO_GENERATE_STREAM_ID) {
            LoggerUtil.debug(() -> String.format(
                "[FdoCompiler] Auto-generated stream ID: 0x%04X for template: %s",
                actualStreamId, templatePath));
        }

        // Resolve variant path for low color mode
        String resolvedPath = FdoTemplateEngine.resolveVariantPath(templatePath, lowColorMode);
        String backendName = compilationService.getBackendName();

        LoggerUtil.debug(String.format("[%s] template compile start | template:%s%s | token:%s | streamId:0x%04X",
            backendName, resolvedPath, lowColorMode && !resolvedPath.equals(templatePath) ? " (BW variant)" : "", token, actualStreamId));

        long startTime = System.currentTimeMillis();

        try {
            // Cast to Map<String, Object> for template processing - safe because we only read from it
            Map<String, Object> varsAsObject = (Map<String, Object>) variables;
            String processedFdo = FdoTemplateEngine.processTemplate(resolvedPath, varsAsObject);
            LoggerUtil.info("processed fdo: " + processedFdo);
            List<FdoChunk> chunks = compileFdoScriptToP3Chunks(processedFdo, token, actualStreamId);

            long duration = System.currentTimeMillis() - startTime;
            int totalBytes = chunks.stream().mapToInt(c -> c.getBinaryData().length).sum();

            LoggerUtil.info(String.format("[%s] template compile complete | template:%s | duration:%dms | chunks:%d | bytes:%d",
                backendName, templatePath, duration, chunks.size(), totalBytes));

            return chunks;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            LoggerUtil.error(String.format("[%s] template compile error | template:%s | duration:%dms | error:%s",
                backendName, templatePath, duration, e.getMessage()));
            throw e;
        }
    }

    /**
     * Compile FDO template for a specific target user (not the current session user).
     * Automatically resolves the target user's low_color_mode preference for BW variant selection.
     *
     * <p>Use this method when compiling FDOs to be sent to a different user than the current session,
     * such as instant messages where the recipient's preference should be used, not the sender's.
     *
     * @param templatePath Path to template file in resources (e.g., "fdo/receive_im.fdo.txt")
     * @param variables Map of variable names to values for substitution
     * @param token Token for P3 chunk generation
     * @param streamId Stream ID for P3 chunk generation (or AUTO_GENERATE_STREAM_ID for auto-generation)
     * @param targetUsername The username of the user who will receive this FDO
     * @param preferencesService Service to look up the target user's preferences
     * @return List of P3 chunks ready for transmission
     */
    public List<FdoChunk> compileFdoTemplateForTargetUser(
            String templatePath,
            Map<String, String> variables,
            String token,
            int streamId,
            String targetUsername,
            ScreennamePreferencesService preferencesService) throws FdoCompilationException, IOException {

        boolean lowColorMode = resolveUserLowColorMode(targetUsername, preferencesService);
        return compileFdoTemplateToP3Chunks(templatePath, variables, token, streamId, lowColorMode);
    }

    /**
     * Resolve whether a specific user has low color mode enabled.
     *
     * @param username The username to look up
     * @param service The preferences service to use for lookup
     * @return true if the user has low_color_mode enabled, false otherwise or on error
     */
    private boolean resolveUserLowColorMode(String username, ScreennamePreferencesService service) {
        if (service == null || username == null) {
            return false;
        }
        try {
            ScreennamePreferences prefs = service.getPreferencesByScreenname(username);
            return prefs.isLowColorModeEnabled();
        } catch (Exception e) {
            LoggerUtil.warn(String.format("[FdoCompiler] Failed to resolve low color mode for user '%s': %s",
                username, e.getMessage()));
            return false; // Default to standard theme on error
        }
    }

    public void close() throws IOException {
        compilationService.close();
    }
}
