/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.dod;

import com.dialtone.art.ArtService;
import com.dialtone.db.models.ScreennamePreferences;
import com.dialtone.fdo.*;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.FdoDslRegistry;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.DodNotAvailableFdoBuilder;
import com.dialtone.fdo.dsl.builders.DodResponseFdoBuilder;
import com.dialtone.fdo.dsl.builders.F1AtomStreamResponseFdoBuilder;
import com.dialtone.fdo.dsl.builders.F2IdbResponseFdoBuilder;
import com.dialtone.fdo.dsl.builders.IdbResetAndSendFdoBuilder;
import com.dialtone.fdo.dsl.builders.K1ResponseFdoBuilder;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.protocol.ClientPlatform;
import com.dialtone.protocol.GidUtils;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.web.services.ScreennamePreferencesService;
import io.netty.channel.ChannelHandlerContext;

import com.dialtone.fdo.spi.FdoCompilationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles Download on Demand (DOD) requests from AOL clients.
 * Extracts Stream ID from incoming fh frames and generates appropriate DOD responses.
 */
public class DodRequestHandler {

    private final FdoCompiler fdoCompiler;
    private final ArtService artService;
    private final Properties properties;
    private final ScreennamePreferencesService preferencesService;

    
    // Cumulative IDB corruption tracking
    private final Map<String, Integer> idbUpdateCounts = new ConcurrentHashMap<>();
    private final Map<String, byte[]> firstIdbCompilation = new ConcurrentHashMap<>();

    private static final String TOKEN_STANDARD = "AT";
    private static final String TOKEN_F2 = "AT";
    private static final String TOKEN_F1 = "AT";
    private static final String TOKEN_K1 = "AT";

    private enum DodRequestType {
        FH_FORM,
        F2_DIRECT
    }

    public DodRequestHandler(FdoCompiler fdoCompiler, ArtService artService, Properties properties) {
        this(fdoCompiler, artService, properties, null);
    }

    public DodRequestHandler(FdoCompiler fdoCompiler, ArtService artService, Properties properties, ScreennamePreferencesService preferencesService) {
        this.fdoCompiler = fdoCompiler;
        this.artService = artService;
        this.properties = properties;
        this.preferencesService = preferencesService;
    }

    private static String logPrefix(String username) {
        return username != null ? "[" + username + "][DOD] " : "[DOD] ";
    }

    /**
     * Check if low color mode is enabled for the given username.
     * Returns false if preferences service is unavailable or username is null.
     */
    private boolean isLowColorModeEnabled(String username) {
        if (preferencesService == null || username == null) {
            return false;
        }
        try {
            ScreennamePreferences prefs = preferencesService.getPreferencesByScreenname(username);
            return prefs.isLowColorModeEnabled();
        } catch (Exception e) {
            LoggerUtil.warn(logPrefix(username) + "Failed to load preferences: " + e.getMessage());
            return false;
        }
    }

    /**
     * Process an incoming fh frame containing a DOD request.
     * Extracts the Stream ID and generates appropriate response.
     *
     * @param ctx Channel context for sending response
     * @param fhFrame The incoming fh frame bytes
     * @param username Username for logging context
     * @param platform Client platform for platform-specific asset selection
     * @return DodResponse containing the Stream ID and compiled response chunks
     * @throws Exception if processing fails
     */
    public DodResponse processDodRequest(ChannelHandlerContext ctx, byte[] fhFrame, String username, ClientPlatform platform) throws Exception {
        LoggerUtil.info(logPrefix(username) + String.format("Processing fh frame: %d bytes", fhFrame.length));

        // Diagnostic: dump first 20 bytes of frame header
        StringBuilder headerHex = new StringBuilder();
        for (int i = 0; i < Math.min(20, fhFrame.length); i++) {
            if (i > 0) headerHex.append(" ");
            headerHex.append(String.format("%02X", fhFrame[i] & 0xFF));
        }
        LoggerUtil.info(logPrefix(username) + "fh frame header: " + headerHex);

        // Step 1: Extract Stream ID from P3 frame header (bytes 10-11, big-endian)
        // P3 frame format: [sync:1][crc:2][len:2][tx:1][rx:1][type:1][token:2][streamId:2][fdo...][0x0D]
        int streamId = 0;
        if (fhFrame.length >= 12) {
            streamId = ((fhFrame[10] & 0xFF) << 8) | (fhFrame[11] & 0xFF);
            LoggerUtil.info(logPrefix(username) + String.format("Stream ID from bytes 10-11: 0x%04X (%d)", streamId, streamId));
            if (streamId > 0 && streamId < 0xFFFF) {
                LoggerUtil.info(logPrefix(username) + String.format("Found valid Stream ID in P3 header: 0x%04X", streamId));
            } else {
                LoggerUtil.info(logPrefix(username) + "Stream ID invalid, will try FDO extraction");
                streamId = 0; // Invalid, will try FDO extraction
            }
        }

        // Step 2: If Stream ID not in header, try extracting from FDO
        if (streamId == 0) {
            streamId = extractStreamIdFromFdo(fhFrame);
        }

        int normalizedStreamId = normalizeStreamId(streamId, username);

        // Step 3: Extract DOD request parameters from FDO
        DodRequestParams params = extractDodParameters(fhFrame);
        LoggerUtil.info(logPrefix(username) + String.format("Extracted DOD params: formId=%s, gidCount=%d",
            params.formId, params.gidPairs != null ? params.gidPairs.size() : 0));

        // Check if this is a stream control message (no GIDs)
        if (!params.hasGids()) {
            LoggerUtil.info(String.format(
                logPrefix(username) + "DOD stream control message (no GID): streamId=0x%04X(%d), formId=%s",
                normalizedStreamId, normalizedStreamId, params.formId));
            LoggerUtil.info(logPrefix(username) + "Returning empty response for stream control - ACK will be sent by handler");
            return new DodResponse(normalizedStreamId, params, new ArrayList<>());
        }

        // Log multi-GID detection
        if (params.hasMultipleGids()) {
            LoggerUtil.info(String.format(
                logPrefix(username) + "DOD Multi-GID Request: streamId=0x%04X(%d), formId=%s, gidCount=%d",
                normalizedStreamId, normalizedStreamId, params.formId, params.gidPairs.size()));
        } else {
            GidTransactionPair pair = params.gidPairs.get(0);
            LoggerUtil.info(String.format(
                logPrefix(username) + "DOD Request: streamId=0x%04X(%d), formId=%s, gid=%s, transactionId=%s",
                normalizedStreamId, normalizedStreamId, params.formId, pair.gid, pair.transactionId));
        }

        // Step 4: Generate DOD responses for ALL GIDs
        List<FdoChunk> allResponseChunks = new ArrayList<>();

        for (GidTransactionPair pair : params.gidPairs) {
            List<FdoChunk> pairChunks = generateDodResponseForSingleGid(
                pair, params.formId, normalizedStreamId, username, DodRequestType.FH_FORM, platform);
            allResponseChunks.addAll(pairChunks);

            LoggerUtil.debug(String.format(
                logPrefix(username) + "Generated response for GID %s: %d chunks",
                pair.gid, pairChunks.size()));
        }

        LoggerUtil.info(String.format(
            logPrefix(username) + "DOD response generated: totalChunks=%d for %d GIDs, streamId=0x%04X",
            allResponseChunks.size(), params.gidPairs.size(), normalizedStreamId));

        return new DodResponse(normalizedStreamId, params, allResponseChunks);
    }

