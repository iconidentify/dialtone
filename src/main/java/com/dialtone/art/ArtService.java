/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.art;

import com.dialtone.protocol.ClientPlatform;
import com.dialtone.utils.LoggerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Service for loading and processing art assets.
 * Loads PNG, JPEG, or GIF images and JSON metadata from resources/art/ directory,
 * converts to GIF87a format with AOL protocol header.
 *
 * <p>Returns raw bytes that can be passed to FdoTemplateEngine with _DATA suffix
 * variables for automatic hex conversion. The native FDO compiler handles
 * chunking automatically via UNI continuation protocol.</p>
 */
public class ArtService {

    private static final String ART_BASE_PATH = "/art/";
    /**
     * Supported image extensions in priority order for pass-through formats.
     * Format priority: GIF > BMP > ART
     * PNG/JPG/JPEG are only used for JSON-metadata based processing.
     */
    private static final String[] PASSTHROUGH_IMAGE_EXTENSIONS = {".gif", ".bmp", ".art"};

    /**
     * All supported image extensions (for JSON-metadata based processing).
     */
    private static final String[] SUPPORTED_IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".gif", ".bmp", ".art"};

    /**
     * Search paths for art resources.
     * Directory priority: higher in directory structure wins.
     * Root path is searched first (highest priority), then subdirectories.
     */
    private static final String[] ART_SEARCH_PATHS = {
        "/art/",                           // Root directory (highest priority - wins over all)
        "/art/alpha/",                     // Alpha subdirectory (2nd priority)
        "/art/extracted/gifs_extracted/",  // Extracted GIFs (3rd priority)
        "/art/extracted/bmp_extracted/",   // Extracted BMPs (3rd priority)
        "/art/extracted/art_extracted/",   // Extracted ART files (3rd priority)
    };

    private final ObjectMapper objectMapper;

    public ArtService() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Find an art resource by searching through all art paths.
     * Searches root directory first (priority), then subdirectories.
     *
     * @param matArtId Art ID (filename without extension)
     * @param extension File extension (e.g., ".png", ".json")
     * @return Full resource path if found, null otherwise
     */
    private String findArtResource(String matArtId, String extension) {
        return findArtResource(matArtId, extension, null);
    }

    /**
     * Find an art resource by searching through all art paths, with platform-aware variants.
     * For Windows clients requesting GIF files, checks for _opaque variant first.
     * Searches root directory first (priority), then subdirectories.
     *
     * @param matArtId Art ID (filename without extension)
     * @param extension File extension (e.g., ".png", ".json")
     * @param platform Client platform (for platform-specific asset selection)
     * @return Full resource path if found, null otherwise
     */
    private String findArtResource(String matArtId, String extension, ClientPlatform platform) {
        // For Windows clients requesting GIF files, check for _opaque variant first
        if (platform == ClientPlatform.WINDOWS && ".gif".equals(extension)) {
            String opaqueVariant = findResourceInPaths(matArtId + "_opaque", extension);
            if (opaqueVariant != null) {
                LoggerUtil.debug(String.format(
                    "Using opaque GIF variant for Windows client: %s_opaque%s", matArtId, extension));
                return opaqueVariant;
            }
        }

        // Fall back to standard resource
        return findResourceInPaths(matArtId, extension);
    }

    /**
     * Search all art paths for a resource.
     *
     * @param matArtId Art ID (filename without extension)
     * @param extension File extension
     * @return Full resource path if found, null otherwise
     */
    private String findResourceInPaths(String matArtId, String extension) {
        for (String searchPath : ART_SEARCH_PATHS) {
            String resourcePath = searchPath + matArtId + extension;
            if (getClass().getResource(resourcePath) != null) {
                return resourcePath;
            }
        }
        return null;
    }

    /**
     * Create default metadata for images without JSON files.
     * Provides sensible defaults that keep GIF files under size limits:
     * - Max size: 256×192 (4:3 aspect ratio, aspect ratio preserved during resize)
     * - Dithering: enabled (better quality)
     * - Transparency: disabled
     * - Posterization: disabled
     *
     * Default dimensions are chosen to keep GIF files under protocol header limits:
     * - 256×192 = 49,152 pixels → estimated GIF size: 15KB-39KB
     * - Stays well under 32KB signed short limit (avoids Size B overflow warnings)
     * - For larger images, create a JSON file with custom dimensions
     *
     * @return Default ArtMetadata instance
     */
    private ArtMetadata createDefaultMetadata() {
        ArtMetadata metadata = new ArtMetadata();
        metadata.setWidth(380);           // Max width constraint (safe for GIF size limits)
        metadata.setHeight(380);          // Max height constraint (4:3 ratio, safe for GIF size limits)
        metadata.setTransparency(false);  // No transparency for default processing
        metadata.setEnableDithering(true);     // Enable dithering for better quality
        metadata.setEnablePosterization(false); // No posterization
        metadata.setPosterizationLevel(32);     // Default level (not used when posterization disabled)
        return metadata;
    }

    /**
     * Load art asset and convert to raw bytes with AOL protocol header.
     *
     * @param matArtId The mat_art_id (e.g., "1-0-34196")
     * @return Raw bytes (protocol header + image data) for FdoTemplateEngine
     * @throws IOException if asset loading or conversion fails
     */
    public byte[] getArtAsBytes(String matArtId) throws IOException {
        return getArtAsBytes(matArtId, null);
    }

    /**
     * Load art asset and convert to raw bytes with AOL protocol header, platform-aware.
     * For Windows clients, automatically uses _opaque GIF variants when available.
     *
     * @param matArtId The mat_art_id (e.g., "1-0-34196")
     * @param platform Client platform (for platform-specific asset selection)
     * @return Raw bytes (protocol header + image data) for FdoTemplateEngine
     * @throws IOException if asset loading or conversion fails
     */
    public byte[] getArtAsBytes(String matArtId, ClientPlatform platform) throws IOException {
        LoggerUtil.debug(String.format("Loading art asset: %s", matArtId));

        // Check if art exists, fall back to default if not
        if (!artExists(matArtId)) {
            throw new IOException("Art asset not found: " + matArtId);
        }
        String actualMatArtId = matArtId;

        // Check if JSON metadata exists for this asset (search all art paths)
        String jsonPath = findArtResource(actualMatArtId, ".json");
        boolean hasJson = (jsonPath != null);

        // Determine image type for handling without JSON
        String imagePath = findImagePath(actualMatArtId, platform);
        String extension = null;
        for (String ext : SUPPORTED_IMAGE_EXTENSIONS) {
            if (imagePath.endsWith(ext)) {
                extension = ext;
                break;
            }
        }

        // Route based on whether JSON metadata exists
        if (!hasJson) {
            // GIF without JSON: pass through without processing
            if (".gif".equals(extension)) {
                LoggerUtil.debug(String.format(
                        "No JSON metadata found for %s - using GIF pass-through mode", actualMatArtId));
                return getArtAsBytesPassthrough(actualMatArtId, platform);
            }

            // BMP without JSON: pass through without processing
            if (".bmp".equals(extension)) {
                LoggerUtil.debug(String.format(
                        "No JSON metadata found for %s - using BMP pass-through mode", actualMatArtId));
                return getArtAsBytesBmpPassthrough(actualMatArtId);
            }

            // ART without JSON: pass through without processing
            if (".art".equals(extension)) {
                LoggerUtil.debug(String.format(
                        "No JSON metadata found for %s - using ART pass-through mode", actualMatArtId));
                return getArtAsBytesArtPassthrough(actualMatArtId);
            }

            // PNG/JPG/JPEG without JSON: use default processing (640x480 constraint)
            LoggerUtil.debug(String.format(
                    "No JSON metadata for %s - using default processing (max 640x480, dithering enabled)",
                    actualMatArtId));

            BufferedImage image = loadImage(actualMatArtId);
            ArtMetadata metadata = createDefaultMetadata();

            LoggerUtil.debug(() -> String.format(
                    "Using default metadata: %s", metadata));

            // Resize with aspect ratio preservation to 640x480 bounds
            BufferedImage resized = GifEncoder.resize(image, metadata.getWidth(), metadata.getHeight());

            // Quantize with dithering enabled
            BufferedImage quantized = GifEncoder.quantizeTo256ColorsAdaptive(
                resized,
                metadata.isEnableDithering(),
                metadata.isEnablePosterization(),
                metadata.getPosterizationLevel()
            );

            // Encode to GIF87a with automatic size limiting (includes AOL protocol header)
            byte[] gifBytes = GifEncoder.encodeWithSizeLimit(quantized, metadata);

            LoggerUtil.debug(String.format(
                    "Art asset converted to GIF87a with default settings: %s, %d bytes (with size limit enforcement)",
                    actualMatArtId, gifBytes.length));

            return gifBytes;
        }

        // Standard processing path: JSON metadata exists
        // Load image (PNG, JPEG, or GIF)
        BufferedImage image = loadImage(actualMatArtId);

        // Load JSON metadata
        ArtMetadata metadata = loadMetadata(actualMatArtId);

        LoggerUtil.debug(() -> String.format(
                "Art metadata loaded: %s", metadata));

        // Resize image to target dimensions (do this BEFORE quantization for better palette generation)
        BufferedImage resized = GifEncoder.resize(image, metadata.getWidth(), metadata.getHeight());

        // Quantize to 256 colors using adaptive palette with optional dithering/posterization
        BufferedImage quantized = GifEncoder.quantizeTo256ColorsAdaptive(
            resized,
            metadata.isEnableDithering(),
            metadata.isEnablePosterization(),
            metadata.getPosterizationLevel()
        );

        // Encode to GIF87a with metadata (includes flag bytes) and automatic size limiting
        byte[] gifBytes = GifEncoder.encodeWithSizeLimit(quantized, metadata);

        LoggerUtil.debug(String.format(
                "Art asset converted to GIF87a: %s, %d bytes (with size limit enforcement)",
                actualMatArtId, gifBytes.length));

        return gifBytes;
    }

    /**
     * Find the image file path for a given mat_art_id.
     * Priority order:
     * 1. Directory priority: higher directories win (e.g., /art/ beats /art/extracted/)
     * 2. Within same directory, format priority: GIF > BMP > ART
     *
     * @param matArtId The mat_art_id to search for
     * @return The resource path to the image file
     * @throws IOException if no image file is found
     */
    private String findImagePath(String matArtId) throws IOException {
        return findImagePath(matArtId, null);
    }

    /**
     * Find the image file path for a given mat_art_id, with platform awareness.
     * Priority order:
     * 1. Directory priority: higher directories win (e.g., /art/ beats /art/extracted/)
     * 2. Within same directory, format priority: GIF (with _opaque for Windows) > BMP > ART
     * 3. Fallback to PNG/JPG/JPEG for JSON-metadata based processing
     *
     * @param matArtId The mat_art_id to search for
     * @param platform Client platform (for platform-specific asset selection)
     * @return The resource path to the image file
     * @throws IOException if no image file is found
     */
    private String findImagePath(String matArtId, ClientPlatform platform) throws IOException {
        // First pass: Search for pass-through formats (GIF > BMP > ART) with directory priority
        for (String searchPath : ART_SEARCH_PATHS) {
            // Within each directory, check formats in priority order
            for (String ext : PASSTHROUGH_IMAGE_EXTENSIONS) {
                String resourcePath;

                // For GIF on Windows, check _opaque variant first
                if (platform == ClientPlatform.WINDOWS && ".gif".equals(ext)) {
                    resourcePath = searchPath + matArtId + "_opaque" + ext;
                    if (getClass().getResource(resourcePath) != null) {
                        LoggerUtil.debug(String.format(
                            "Found opaque GIF variant for Windows: %s", resourcePath));
                        return resourcePath;
                    }
                }

                // Check standard file
                resourcePath = searchPath + matArtId + ext;
                if (getClass().getResource(resourcePath) != null) {
                    return resourcePath;
                }
            }
        }

        // Second pass: Fallback to PNG/JPG/JPEG for JSON-metadata based processing
        for (String ext : new String[]{".png", ".jpg", ".jpeg"}) {
            String path = findArtResource(matArtId, ext, platform);
            if (path != null) {
                return path;
            }
        }

        throw new IOException("Image file not found for: " + matArtId +
            " (tried extensions: .gif, .bmp, .art, .png, .jpg, .jpeg in all art paths)");
    }

    /**
     * Load image from resources (supports PNG, JPEG, GIF).
     * ImageIO automatically detects the format.
     */
    private BufferedImage loadImage(String matArtId) throws IOException {
        String imagePath = findImagePath(matArtId);

        try (InputStream inputStream = getClass().getResourceAsStream(imagePath)) {
            if (inputStream == null) {
                throw new IOException("Image file not found: " + imagePath);
            }

            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new IOException("Failed to read image: " + imagePath);
            }

            LoggerUtil.debug(() -> String.format(
                    "Loaded image: %s (%dx%d)",
                    imagePath, image.getWidth(), image.getHeight()));

            return image;
        }
    }

    /**
     * Load JSON metadata from resources.
     * Searches all art paths (root + subdirectories).
     */
    private ArtMetadata loadMetadata(String matArtId) throws IOException {
        String jsonPath = findArtResource(matArtId, ".json");

        if (jsonPath == null) {
            throw new IOException("JSON metadata file not found for: " + matArtId +
                " (searched all art paths)");
        }

        try (InputStream inputStream = getClass().getResourceAsStream(jsonPath)) {
            if (inputStream == null) {
                throw new IOException("JSON metadata file not found: " + jsonPath);
            }

            ArtMetadata metadata = objectMapper.readValue(inputStream, ArtMetadata.class);

            // Validate metadata
            if (metadata.getWidth() <= 0 || metadata.getHeight() <= 0) {
                throw new IOException("Invalid dimensions in metadata: " + metadata);
            }

            return metadata;
        }
    }

    /**
     * Extract dimensions from a GIF file's logical screen descriptor.
     * GIF format: bytes 6-7 = width (little-endian), bytes 8-9 = height (little-endian)
     *
     * @param gifBytes The raw GIF data
     * @return Array of [width, height]
     * @throws IOException if GIF header is invalid or too short
     */
    private int[] extractGifDimensions(byte[] gifBytes) throws IOException {
        if (gifBytes.length < 10) {
            throw new IOException("GIF file too short (< 10 bytes)");
        }

        // Verify GIF87a or GIF89a header
        if (!(gifBytes[0] == 'G' && gifBytes[1] == 'I' && gifBytes[2] == 'F' &&
              gifBytes[3] == '8' && (gifBytes[4] == '7' || gifBytes[4] == '9') && gifBytes[5] == 'a')) {
            throw new IOException("Invalid GIF header");
        }

        // Extract width and height (little-endian, bytes 6-9)
        int width = (gifBytes[6] & 0xFF) | ((gifBytes[7] & 0xFF) << 8);
        int height = (gifBytes[8] & 0xFF) | ((gifBytes[9] & 0xFF) << 8);

        LoggerUtil.debug(() -> String.format(
                "Extracted GIF dimensions: %dx%d", width, height));

        return new int[]{width, height};
    }

    /**
     * Extract dimensions from a BMP file header.
     * BMP format: bytes 18-21 = width (little-endian 32-bit), bytes 22-25 = height (little-endian 32-bit, can be negative for top-down)
     *
     * @param bmpBytes The raw BMP data
     * @return Array of [width, height]
     * @throws IOException if BMP header is invalid or too short
     */
    private int[] extractBmpDimensions(byte[] bmpBytes) throws IOException {
        if (bmpBytes.length < 26) {
            throw new IOException("BMP file too short (< 26 bytes)");
        }

        // Verify BMP magic "BM" at offset 0
        if (!(bmpBytes[0] == 'B' && bmpBytes[1] == 'M')) {
            throw new IOException("Invalid BMP header (expected 'BM' signature)");
        }

        // Extract width (little-endian 32-bit at offset 18)
        int width = (bmpBytes[18] & 0xFF) |
                    ((bmpBytes[19] & 0xFF) << 8) |
                    ((bmpBytes[20] & 0xFF) << 16) |
                    ((bmpBytes[21] & 0xFF) << 24);

        // Extract height (little-endian 32-bit at offset 22, can be negative for top-down)
        int height = (bmpBytes[22] & 0xFF) |
                     ((bmpBytes[23] & 0xFF) << 8) |
                     ((bmpBytes[24] & 0xFF) << 16) |
                     ((bmpBytes[25] & 0xFF) << 24);

        // Height can be negative (top-down BMP), use absolute value
        height = Math.abs(height);

        final int finalWidth = width;
        final int finalHeight = height;
        LoggerUtil.debug(() -> String.format(
                "Extracted BMP dimensions: %dx%d", finalWidth, finalHeight));

        return new int[]{width, height};
    }

    /**
     * Extract dimensions from an AOL .ART file header.
     * ART format: "JG" magic at bytes 0-1, width at bytes 12-13 (little-endian 16-bit), height at bytes 14-15 (little-endian 16-bit)
     *
     * @param artBytes The raw ART data
     * @return Array of [width, height]
     * @throws IOException if ART header is invalid or too short
     */
    private int[] extractArtDimensions(byte[] artBytes) throws IOException {
        if (artBytes.length < 16) {
            throw new IOException("ART file too short (< 16 bytes)");
        }

        // Verify ART magic "JG" (0x4a, 0x47) at offset 0
        if (!(artBytes[0] == 0x4a && artBytes[1] == 0x47)) {
            throw new IOException("Invalid ART header (expected 'JG' signature)");
        }

        // Extract width (little-endian 16-bit at offset 12)
        int width = (artBytes[12] & 0xFF) | ((artBytes[13] & 0xFF) << 8);

        // Extract height (little-endian 16-bit at offset 14)
        int height = (artBytes[14] & 0xFF) | ((artBytes[15] & 0xFF) << 8);

        LoggerUtil.debug(() -> String.format(
                "Extracted ART dimensions: %dx%d", width, height));

        return new int[]{width, height};
    }

    /**
     * Load and process a GIF file in pass-through mode (no resize, quantize, or dither).
     * Used for pre-formatted GIFs extracted from databases.
     * Wraps raw GIF with minimal 40-byte protocol header.
     *
     * @param matArtId The mat_art_id
     * @param platform Client platform (for platform-specific asset selection)
     * @return Raw bytes (protocol header + GIF data)
     * @throws IOException if GIF loading fails
     */
    private byte[] getArtAsBytesPassthrough(String matArtId, ClientPlatform platform) throws IOException {
        LoggerUtil.debug(String.format(
                "Loading GIF in pass-through mode: %s (platform: %s)", matArtId, platform));

        // Find GIF path with platform-aware resolution (checks for _opaque variant for Windows)
        String gifPath = findArtResource(matArtId, ".gif", platform);
        if (gifPath == null) {
            throw new IOException("GIF file not found: " + matArtId + ".gif");
        }
        byte[] rawGifBytes;

        try (InputStream inputStream = getClass().getResourceAsStream(gifPath)) {
            if (inputStream == null) {
                throw new IOException("GIF file not found: " + gifPath);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            rawGifBytes = baos.toByteArray();
        }

        LoggerUtil.debug(String.format(
                "Loaded raw GIF: %s, %d bytes", matArtId, rawGifBytes.length));

        // Extract dimensions from GIF header
        int[] dimensions = extractGifDimensions(rawGifBytes);
        int width = dimensions[0];
        int height = dimensions[1];

        // Wrap raw GIF with minimal 40-byte protocol header
        // Use default flag bytes: 0x80 (category), 0xFD (type code)
        byte[] aolHeader = AolArtHeader.generate(
                width,
                height,
                rawGifBytes.length,
                0,      // No padding
                0x80,   // Default flag byte 1
                0xFD);  // Default flag byte 2

        // Combine header + raw GIF data (no padding)
        byte[] completePayload = new byte[aolHeader.length + rawGifBytes.length];
        System.arraycopy(aolHeader, 0, completePayload, 0, aolHeader.length);
        System.arraycopy(rawGifBytes, 0, completePayload, aolHeader.length, rawGifBytes.length);

        LoggerUtil.debug(String.format(
                "GIF pass-through complete: %dx%d, %d bytes (header) + %d bytes (GIF) = %d bytes total",
                width, height, aolHeader.length, rawGifBytes.length, completePayload.length));

        return completePayload;
    }

    /**
     * Load and process a BMP file in pass-through mode.
     * Wraps raw BMP with 40-byte protocol header (using MAGIC_GIF).
     *
     * @param matArtId The mat_art_id
     * @return Raw bytes (protocol header + BMP data)
     * @throws IOException if BMP loading fails
     */
    private byte[] getArtAsBytesBmpPassthrough(String matArtId) throws IOException {
        LoggerUtil.debug(String.format(
                "Loading BMP in pass-through mode: %s", matArtId));

        // Find BMP path (no _opaque handling for BMP)
        String bmpPath = findArtResource(matArtId, ".bmp");
        if (bmpPath == null) {
            throw new IOException("BMP file not found: " + matArtId + ".bmp");
        }
        byte[] rawBmpBytes;

        try (InputStream inputStream = getClass().getResourceAsStream(bmpPath)) {
            if (inputStream == null) {
                throw new IOException("BMP file not found: " + bmpPath);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            rawBmpBytes = baos.toByteArray();
        }

        LoggerUtil.debug(String.format(
                "Loaded raw BMP: %s, %d bytes", matArtId, rawBmpBytes.length));

        // Extract dimensions from BMP header
        int[] dimensions = extractBmpDimensions(rawBmpBytes);
        int width = dimensions[0];
        int height = dimensions[1];

        // Wrap raw BMP with 40-byte protocol header (using MAGIC_GIF for BMP)
        byte[] aolHeader = AolArtHeader.generate(
                width,
                height,
                rawBmpBytes.length,
                0,      // No padding
                0x80,   // Default flag byte 1
                0xFD,   // Default flag byte 2
                AolArtHeader.MAGIC_GIF);  // BMP uses GIF magic constant

        // Combine header + raw BMP data
        byte[] completePayload = new byte[aolHeader.length + rawBmpBytes.length];
        System.arraycopy(aolHeader, 0, completePayload, 0, aolHeader.length);
        System.arraycopy(rawBmpBytes, 0, completePayload, aolHeader.length, rawBmpBytes.length);

        LoggerUtil.debug(String.format(
                "BMP pass-through complete: %dx%d, %d bytes (header) + %d bytes (BMP) = %d bytes total",
                width, height, aolHeader.length, rawBmpBytes.length, completePayload.length));

        return completePayload;
    }

    /**
     * Load and process an ART file in pass-through mode.
     * Wraps raw ART with 40-byte protocol header (using MAGIC_ART).
     *
     * @param matArtId The mat_art_id
     * @return Raw bytes (protocol header + ART data)
     * @throws IOException if ART loading fails
     */
    private byte[] getArtAsBytesArtPassthrough(String matArtId) throws IOException {
        LoggerUtil.debug(String.format(
                "Loading ART in pass-through mode: %s", matArtId));

        // Find ART path (no _opaque handling for ART)
        String artPath = findArtResource(matArtId, ".art");
        if (artPath == null) {
            throw new IOException("ART file not found: " + matArtId + ".art");
        }
        byte[] rawArtBytes;

        try (InputStream inputStream = getClass().getResourceAsStream(artPath)) {
            if (inputStream == null) {
                throw new IOException("ART file not found: " + artPath);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            rawArtBytes = baos.toByteArray();
        }

        LoggerUtil.debug(String.format(
                "Loaded raw ART: %s, %d bytes", matArtId, rawArtBytes.length));

        // Extract dimensions from ART header
        int[] dimensions = extractArtDimensions(rawArtBytes);
        int width = dimensions[0];
        int height = dimensions[1];

        // Wrap raw ART with 40-byte protocol header (using MAGIC_ART for .ART files)
        byte[] aolHeader = AolArtHeader.generate(
                width,
                height,
                rawArtBytes.length,
                0,      // No padding
                0x80,   // Default flag byte 1
                0xFD,   // Default flag byte 2
                AolArtHeader.MAGIC_ART);  // ART uses ART magic constant (0x0209)

        // Combine header + raw ART data
        byte[] completePayload = new byte[aolHeader.length + rawArtBytes.length];
        System.arraycopy(aolHeader, 0, completePayload, 0, aolHeader.length);
        System.arraycopy(rawArtBytes, 0, completePayload, aolHeader.length, rawArtBytes.length);

        LoggerUtil.debug(String.format(
                "ART pass-through complete: %dx%d, %d bytes (header) + %d bytes (ART) = %d bytes total",
                width, height, aolHeader.length, rawArtBytes.length, completePayload.length));

        return completePayload;
    }

    /**
     * Check if an art asset exists in resources.
     * Searches all art paths (root + subdirectories).
     * Returns true if:
     * - Image file exists with JSON metadata (standard processing), OR
     * - GIF/BMP/ART file exists without JSON (pass-through mode), OR
     * - PNG/JPG/JPEG file exists without JSON (default processing with 640x480 constraint)
     */
    public boolean artExists(String matArtId) {
        // Search for image in all art paths
        String imagePath = null;
        String foundExtension = null;
        for (String ext : SUPPORTED_IMAGE_EXTENSIONS) {
            imagePath = findArtResource(matArtId, ext);
            if (imagePath != null) {
                foundExtension = ext;
                break;
            }
        }

        if (imagePath == null) {
            return false;
        }

        // Check for JSON metadata in all art paths
        String jsonPath = findArtResource(matArtId, ".json");

        // If JSON exists, both files must be present (standard processing)
        if (jsonPath != null) {
            return true;
        }

        // If JSON doesn't exist, allow:
        // - GIF/BMP/ART files (pass-through mode)
        // - PNG/JPG/JPEG files (default processing with 640x480 constraint)
        return ".gif".equals(foundExtension) ||
               ".bmp".equals(foundExtension) ||
               ".art".equals(foundExtension) ||
               ".png".equals(foundExtension) ||
               ".jpg".equals(foundExtension) ||
               ".jpeg".equals(foundExtension);
    }

    /**
     * Get the display dimensions for an art asset.
     * Returns the dimensions that the image will be displayed at after processing.
     *
     * @param matArtId The mat_art_id (e.g., "1-0-34196")
     * @return Array of [width, height] in pixels
     * @throws IOException if art asset doesn't exist or dimensions can't be determined
     */
    public int[] getArtDimensions(String matArtId) throws IOException {
        // Check if art exists, fall back to default if not
        if (!artExists(matArtId)) {
            throw new IOException("Art asset not found: " + matArtId);
        }
        String actualMatArtId = matArtId;

        // Check if JSON metadata exists (search all art paths)
        String jsonPath = findArtResource(actualMatArtId, ".json");
        boolean hasJson = (jsonPath != null);

        // Determine image type for handling without JSON
        String imagePath = findImagePath(actualMatArtId);
        String extension = null;
        for (String ext : SUPPORTED_IMAGE_EXTENSIONS) {
            if (imagePath.endsWith(ext)) {
                extension = ext;
                break;
            }
        }

        // Make final copy for lambda expressions
        final String finalMatArtId = actualMatArtId;

        if (hasJson) {
            // JSON asset: Process full image to get actual GIF dimensions after aspect-ratio resize
            // This ensures the window dimensions match the actual displayed image
            LoggerUtil.debug(() -> String.format(
                "Processing image to extract actual GIF dimensions for %s", finalMatArtId));

            // Load and process image through full pipeline
            BufferedImage image = loadImage(actualMatArtId);
            ArtMetadata metadata = loadMetadata(actualMatArtId);

            // Resize with aspect ratio preservation
            BufferedImage resized = GifEncoder.resize(image, metadata.getWidth(), metadata.getHeight());

            // Quantize to 256 colors
            BufferedImage quantized = GifEncoder.quantizeTo256ColorsAdaptive(
                resized,
                metadata.isEnableDithering(),
                metadata.isEnablePosterization(),
                metadata.getPosterizationLevel()
            );

            // Encode to GIF with protocol header and automatic size limiting
            byte[] gifBytes = GifEncoder.encodeWithSizeLimit(quantized, metadata);

            // GIF structure: [40-byte protocol header] + [GIF data]
            // GIF header dimensions at bytes 6-9 of GIF data (little-endian)
            // So in complete payload: bytes 46-49
            if (gifBytes.length < 50) {
                throw new IOException("GIF payload too short after encoding: " + gifBytes.length);
            }

            // Extract dimensions from GIF header (little-endian)
            int width = (gifBytes[46] & 0xFF) | ((gifBytes[47] & 0xFF) << 8);
            int height = (gifBytes[48] & 0xFF) | ((gifBytes[49] & 0xFF) << 8);

            LoggerUtil.debug(() -> String.format(
                "Extracted actual GIF dimensions for %s: %dx%d (after aspect-ratio resize)",
                finalMatArtId, width, height));

            return new int[]{width, height};
        } else {
            // No JSON metadata: handle pass-through formats or PNG/JPG with default processing
            if (".gif".equals(extension)) {
                // GIF pass-through: extract dimensions from GIF header
                try (InputStream inputStream = getClass().getResourceAsStream(imagePath)) {
                    if (inputStream == null) {
                        throw new IOException("GIF file not found: " + imagePath);
                    }

                    // Read first 10 bytes to extract dimensions
                    byte[] header = new byte[10];
                    int bytesRead = inputStream.read(header);
                    if (bytesRead < 10) {
                        throw new IOException("GIF file too short (< 10 bytes): " + imagePath);
                    }

                    // Extract dimensions using existing method
                    int[] dimensions = extractGifDimensions(header);
                    LoggerUtil.debug(() -> String.format(
                        "Extracted dimensions from GIF header for %s: %dx%d",
                        finalMatArtId, dimensions[0], dimensions[1]));
                    return dimensions;
                }
            } else if (".bmp".equals(extension)) {
                // BMP pass-through: extract dimensions from BMP header
                try (InputStream inputStream = getClass().getResourceAsStream(imagePath)) {
                    if (inputStream == null) {
                        throw new IOException("BMP file not found: " + imagePath);
                    }

                    // Read first 26 bytes to extract dimensions
                    byte[] header = new byte[26];
                    int bytesRead = inputStream.read(header);
                    if (bytesRead < 26) {
                        throw new IOException("BMP file too short (< 26 bytes): " + imagePath);
                    }

                    // Extract dimensions using existing method
                    int[] dimensions = extractBmpDimensions(header);
                    LoggerUtil.debug(() -> String.format(
                        "Extracted dimensions from BMP header for %s: %dx%d",
                        finalMatArtId, dimensions[0], dimensions[1]));
                    return dimensions;
                }
            } else if (".art".equals(extension)) {
                // ART pass-through: extract dimensions from ART header
                try (InputStream inputStream = getClass().getResourceAsStream(imagePath)) {
                    if (inputStream == null) {
                        throw new IOException("ART file not found: " + imagePath);
                    }

                    // Read first 16 bytes to extract dimensions
                    byte[] header = new byte[16];
                    int bytesRead = inputStream.read(header);
                    if (bytesRead < 16) {
                        throw new IOException("ART file too short (< 16 bytes): " + imagePath);
                    }

                    // Extract dimensions using existing method
                    int[] dimensions = extractArtDimensions(header);
                    LoggerUtil.debug(() -> String.format(
                        "Extracted dimensions from ART header for %s: %dx%d",
                        finalMatArtId, dimensions[0], dimensions[1]));
                    return dimensions;
                }
            } else {
                // PNG/JPG/JPEG without JSON: process with default metadata to get actual GIF dimensions
                LoggerUtil.debug(() -> String.format(
                    "Processing image with default metadata to extract actual GIF dimensions for %s", finalMatArtId));

                BufferedImage image = loadImage(actualMatArtId);
                ArtMetadata metadata = createDefaultMetadata();

                // Resize with aspect ratio preservation to 640x480 bounds
                BufferedImage resized = GifEncoder.resize(image, metadata.getWidth(), metadata.getHeight());

                // Quantize with dithering enabled
                BufferedImage quantized = GifEncoder.quantizeTo256ColorsAdaptive(
                    resized,
                    metadata.isEnableDithering(),
                    metadata.isEnablePosterization(),
                    metadata.getPosterizationLevel()
                );

                // Encode to GIF with protocol header and automatic size limiting
                byte[] gifBytes = GifEncoder.encodeWithSizeLimit(quantized, metadata);

                // Extract dimensions from GIF header
                if (gifBytes.length < 50) {
                    throw new IOException("GIF payload too short after encoding: " + gifBytes.length);
                }

                int width = (gifBytes[46] & 0xFF) | ((gifBytes[47] & 0xFF) << 8);
                int height = (gifBytes[48] & 0xFF) | ((gifBytes[49] & 0xFF) << 8);

                LoggerUtil.debug(() -> String.format(
                    "Extracted actual GIF dimensions for %s (default processing): %dx%d",
                    finalMatArtId, width, height));

                return new int[]{width, height};
            }
        }
    }
}