    /**
     * Process a direct GID-based DOD request (from f2 token).
     * This method handles DOD requests where the GID is provided directly
     * as a 32-bit integer rather than being encoded in an FDO.
     *
     * Supports both atom streams (from replace_client_fdo/) and art/pictures (from art/).
     * Atom FDOs take priority - if found, uses type "a", otherwise falls back to art with type "p".
     *
     * @param ctx Channel context for sending response
     * @param gid The 32-bit GID value extracted from f2 frame
     * @param streamId The Stream ID from the P3 frame header
     * @param username Username for logging context
     * @param platform Client platform for platform-specific asset selection
     * @return DodResponse containing the Stream ID and compiled response chunks
     * @throws Exception if processing fails
     */
    public DodResponse processDodRequest(ChannelHandlerContext ctx, int gid, int streamId, String username, ClientPlatform platform)
            throws Exception {
        LoggerUtil.debug(logPrefix(username) + "Processing direct GID DOD request");

        // Convert integer GID to AOL display format
        String gidDisplay = GidUtils.formatToDisplay(gid);
        
        // Create a DodRequestParams with a single GID
        DodRequestParams params = new DodRequestParams();
        params.gidPairs = new ArrayList<>();
        params.formId = null; // f2 requests do not provide a form ID
        params.gidPairs.add(new GidTransactionPair(gidDisplay, null));
        
        int normalizedStreamId = normalizeStreamId(streamId, username);

        LoggerUtil.info(String.format(
            logPrefix(username) + "f2 Direct DOD Request: streamId=0x%04X(%d), gid=%s (%d/0x%08X)",
            normalizedStreamId, normalizedStreamId, gidDisplay, gid, gid));

        // Build template variables - use Map<String, Object> to support byte[] values
        Map<String, Object> variables = new HashMap<>();
        variables.put("ATOM_GID", gidDisplay);

        // Step 1: Check for atom stream FDO (DSL registry first, then replace_client_fdo/)
        // Use variant resolution for low color mode (checks for .bw.fdo.txt or DSL BW variant)
        boolean lowColorMode = isLowColorModeEnabled(username);
        Optional<String> atomFdoSource = resolveFdoSource(gidDisplay, lowColorMode, platform, username);

        if (atomFdoSource.isPresent()) {
            // ATOM STREAM: compile FDO and set type "a"
            LoggerUtil.info(logPrefix(username) + "f2 atom stream request for GID: " + gidDisplay +
                (lowColorMode ? " (using BW variant if available)" : ""));

            try {
                // Apply button theme variable substitution before compilation
                Map<String, Object> buttonVars = new FdoVariableBuilder()
                    .withButtonTheme(properties)
                    .buildAsObjects();
                String processedFdo = FdoTemplateEngine.substituteVariables(atomFdoSource.get(), buttonVars);
                byte[] compiledFdo = fdoCompiler.compileFdoScript(processedFdo);

                LoggerUtil.info(String.format(
                    logPrefix(username) + "Compiled f2 atom stream FDO: %d bytes for GID %s",
                    compiledFdo.length, gidDisplay));

                LoggerUtil.info(String.format("fdo to compile for idb: " + processedFdo));

                variables.put("IDB_OBJ_TYPE", "a");
                // Use byte[] with auto-hex conversion via IDB_APPEND_DATA
                variables.put("IDB_APPEND_DATA", compiledFdo);
                variables.put("DATA_LENGTH", String.valueOf(compiledFdo.length));

            } catch (Exception e) {
                LoggerUtil.error(String.format(
                    logPrefix(username) + "Failed to compile f2 atom stream FDO for GID %s: %s",
                    gidDisplay, e.getMessage()));
                // Return empty response on compilation failure
                return new DodResponse(normalizedStreamId, params, new ArrayList<>());
            }
        } else {
            // PICTURE/ART: load art and set type "p"
            LoggerUtil.info(logPrefix(username) + "f2 art request for GID: " + gidDisplay);

            Optional<ArtPayload> artPayload = loadArtPayload(gidDisplay, username, platform);
            if (artPayload.isPresent()) {
                byte[] idbData = artPayload.get().idbData;
                variables.put("IDB_OBJ_TYPE", "p");
                // Use byte[] with auto-hex conversion via IDB_APPEND_DATA
                variables.put("IDB_APPEND_DATA", idbData);
                variables.put("DATA_LENGTH", String.valueOf(idbData.length));
            } else {
                // Art not found - return empty response
                LoggerUtil.info(logPrefix(username) + "No art found for GID: " + gidDisplay);
                return new DodResponse(normalizedStreamId, params, new ArrayList<>());
            }
        }

        // Step 2: Build and compile IDB response using DSL builder
        FdoGid atomGid = parseGidToFdoGid(gidDisplay);
        byte[] idbData = (byte[]) variables.get("IDB_APPEND_DATA");
        String objType = (String) variables.get("IDB_OBJ_TYPE");

        F2IdbResponseFdoBuilder f2Builder = new F2IdbResponseFdoBuilder(
            objType, atomGid, idbData.length, idbData);
        String fdoSource = f2Builder.toSource(RenderingContext.DEFAULT);
        List<FdoChunk> responseChunks = fdoCompiler.compileFdoScriptToP3Chunks(
            fdoSource, TOKEN_F2, normalizedStreamId);

        LoggerUtil.debug(String.format(
            logPrefix(username) + "Generated f2 response for GID %s: %d chunks",
            gidDisplay, responseChunks.size()));
        
        LoggerUtil.info(String.format(
            logPrefix(username) + "f2 DOD response generated: totalChunks=%d for GID %s, streamId=0x%04X",
            responseChunks.size(), gidDisplay, normalizedStreamId));

        return new DodResponse(normalizedStreamId, params, responseChunks);
    }

    /**
     * Process an f1 atom stream request.
     * Looks up FDO file in replace_client_fdo/ by GID, compiles it via atomforge,
     * and returns the compiled binary wrapped in an atom stream response.
     *
     * @param ctx Channel context for sending response
     * @param gid The 32-bit GID value extracted from f1 frame
     * @param streamId The Stream ID from the P3 frame header
     * @param username Username for logging context
     * @param platform Client platform for platform-specific asset selection
     * @return AtomStreamResponse containing the Stream ID and compiled response chunks
     * @throws Exception if processing fails
     */
    public AtomStreamResponse processAtomStreamRequest(ChannelHandlerContext ctx, int gid, int streamId, String username, ClientPlatform platform)
            throws Exception {
        LoggerUtil.debug(logPrefix(username) + "Processing f1 atom stream request");

        // Convert integer GID to AOL display format
        String gidDisplay = GidUtils.formatToDisplay(gid);
        int normalizedStreamId = normalizeStreamId(streamId, username);

        LoggerUtil.info(String.format(
            logPrefix(username) + "f1 Atom Stream Request: streamId=0x%04X(%d), gid=%s (%d/0x%08X)",
            normalizedStreamId, normalizedStreamId, gidDisplay, gid, gid));

        // Look for FDO (DSL registry first, then replace_client_fdo/)
        // Use variant resolution for low color mode (checks for .bw.fdo.txt or DSL BW variant)
        boolean lowColorMode = isLowColorModeEnabled(username);
        Optional<String> fdoSource = resolveFdoSource(gidDisplay, lowColorMode, platform, username);

        if (fdoSource.isEmpty()) {
            LoggerUtil.info(String.format(
                logPrefix(username) + "No atom stream FDO found for GID %s",
                gidDisplay));
            return new AtomStreamResponse(normalizedStreamId, gidDisplay, new ArrayList<>(), false);
        }

        LoggerUtil.debug(String.format(
            logPrefix(username) + "Found atom stream FDO for GID %s (platform=%s, lowColor=%s), compiling...",
            gidDisplay, platform, lowColorMode));

        try {
            // Apply button theme variable substitution before compilation
            Map<String, Object> buttonVars = new FdoVariableBuilder()
                .withButtonTheme(properties)
                .buildAsObjects();
            String processedFdo = FdoTemplateEngine.substituteVariables(fdoSource.get(), buttonVars);
            byte[] compiledFdo = fdoCompiler.compileFdoScript(processedFdo);

            LoggerUtil.debug(String.format(
                logPrefix(username) + "Compiled atom stream FDO: %d bytes for GID %s",
                compiledFdo.length, gidDisplay));

            // Build f1 atom stream response using DSL builder
            FdoGid atomGid = parseGidToFdoGid(gidDisplay);
            F1AtomStreamResponseFdoBuilder f1Builder = new F1AtomStreamResponseFdoBuilder(
                atomGid, compiledFdo.length, compiledFdo);
            String f1FdoSource = f1Builder.toSource(RenderingContext.DEFAULT);
            List<FdoChunk> responseChunks = fdoCompiler.compileFdoScriptToP3Chunks(
                f1FdoSource, TOKEN_F1, normalizedStreamId);

            LoggerUtil.info(String.format(
                logPrefix(username) + "f1 Atom stream response generated: chunks=%d for GID %s, streamId=0x%04X",
                responseChunks.size(), gidDisplay, normalizedStreamId));

            return new AtomStreamResponse(normalizedStreamId, gidDisplay, responseChunks, true);

        } catch (Exception e) {
            LoggerUtil.error(String.format(
                logPrefix(username) + "Failed to compile atom stream FDO for GID %s: %s",
                gidDisplay, e.getMessage()));
            return new AtomStreamResponse(normalizedStreamId, gidDisplay, new ArrayList<>(), false);
        }
    }

    /**
     * Process a K1 FDO request.
     * Looks up FDO file in replace_client_fdo/ by GID, wraps it with the K1 response
     * template including the echoed response ID, compiles via atomforge, and returns chunks.
     *
     * @param ctx Channel context for sending response
     * @param gid The 32-bit GID value extracted from K1 frame's de_data
     * @param responseId The response ID from K1's man_set_response_id to echo back
     * @param streamId The Stream ID from the P3 frame header
     * @param username Username for logging context
     * @param platform Client platform for platform-specific asset selection
     * @return K1Response containing the Stream ID and compiled response chunks
     * @throws Exception if processing fails
     */
    public K1Response processK1Request(ChannelHandlerContext ctx, int gid, int responseId, int streamId, String username, ClientPlatform platform)
            throws Exception {
        LoggerUtil.debug(logPrefix(username) + "Processing K1 FDO request");

        // Convert integer GID to AOL display format
        String gidDisplay = GidUtils.formatToDisplay(gid);
        int normalizedStreamId = normalizeStreamId(streamId, username);

        LoggerUtil.info(String.format(
            logPrefix(username) + "K1 Request: streamId=0x%04X(%d), gid=%s (%d/0x%08X), responseId=%d",
            normalizedStreamId, normalizedStreamId, gidDisplay, gid, gid, responseId));

        // Look for FDO (DSL registry first, then replace_client_fdo/)
        // Use variant resolution for low color mode (checks for .bw.fdo.txt or DSL BW variant)
        boolean lowColorMode = isLowColorModeEnabled(username);
        Optional<String> fdoSource = resolveFdoSource(gidDisplay, lowColorMode, platform, username);

        if (fdoSource.isEmpty()) {
            LoggerUtil.info(String.format(
                logPrefix(username) + "No K1 FDO found for GID %s",
                gidDisplay));
            return new K1Response(normalizedStreamId, gidDisplay, responseId, new ArrayList<>(), false);
        }

        LoggerUtil.debug(String.format(
            logPrefix(username) + "Found K1 FDO for GID %s (platform=%s, lowColor=%s), wrapping and compiling...",
            gidDisplay, platform, lowColorMode));

        try {
            // Apply button theme variable substitution to inner FDO before wrapping
            Map<String, Object> buttonVars = new FdoVariableBuilder()
                .withButtonTheme(properties)
                .buildAsObjects();
            String processedInnerFdo = FdoTemplateEngine.substituteVariables(fdoSource.get(), buttonVars);

            // Use DSL builder for K1 response wrapper (replaces fdo/k1_response.fdo.txt)
            K1ResponseFdoBuilder k1Builder = K1ResponseFdoBuilder.wrap(responseId, processedInnerFdo);
            String k1FdoSource = k1Builder.toSource(RenderingContext.DEFAULT);
            List<FdoChunk> responseChunks = fdoCompiler.compileFdoScriptToP3Chunks(
                k1FdoSource,
                TOKEN_K1,
                normalizedStreamId
            );

            LoggerUtil.info(String.format(
                logPrefix(username) + "K1 response generated: chunks=%d for GID %s, responseId=%d, streamId=0x%04X",
                responseChunks.size(), gidDisplay, responseId, normalizedStreamId));

            return new K1Response(normalizedStreamId, gidDisplay, responseId, responseChunks, true);

        } catch (Exception e) {
            LoggerUtil.error(String.format(
                logPrefix(username) + "Failed to compile K1 FDO for GID %s: %s",
                gidDisplay, e.getMessage()));
            return new K1Response(normalizedStreamId, gidDisplay, responseId, new ArrayList<>(), false);
        }
    }

    /**
     * Load FDO source from a resource path.
     *
     * @param resourcePath Path to the FDO resource file
     * @return Optional containing the FDO source, or empty if not found
     */
    private Optional<String> loadFdoResource(String resourcePath) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return Optional.empty();
            }
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return Optional.of(content);
        } catch (IOException e) {
            LoggerUtil.error("Failed to load FDO resource: " + resourcePath + " - " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Load FDO source with black-and-white variant resolution.
     * When lowColorMode is true, checks for a .bw.fdo.txt variant first.
     *
     * @param resourcePath Path to the FDO resource file
     * @param lowColorMode If true, prefer .bw.fdo.txt variant when available
     * @return Optional containing the FDO source, or empty if not found
     */
    private Optional<String> loadFdoResourceWithVariant(String resourcePath, boolean lowColorMode) {
        String resolvedPath = FdoTemplateEngine.resolveVariantPath(resourcePath, lowColorMode);
        return loadFdoResource(resolvedPath);
    }

    /**
     * Resolve FDO source for a GID, checking DSL registry first then falling back to filesystem.
     *
     * <p>This method enables programmatic FDO generation via DSL builders while maintaining
     * backward compatibility with text-based templates in the replace_client_fdo/ directory.</p>
     *
     * <p>Resolution order:</p>
     * <ol>
     *   <li>Check DSL registry for a builder registered for this GID</li>
     *   <li>If found, call builder's toSource(RenderingContext) with platform + color mode</li>
     *   <li>If not found, fall back to filesystem template with variant resolution</li>
     * </ol>
     *
     * @param gid The GID to resolve (display format, e.g., "69-420")
     * @param lowColorMode If true, prefer BW variant (DSL or filesystem)
     * @param platform Client platform for platform-specific rendering
     * @param username Username for logging context
     * @return Optional containing the FDO source, or empty if not found
     */
    private Optional<String> resolveFdoSource(String gid, boolean lowColorMode,
                                               ClientPlatform platform, String username) {
        // Step 1: Check DSL registry first (preferred path)
        FdoDslRegistry registry = FdoDslRegistry.getInstance();
        Optional<FdoDslBuilder> builder = registry.getBuilder(gid);

        if (builder.isPresent()) {
            FdoDslBuilder dslBuilder = builder.get();
            LoggerUtil.debug(String.format(
                logPrefix(username) + "Using DSL builder for GID %s: %s (platform=%s, lowColor=%s)",
                gid, dslBuilder.getClass().getSimpleName(), platform, lowColorMode));

            // Create rendering context with platform and color mode
            RenderingContext ctx = new RenderingContext(platform, lowColorMode);
            String fdoSource = dslBuilder.toSource(ctx);
            return Optional.of(fdoSource);
        }

        // Step 2: Fallback to filesystem template with variant resolution
        // Note: Filesystem templates only support BW variants, not platform-specific
        String fdoResourcePath = "replace_client_fdo/" + gid + ".fdo.txt";
        return loadFdoResourceWithVariant(fdoResourcePath, lowColorMode);
    }

    /**
     * Extract Stream ID from FDO using native FdoStream API.
     * The Stream ID is the value in uni_start_stream atom.
     */
    private int extractStreamIdFromFdo(byte[] fhFrame) {
        try {
            // Strip P3 header to get pure FDO binary (12-byte header + optional 0x0D terminator)
            byte[] fdoBinary = FdoStreamExtractor.stripP3Header(fhFrame);

            if (fdoBinary == null || fdoBinary.length == 0) {
                LoggerUtil.debug(() -> "No FDO payload in frame");
                return 0;
            }

            // Use native FdoStream extraction
            Integer streamId = FdoStreamExtractor.extractStreamId(fdoBinary);
            if (streamId != null) {
                LoggerUtil.info("Found Stream ID in uni_start_stream: 0x" +
                    Integer.toHexString(streamId) + " (" + streamId + ")");
                return streamId;
            }

            LoggerUtil.debug(() -> "No Stream ID found in FDO content");
            return 0;

        } catch (Exception e) {
            LoggerUtil.error("Failed to extract Stream ID from FDO: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Extract DOD parameters from the request FDO using native FdoStream API.
     * Supports both single-GID and multi-GID formats.
     */
    private DodRequestParams extractDodParameters(byte[] fhFrame) {
        // Strip P3 header to get pure FDO binary (12-byte header + optional 0x0D terminator)
        byte[] fdoBinary = FdoStreamExtractor.stripP3Header(fhFrame);

        LoggerUtil.info(String.format("extractDodParameters: fhFrame=%d bytes, fdoBinary=%d bytes after strip",
            fhFrame.length, fdoBinary != null ? fdoBinary.length : 0));

        if (fdoBinary == null || fdoBinary.length == 0) {
            LoggerUtil.warn("No FDO payload in fh frame for DOD parameters extraction");
            DodRequestParams params = new DodRequestParams();
            params.gidPairs = new ArrayList<>();
            return params;
        }

        // Diagnostic: dump first 20 bytes of FDO binary
        StringBuilder fdoHex = new StringBuilder();
        for (int i = 0; i < Math.min(20, fdoBinary.length); i++) {
            if (i > 0) fdoHex.append(" ");
            fdoHex.append(String.format("%02X", fdoBinary[i] & 0xFF));
        }
        LoggerUtil.info("FDO binary (first 20 bytes): " + fdoHex);

        // Use native FdoStream extraction
        FdoStreamExtractor.DodParameters extracted = FdoStreamExtractor.extractDodParameters(fdoBinary);
        LoggerUtil.info(String.format("Native extraction result: formId=%s, gidPairs=%d",
            extracted.formId, extracted.gidPairs != null ? extracted.gidPairs.size() : 0));

        // Convert to local DodRequestParams format
        DodRequestParams params = new DodRequestParams();
        params.formId = extracted.formId;
        params.gidPairs = new ArrayList<>();

        for (FdoStreamExtractor.GidTransactionPair pair : extracted.gidPairs) {
            params.gidPairs.add(new GidTransactionPair(pair.gid, pair.transactionId));
            LoggerUtil.info(String.format("Extracted GID pair: transactionId=%s, gid=%s", pair.transactionId, pair.gid));
        }

        return params;
    }

    /**
     * Generate DOD response FDO chunks for a SINGLE GID-transaction pair.
     * This method is called once per GID in multi-GID requests.
     */
    private List<FdoChunk> generateDodResponseForSingleGid(
            GidTransactionPair pair,
            String formId,
            int streamId,
            String username,
            DodRequestType requestType,
            ClientPlatform platform) throws FdoCompilationException, java.io.IOException {

        int effectiveStreamId = streamId;
        if (effectiveStreamId == 0) {
            effectiveStreamId = 0x2100; // Default to 8448
            LoggerUtil.warn(logPrefix(username) + "Using default Stream ID 0x2100 as extraction failed");
        }

        Optional<ArtPayload> artPayload = loadArtPayload(pair.gid, username, platform);
        if (artPayload.isEmpty()) {
            LoggerUtil.info(logPrefix(username) + "Art payload unavailable for gid=" + pair.gid
                    + " - sending fallback response");
            return compileNotAvailableResponse(effectiveStreamId, requestType, username, pair.gid);
        }

        ArtPayload payload = artPayload.get();

        if (requestType == DodRequestType.FH_FORM) {
            // Build DOD response using DSL builder
            LoggerUtil.debug(String.format(
                logPrefix(username) + "Compiling FH DOD response: streamId=0x%04X(%d), formId=%s, gid=%s, transactionId=%s",
                effectiveStreamId, effectiveStreamId, formId, pair.gid, pair.transactionId
            ));

            // Parse transaction ID to int (default to 0 if null/invalid)
            int txId = 0;
            if (pair.transactionId != null) {
                try {
                    txId = Integer.parseInt(pair.transactionId);
                } catch (NumberFormatException e) {
                    LoggerUtil.warn(logPrefix(username) + "Invalid transaction ID: " + pair.transactionId);
                }
            }

            FdoGid gid = parseGidToFdoGid(pair.gid);
            DodResponseFdoBuilder dodBuilder = new DodResponseFdoBuilder(txId, formId, gid, payload.dodData);
            String dodFdoSource = dodBuilder.toSource(RenderingContext.DEFAULT);
            List<FdoChunk> responseChunks = fdoCompiler.compileFdoScriptToP3Chunks(
                dodFdoSource, TOKEN_STANDARD, effectiveStreamId);

            LoggerUtil.debug(String.format(
                logPrefix(username) + "FH DOD compiled: chunks=%d for streamId=0x%04X, gid=%s",
                responseChunks.size(), effectiveStreamId, pair.gid));

            return responseChunks;
        } else {
            // Build f2 IDB response using DSL builder
            LoggerUtil.debug(String.format(
                logPrefix(username) + "Compiling f2 IDB response: streamId=0x%04X(%d), gid=%s",
                effectiveStreamId, effectiveStreamId, pair.gid
            ));

            FdoGid gid = parseGidToFdoGid(pair.gid);
            F2IdbResponseFdoBuilder f2Builder = new F2IdbResponseFdoBuilder(
                "p", gid, payload.idbData.length, payload.idbData);
            String f2FdoSource = f2Builder.toSource(RenderingContext.DEFAULT);
            return fdoCompiler.compileFdoScriptToP3Chunks(
                f2FdoSource, TOKEN_F2, effectiveStreamId);
        }
    }

    /**
     * Generate IDB download for a specific art ID.
     * This method allows triggering IDB art downloads independently without a full DOD request.
     * Useful for preloading art, resetting art cache, or on-demand art downloads.
     *
     * @param artId The mat_art_id to download (e.g., "1-0-21864")
     * @param platform Client platform for platform-specific asset selection
     * @return List of compiled FDO chunks ready to send
     * @throws Exception if art loading or compilation fails
     */
    public List<FdoChunk> generateIdbDownload(String artId, ClientPlatform platform) throws Exception {
        LoggerUtil.info(String.format("Generating IDB download for art ID: %s (platform: %s)", artId, platform));

        byte[] artBytes;
        try {
            // Load raw art bytes
            artBytes = artService.getArtAsBytes(artId, platform);
            LoggerUtil.debug(String.format(
                "Art data loaded for IDB download: artId=%s, bytes=%d",
                artId, artBytes.length));
        } catch (Exception e) {
            LoggerUtil.error(String.format(
                "Failed to load art for IDB download: artId=%s, error=%s",
                artId, e.getMessage()));
            // Use placeholder art data on error
            artBytes = new byte[]{0x00};
        }

        // Build IDB response using DSL builder
        FdoGid gid = parseGidToFdoGid(artId);
        F2IdbResponseFdoBuilder f2Builder = new F2IdbResponseFdoBuilder(
            "p", gid, artBytes.length, artBytes);
        String fdoSource = f2Builder.toSource(RenderingContext.DEFAULT);

        LoggerUtil.info(String.format(
            "Compiling IDB response: artId=%s", artId));

        // Compile IDB response using DSL source
        List<FdoChunk> idbChunks = fdoCompiler.compileFdoScriptToP3Chunks(
            fdoSource, "AT", FdoCompiler.AUTO_GENERATE_STREAM_ID);

        LoggerUtil.info(String.format(
            "IDB download generated: artId=%s, chunks=%d",
            artId, idbChunks.size()));

        return idbChunks;
    }

    /**
     * Generate an IDB reset+send response for an atom stream.
     * This deletes the existing IDB object and sends the new compiled atom stream data.
     * Used for proactive preloading of welcome screen atom streams during login.
     *
     * @param gid      The atom stream GID (e.g., "32-117")
     * @param username Username for logging and low color mode detection
     * @param platform Client platform for platform-specific rendering
     * @return List of compiled FDO chunks ready to send
     * @throws Exception if FDO loading or compilation fails
     */
    public List<FdoChunk> generateIdbAtomStreamResetAndSend(String gid, String username, ClientPlatform platform) throws Exception {
        LoggerUtil.info(logPrefix(username) + "Generating IDB atom stream reset+send for GID: " + gid);

        // 1. Load FDO source (DSL registry first, then replace_client_fdo/{gid}.fdo.txt)
        boolean lowColorMode = isLowColorModeEnabled(username);
        Optional<String> fdoSource = resolveFdoSource(gid, lowColorMode, platform, username);

        if (fdoSource.isEmpty()) {
            throw new IllegalArgumentException("No atom stream FDO found for GID: " + gid);
        }

        // 2. Apply button theme variables and compile FDO to binary (same pattern as f1 atom stream)
        Map<String, Object> buttonVars = new FdoVariableBuilder()
            .withButtonTheme(properties)
            .buildAsObjects();
        String processedFdo = FdoTemplateEngine.substituteVariables(fdoSource.get(), buttonVars);
        byte[] compiledFdo = fdoCompiler.compileFdoScript(processedFdo);

        LoggerUtil.debug(logPrefix(username) + String.format(
            "Compiled atom stream FDO: %d bytes for GID %s", compiledFdo.length, gid));

        // 3. Parse GID and use DSL builder for idb_reset_and_send (replaces fdo/idb_reset_and_send.fdo.txt)
        FdoGid atomGid = parseGidToFdoGid(gid);
        IdbResetAndSendFdoBuilder idbBuilder = IdbResetAndSendFdoBuilder.reset("a", atomGid, compiledFdo);
        String idbFdoSource = idbBuilder.toSource(RenderingContext.DEFAULT);

        // 4. Compile and return chunks
        List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(
            idbFdoSource,
            "AT",
            FdoCompiler.AUTO_GENERATE_STREAM_ID
        );

        LoggerUtil.info(logPrefix(username) + String.format(
            "IDB reset+send generated: chunks=%d for GID %s", chunks.size(), gid));

        // 5. Track cumulative IDB updates and detect corruption drift
        trackIdbUpdate(gid, compiledFdo, username);

        return chunks;
    }

    private Optional<ArtPayload> loadArtPayload(String gid, String username, ClientPlatform platform) {
        if (!isMatArtId(gid)) {
            LoggerUtil.warn(logPrefix(username) + "GID does not match mat_art_id pattern: " + gid);
            return Optional.empty();
        }

        try {
            // Load raw art bytes - FdoTemplateEngine handles hex conversion automatically
            byte[] artBytes = artService.getArtAsBytes(gid, platform);

            LoggerUtil.info(String.format(
                logPrefix(username) + "Art data loaded for gid=%s: %d bytes",
                gid, artBytes.length));

            // Same bytes used for both DOD and IDB responses
            return Optional.of(new ArtPayload(artBytes, artBytes));
        } catch (Exception e) {
            LoggerUtil.error(String.format(
                logPrefix(username) + "Failed to load art for gid=%s: %s",
                gid, e.getMessage()));
            return Optional.empty();
        }
    }

    private int normalizeStreamId(int streamId, String username) {
        if (streamId == 0) {
            LoggerUtil.warn(logPrefix(username) + "Stream ID missing - defaulting to 0x2100");
            return 0x2100;
        }
        return streamId;
    }

    /**
     * Parse a GID display string (e.g., "69-420" or "1-0-1333") into an FdoGid object.
     *
     * @param gid GID in display format
     * @return FdoGid object
     */
    private FdoGid parseGidToFdoGid(String gid) {
        if (gid == null || gid.isEmpty()) {
            return FdoGid.of(0, 0);
        }
        String[] parts = gid.split("-");
        try {
            if (parts.length == 2) {
                return FdoGid.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            } else if (parts.length == 3) {
                return FdoGid.of(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
                );
            }
        } catch (NumberFormatException e) {
            LoggerUtil.warn("Failed to parse GID: " + gid);
        }
        return FdoGid.of(0, 0);
    }

    private List<FdoChunk> compileNotAvailableResponse(int streamId, DodRequestType requestType, String username, String gid)
            throws FdoCompilationException, java.io.IOException {
        LoggerUtil.debug(String.format("%sCompiling not-available response | streamId=0x%04X gid=%s type=%s",
                logPrefix(username), streamId, gid, requestType));

        String resolvedGid = (gid != null && !gid.isEmpty()) ? gid : "0-0";

        if (requestType == DodRequestType.F2_DIRECT) {
            // Build f2 IDB response with empty placeholder data
            FdoGid atomGid = parseGidToFdoGid(resolvedGid);
            byte[] placeholder = new byte[]{0x00};
            F2IdbResponseFdoBuilder f2Builder = new F2IdbResponseFdoBuilder(
                "p", atomGid, placeholder.length, placeholder);
            String fdoSource = f2Builder.toSource(RenderingContext.DEFAULT);
            return fdoCompiler.compileFdoScriptToP3Chunks(fdoSource, TOKEN_F2, streamId);
        }

        // Use static DOD not available builder
        String fdoSource = DodNotAvailableFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);
        return fdoCompiler.compileFdoScriptToP3Chunks(fdoSource, TOKEN_STANDARD, streamId);
    }

    /**
     * Check if a GID looks like a mat_art_id (format: "1-0-34196").
     */
    private boolean isMatArtId(String gid) {
        if (gid == null) {
            return false;
        }

        // Pattern: number-number-number (e.g., "1-0-34196")
        Pattern matArtIdPattern = Pattern.compile("^\\d+-\\d+-\\d+$");
        return matArtIdPattern.matcher(gid).matches();
    }

    /**
     * Holds raw art bytes for DOD and IDB responses.
     * The FdoTemplateEngine will auto-convert byte[] to FDO hex format.
     */
    private static class ArtPayload {
        final byte[] dodData;
        final byte[] idbData;

        ArtPayload(byte[] dodData, byte[] idbData) {
            this.dodData = dodData;
            this.idbData = idbData;
        }
    }

    /**
     * Pairs a GID with its transaction ID for multi-GID DOD requests.
     */
    public static class GidTransactionPair {
        public String gid;
        public String transactionId;

        public GidTransactionPair(String gid, String transactionId) {
            this.gid = gid;
            this.transactionId = transactionId;
        }

        @Override
        public String toString() {
            return String.format("{gid='%s', txId='%s'}", gid, transactionId);
        }
    }

    /**
     * Parameters extracted from a DOD request.
     */
    public static class DodRequestParams {
        public String formId;
        public List<GidTransactionPair> gidPairs;

        public boolean hasMultipleGids() {
            return gidPairs != null && gidPairs.size() > 1;
        }

        public boolean hasGids() {
            return gidPairs != null && !gidPairs.isEmpty();
        }

        @Override
        public String toString() {
            if (gidPairs == null || gidPairs.isEmpty()) {
                return String.format("DodRequestParams{formId='%s', gids=[]}", formId);
            }
            return String.format("DodRequestParams{formId='%s', gidCount=%d, gids=%s}",
                    formId, gidPairs.size(), gidPairs);
        }
    }

    /**
     * Response data for a DOD request.
     */
    public static class DodResponse {
        public final int streamId;
        public final DodRequestParams requestParams;
        public final List<FdoChunk> responseChunks;

        public DodResponse(int streamId, DodRequestParams requestParams, List<FdoChunk> responseChunks) {
            this.streamId = streamId;
            this.requestParams = requestParams;
            this.responseChunks = responseChunks;
        }
    }

    /**
     * Response data for an f1 atom stream request.
     */
    public static class AtomStreamResponse {
        public final int streamId;
        public final String gid;
        public final List<FdoChunk> responseChunks;
        public final boolean found;

        public AtomStreamResponse(int streamId, String gid, List<FdoChunk> responseChunks, boolean found) {
            this.streamId = streamId;
            this.gid = gid;
            this.responseChunks = responseChunks;
            this.found = found;
        }
    }

    /**
     * Response data for a K1 FDO request.
     */
    public static class K1Response {
        public final int streamId;
        public final String gid;
        public final int responseId;
        public final List<FdoChunk> responseChunks;
        public final boolean found;

        public K1Response(int streamId, String gid, int responseId, List<FdoChunk> responseChunks, boolean found) {
            this.streamId = streamId;
            this.gid = gid;
            this.responseId = responseId;
            this.responseChunks = responseChunks;
            this.found = found;
        }
    }

    // ========== IDB Shadow Comparison and Corruption Tracking ==========

    /**
     * Track cumulative IDB updates for a GID and detect if compiled output drifts.
     * Stores first compilation as reference and logs warnings if subsequent compilations differ.
     */
    private void trackIdbUpdate(String gid, byte[] compiledFdo, String username) {
        int updateCount = idbUpdateCounts.merge(gid, 1, Integer::sum);

        if (updateCount == 1) {
            // Store first compilation as reference
            firstIdbCompilation.put(gid, compiledFdo.clone());
            LoggerUtil.debug(logPrefix(username) + String.format(
                "[IDB-Track] First update for %s stored as reference (%d bytes)",
                gid, compiledFdo.length));
        } else {
            // Compare with first compilation
            byte[] reference = firstIdbCompilation.get(gid);
            if (reference != null) {
                if (Arrays.equals(reference, compiledFdo)) {
                    LoggerUtil.debug(logPrefix(username) + String.format(
                        "[IDB-Track] Update #%d for %s matches reference (%d bytes)",
                        updateCount, gid, compiledFdo.length));
                } else {
                    // DRIFT DETECTED - this is critical information
                    LoggerUtil.warn(logPrefix(username) + String.format(
                        "[IDB-Track] DRIFT DETECTED for %s on update #%d | ref=%d bytes, now=%d bytes, diff=%d",
                        gid, updateCount, reference.length, compiledFdo.length,
                        compiledFdo.length - reference.length));
                    logByteDiff(gid, reference, compiledFdo, username);
                }
            }
        }
    }

    /**
     * Log byte-level differences between two byte arrays.
     */
    private void logByteDiff(String gid, byte[] reference, byte[] current, String username) {
        int minLen = Math.min(reference.length, current.length);
        int maxLen = Math.max(reference.length, current.length);

        StringBuilder diffLog = new StringBuilder();
        diffLog.append(logPrefix(username)).append("[IDB-Track] Byte diff for ").append(gid).append(":\n");

        // Find first difference
        int firstDiff = -1;
        for (int i = 0; i < minLen; i++) {
            if (reference[i] != current[i]) {
                firstDiff = i;
                break;
            }
        }

        if (firstDiff == -1 && reference.length != current.length) {
            firstDiff = minLen;  // Difference is in length
        }

        if (firstDiff >= 0) {
            diffLog.append("  First difference at offset ").append(firstDiff).append("\n");

            // Show context around first difference (10 bytes before and after)
            int start = Math.max(0, firstDiff - 10);
            int end = Math.min(maxLen, firstDiff + 20);

            diffLog.append("  Reference [").append(start).append("-").append(Math.min(end, reference.length)).append("]: ");
            for (int i = start; i < Math.min(end, reference.length); i++) {
                diffLog.append(String.format("%02X ", reference[i] & 0xFF));
            }
            diffLog.append("\n");

            diffLog.append("  Current   [").append(start).append("-").append(Math.min(end, current.length)).append("]: ");
            for (int i = start; i < Math.min(end, current.length); i++) {
                diffLog.append(String.format("%02X ", current[i] & 0xFF));
            }
            diffLog.append("\n");

            // Count total differences
            int diffCount = 0;
            for (int i = 0; i < minLen; i++) {
                if (reference[i] != current[i]) diffCount++;
            }
            diffCount += Math.abs(reference.length - current.length);
            diffLog.append("  Total different bytes: ").append(diffCount);
        }

        LoggerUtil.warn(diffLog.toString());
    }

}