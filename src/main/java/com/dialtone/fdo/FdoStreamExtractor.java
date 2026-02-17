/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo;

import com.atomforge.fdo.model.FdoAtom;
import com.atomforge.fdo.model.FdoStream;
import com.dialtone.protocol.auth.LoginCredentials;
import com.dialtone.protocol.im.InstantMessage;
import com.dialtone.utils.LoggerUtil;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Native FDO extraction using the FdoStream API.
 *
 * <p>This utility replaces the HTTP-based decompilation + regex parsing approach
 * with native Java decoding of FDO binary data. Benefits include:</p>
 * <ul>
 *   <li>No HTTP round-trips to Atomforge API</li>
 *   <li>Type-safe access to atom values</li>
 *   <li>Automatic handling of large atom continuations</li>
 *   <li>Simpler multi-frame reassembly (just concatenate bytes)</li>
 * </ul>
 *
 * <p>Usage example:</p>
 * <pre>
 * // Single frame extraction
 * LoginCredentials creds = FdoStreamExtractor.extractLoginCredentials(ddFrame);
 *
 * // Multi-frame extraction
 * InstantMessage im = FdoStreamExtractor.extractInstantMessage(allFrames);
 * </pre>
 */
public final class FdoStreamExtractor {

    /**
     * P3 frame header size (includes sync, CRC, length, TX/RX, type, token, streamId).
     */
    private static final int P3_HEADER_SIZE = 12;

    /**
     * Frame terminator byte.
     */
    private static final byte FRAME_TERMINATOR = 0x0D;

    private FdoStreamExtractor() {
        // Utility class
    }

    /**
     * Strip P3 header from a frame, returning pure FDO bytes.
     *
     * <p>P3 frame structure:</p>
     * <pre>
     * [sync:1][crc:2][len:2][tx:1][rx:1][type:1][token:2][streamId:2][fdo...][0x0D]
     *  0       1-2    3-4    5     6     7       8-9      10-11       12+
     * </pre>
     *
     * @param frame complete P3 frame bytes
     * @return FDO payload bytes (header and optional terminator removed)
     */
    public static byte[] stripP3Header(byte[] frame) {
        if (frame == null || frame.length <= P3_HEADER_SIZE) {
            return new byte[0];
        }

        int end = frame.length;
        // Remove trailing 0x0D terminator if present
        if (end > 0 && frame[end - 1] == FRAME_TERMINATOR) {
            end--;
        }

        if (end <= P3_HEADER_SIZE) {
            return new byte[0];
        }

        return Arrays.copyOfRange(frame, P3_HEADER_SIZE, end);
    }

    /**
     * Concatenate FDO payloads from multiple P3 frames.
     *
     * <p>Strips P3 headers from each frame and concatenates the pure FDO bytes.
     * The FdoStream decoder will handle large atom continuation atoms internally.</p>
     *
     * @param frames list of complete P3 frames
     * @return concatenated FDO binary payload
     */
    public static byte[] concatenateFrames(List<byte[]> frames) {
        if (frames == null || frames.isEmpty()) {
            return new byte[0];
        }

        ByteArrayOutputStream combined = new ByteArrayOutputStream();
        for (byte[] frame : frames) {
            byte[] fdo = stripP3Header(frame);
            if (fdo.length > 0) {
                combined.write(fdo, 0, fdo.length);
            }
        }
        return combined.toByteArray();
    }

    /**
     * Extract all de_data string values from FDO binary.
     *
     * @param fdoBinary pure FDO binary (no P3 header)
     * @return list of de_data string values in order of appearance
     */
    public static List<String> extractDeData(byte[] fdoBinary) {
        if (fdoBinary == null || fdoBinary.length == 0) {
            return List.of();
        }

        try {
            FdoStream stream = FdoStream.decode(fdoBinary);
            return stream.findAll("de_data").stream()
                    .filter(FdoAtom::isString)
                    .map(FdoAtom::getString)
                    .toList();
        } catch (Exception e) {
            LoggerUtil.warn("[FdoStreamExtractor] Failed to decode FDO binary: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Extract login credentials (username, password) from a Dd frame.
     *
     * <p>Dd frame FDO structure:</p>
     * <pre>
     * uni_start_stream
     *   man_set_context_relative <1>
     *   man_set_context_index <1>
     *   de_data <"username">      <-- first de_data
     *   man_end_context
     *   man_end_context
     *   man_set_context_relative <2>
     *   de_data <"password">      <-- second de_data
     *   man_end_context
     * uni_end_stream
     * </pre>
     *
     * @param ddFrame complete Dd P3 frame bytes
     * @return LoginCredentials with username and password
     * @throws IllegalArgumentException if credentials cannot be extracted
     */
    public static LoginCredentials extractLoginCredentials(byte[] ddFrame) {
        byte[] fdo = stripP3Header(ddFrame);
        if (fdo.length == 0) {
            throw new IllegalArgumentException("Dd frame has no FDO payload");
        }

        try {
            FdoStream stream = FdoStream.decode(fdo);
            List<FdoAtom> deDataAtoms = stream.findAll("de_data");

            // Filter to only string values (some may be EmptyValue)
            List<String> stringValues = deDataAtoms.stream()
                    .filter(FdoAtom::isString)
                    .map(FdoAtom::getString)
                    .toList();

            if (stringValues.size() < 2) {
                throw new IllegalArgumentException(
                        String.format("Expected at least 2 string de_data atoms in Dd frame, found %d atoms (%d strings)",
                                deDataAtoms.size(), stringValues.size()));
            }

            // First de_data is username, second is password
            String username = stringValues.get(0);
            String password = stringValues.get(1);

            // Trim username to handle Windows client padding (e.g., "Guest      ")
            username = username.trim();

            LoggerUtil.info("[FdoStreamExtractor] Extracted login credentials: username='" + username + "'");
            return new LoginCredentials(username, password);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to extract credentials from Dd frame: " + e.getMessage(), e);
        }
    }

    /**
     * Extract instant message data from iS frame(s).
     *
     * <p>iS frame FDO structure:</p>
     * <pre>
     * uni_start_stream
     *   man_set_response_id <123>      <-- optional, for replies
     *   man_set_context_relative <1>
     *   de_data <"recipient">          <-- first de_data (null for replies)
     *   man_end_context
     *   man_set_context_relative <2>
     *   de_data <"message content">    <-- second de_data (the message)
     *   man_end_context
     * uni_end_stream
     * </pre>
     *
     * <p>For multi-frame messages, the FDO binary is split across frames.
     * This method concatenates all frame payloads and decodes once.</p>
     *
     * @param frames list of iS P3 frames (single frame or multi-frame sequence)
     * @return InstantMessage with recipient, message, and optional responseId
     * @throws IllegalArgumentException if message cannot be extracted
     */
    public static InstantMessage extractInstantMessage(List<byte[]> frames) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("No frames provided for IM extraction");
        }

        byte[] fdo = concatenateFrames(frames);
        if (fdo.length == 0) {
            throw new IllegalArgumentException("iS frames have no FDO payload");
        }

        try {
            FdoStream stream = FdoStream.decode(fdo);

            // Extract response_id if present (for replies to existing conversations)
            Integer responseId = stream.findFirst("man_set_response_id")
                    .filter(FdoAtom::isNumber)
                    .map(atom -> (int) atom.getNumber())
                    .orElse(null);

            // Extract de_data atoms - filter to only string values
            // Some de_data atoms may be EmptyValue (e.g., in reply frames or fragmented messages)
            List<FdoAtom> deDataAtoms = stream.findAll("de_data");
            List<String> stringValues = deDataAtoms.stream()
                    .filter(FdoAtom::isString)
                    .map(FdoAtom::getString)
                    .filter(s -> s != null && !s.isEmpty())
                    .toList();

            LoggerUtil.debug(() -> String.format(
                    "[FdoStreamExtractor] Found %d de_data atoms, %d with string values",
                    deDataAtoms.size(), stringValues.size()));

            String recipient = null;
            String message = null;

            if (stringValues.size() >= 2) {
                // Standard format: first de_data is recipient, second is message
                recipient = stringValues.get(0);
                message = stringValues.get(1);
            } else if (stringValues.size() == 1) {
                // Reply format: only message present (recipient is null)
                message = stringValues.get(0);
            }

            if (message == null || message.isEmpty()) {
                throw new IllegalArgumentException(
                        String.format("No message found in iS frame(s): %d de_data atoms, %d string values",
                                deDataAtoms.size(), stringValues.size()));
            }

            // Strip HTML wrapper tags if present
            message = stripHtmlTags(message);

            LoggerUtil.info(String.format(
                    "[FdoStreamExtractor] Extracted IM: responseId=%s, to='%s', message=%d chars",
                    responseId, recipient, message.length()));

            return new InstantMessage(recipient, message, responseId);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to extract IM from iS frame(s): " + e.getMessage(), e);
        }
    }

    /**
     * Extract instant message from a single frame.
     * Convenience method that wraps the frame in a list.
     *
     * @param isFrame single iS P3 frame bytes
     * @return InstantMessage with recipient, message, and optional responseId
     */
    public static InstantMessage extractInstantMessage(byte[] isFrame) {
        return extractInstantMessage(List.of(isFrame));
    }

    /**
     * Strip HTML wrapper tags from message content.
     *
     * <p>AOL IM messages often contain HTML formatting like:</p>
     * <pre>
     * &lt;HTML&gt;&lt;BODY&gt;Hello world&lt;/BODY&gt;&lt;/HTML&gt;
     * </pre>
     *
     * @param message raw message content
     * @return message with HTML tags stripped
     */
    private static String stripHtmlTags(String message) {
        if (message == null) {
            return null;
        }

        // Remove common HTML wrapper tags
        String stripped = message
                .replaceAll("(?i)<html>", "")
                .replaceAll("(?i)</html>", "")
                .replaceAll("(?i)<body[^>]*>", "")
                .replaceAll("(?i)</body>", "")
                .trim();

        return stripped;
    }

    // ========== DOD Request Extraction Methods ==========

    /**
     * Extract Stream ID from uni_start_stream atom in FDO binary.
     *
     * <p>DOD request FDO typically contains:</p>
     * <pre>
     * uni_start_stream <0x2100>  -- Stream ID as hex number
     * </pre>
     *
     * @param fdoBinary pure FDO binary (no P3 header)
     * @return Stream ID value, or null if not found
     */
    public static Integer extractStreamId(byte[] fdoBinary) {
        if (fdoBinary == null || fdoBinary.length == 0) {
            return null;
        }

        try {
            FdoStream stream = FdoStream.decode(fdoBinary);
            return stream.findFirst("uni_start_stream")
                    .filter(FdoAtom::isNumber)
                    .map(atom -> (int) atom.getNumber())
                    .orElse(null);
        } catch (Exception e) {
            LoggerUtil.warn("[FdoStreamExtractor] Failed to extract stream ID: " + e.getMessage());
            return null;
        }
    }

    /**
     * Extract DOD parameters from FDO binary.
     *
     * <p>DOD request FDO structure (multi-GID format):</p>
     * <pre>
     * dod_form_id <"form123">
     * uni_transaction_id <12345>
     * dod_gid <"gid1">
     * uni_transaction_id <12346>
     * dod_gid <"gid2">
     * ...
     * </pre>
     *
     * @param fdoBinary pure FDO binary (no P3 header)
     * @return DodParameters containing form ID and GID/transaction pairs
     */
    public static DodParameters extractDodParameters(byte[] fdoBinary) {
        DodParameters params = new DodParameters();
        params.gidPairs = new ArrayList<>();

        if (fdoBinary == null || fdoBinary.length == 0) {
            return params;
        }

        try {
            LoggerUtil.info("[FdoStreamExtractor] Decoding FDO binary: " + fdoBinary.length + " bytes");
            FdoStream stream = FdoStream.decode(fdoBinary);
            LoggerUtil.info("[FdoStreamExtractor] FdoStream decoded successfully");

            // Extract form ID (it's a GID, not a String)
            params.formId = stream.findFirst("dod_form_id")
                    .filter(FdoAtom::isGid)
                    .map(atom -> atom.getGid().toString())
                    .orElse(null);
            LoggerUtil.info("[FdoStreamExtractor] dod_form_id: " + params.formId);

            // Extract all transaction IDs (they are numbers)
            List<String> transactionIds = stream.findAll("uni_transaction_id").stream()
                    .map(atom -> {
                        if (atom.isNumber()) {
                            return String.valueOf((long) atom.getNumber());
                        }
                        return null;
                    })
                    .filter(s -> s != null)
                    .toList();
            LoggerUtil.info("[FdoStreamExtractor] uni_transaction_id count: " + transactionIds.size());

            // Extract all GIDs (they are GIDs, not Strings)
            List<String> gids = stream.findAll("dod_gid").stream()
                    .filter(FdoAtom::isGid)
                    .map(atom -> atom.getGid().toString())
                    .toList();
            LoggerUtil.info("[FdoStreamExtractor] dod_gid count: " + gids.size());

            // Pair them up - GIDs and transaction IDs are typically paired in order
            // Each transaction ID is followed by its GID in the FDO structure
            for (int i = 0; i < gids.size(); i++) {
                String transactionId = i < transactionIds.size() ? transactionIds.get(i) : "0";
                params.gidPairs.add(new GidTransactionPair(gids.get(i), transactionId));
            }

            LoggerUtil.info(String.format(
                    "[FdoStreamExtractor] Extracted DOD params: formId=%s, gidPairs=%d",
                    params.formId, params.gidPairs.size()));

            return params;

        } catch (Exception e) {
            LoggerUtil.error("[FdoStreamExtractor] Failed to extract DOD parameters: " + e.getMessage());
            e.printStackTrace();
            return params;
        }
    }

    /**
     * Check if FDO binary contains uni_end_stream atom.
     *
     * <p>Used for multi-frame detection to determine if a frame sequence is complete.</p>
     *
     * @param fdoBinary pure FDO binary (no P3 header)
     * @return true if uni_end_stream is present
     */
    public static boolean hasUniEndStream(byte[] fdoBinary) {
        if (fdoBinary == null || fdoBinary.length == 0) {
            return false;
        }

        try {
            FdoStream stream = FdoStream.decode(fdoBinary);
            return stream.findFirst("uni_end_stream").isPresent();
        } catch (Exception e) {
            LoggerUtil.debug(() -> "[FdoStreamExtractor] Failed to check for uni_end_stream: " + e.getMessage());
            return false;
        }
    }

    /**
     * Extract the first de_data string value from FDO binary.
     * Convenience method for single-value extraction (Aa/Kk tokens).
     *
     * @param fdoBinary pure FDO binary (no P3 header)
     * @return first de_data string value, or null if not found
     */
    public static String extractFirstDeData(byte[] fdoBinary) {
        List<String> values = extractDeData(fdoBinary);
        return values.isEmpty() ? null : values.get(0);
    }

    // ========== K1 Token Extraction Methods ==========

    /**
     * Extract K1 parameters (GID and response ID) from a K1 frame.
     *
     * <p>K1 frame FDO structure:</p>
     * <pre>
     * uni_start_stream
     *   man_set_response_id <12345>   <-- response ID for reply routing
     *   de_data <raw 4 bytes>         <-- GID in big-endian format
     * uni_end_stream
     * </pre>
     *
     * @param k1Frame complete K1 P3 frame bytes
     * @return K1Parameters with GID and response ID
     */
    public static K1Parameters extractK1Parameters(byte[] k1Frame) {
        byte[] fdo = stripP3Header(k1Frame);
        if (fdo.length == 0) {
            LoggerUtil.warn("[FdoStreamExtractor] K1 frame has no FDO payload");
            return new K1Parameters(0, 0);
        }

        try {
            FdoStream stream = FdoStream.decode(fdo);

            // Extract response ID
            int responseId = stream.findFirst("man_set_response_id")
                    .filter(FdoAtom::isNumber)
                    .map(atom -> (int) atom.getNumber())
                    .orElse(0);

            // Extract GID from de_data - it's raw bytes, not a string
            // The de_data contains 4 bytes representing the GID in big-endian format
            int gid = 0;
            var deDataAtom = stream.findFirst("de_data");
            if (deDataAtom.isPresent()) {
                FdoAtom atom = deDataAtom.get();
    if (atom.isString()) {
                    // String contains raw bytes - treat each char as a byte
                    String str = atom.getString();
                    byte[] gidBytes = new byte[str.length()];
                    for (int i = 0; i < str.length(); i++) {
                        gidBytes[i] = (byte) str.charAt(i);
                    }
                    if (gidBytes.length >= 4) {
                        // Big-endian byte order
                        gid = ((gidBytes[0] & 0xFF) << 24) |
                              ((gidBytes[1] & 0xFF) << 16) |
                              ((gidBytes[2] & 0xFF) << 8) |
                              (gidBytes[3] & 0xFF);
                    }
                }
            }

            LoggerUtil.info(String.format("[FdoStreamExtractor] Extracted K1 params: gid=0x%08X, responseId=%d",
                    gid, responseId));

            return new K1Parameters(gid, responseId);

        } catch (Exception e) {
            LoggerUtil.error("[FdoStreamExtractor] Failed to extract K1 parameters: " + e.getMessage());
            return new K1Parameters(0, 0);
        }
    }

    /**
     * Parameters extracted from a K1 request frame.
     */
    public static class K1Parameters {
        public final int gid;
        public final int responseId;

        public K1Parameters(int gid, int responseId) {
            this.gid = gid;
            this.responseId = responseId;
        }
    }

    // ========== NX Token Extraction Methods ==========

    /**
     * Extract NX identifier from an NX frame.
     *
     * <p>NX frame FDO structure:</p>
     * <pre>
     * uni_start_stream
     *   sm_send_token_arg <"NX5">
     * uni_end_stream
     * </pre>
     *
     * <p>The sm_send_token_arg produces de_data like <"\x00\x00\x005"> where the digit is at the end.</p>
     *
     * @param nxFrame complete NX P3 frame bytes
     * @return NX identifier string (1-5), or null if not found
     */
    public static String extractNxIdentifier(byte[] nxFrame) {
        byte[] fdo = stripP3Header(nxFrame);
        if (fdo.length == 0) {
            LoggerUtil.warn("[FdoStreamExtractor] NX frame has no FDO payload");
            return null;
        }

        try {
            FdoStream stream = FdoStream.decode(fdo);

            // Look for sm_send_token_arg first - this has the NX identifier
            var tokenArg = stream.findFirst("sm_send_token_arg");
            if (tokenArg.isPresent() && tokenArg.get().isString()) {
                String arg = tokenArg.get().getString();
                // Format is "NX5" - extract the digit
                if (arg != null && arg.length() >= 3 && arg.startsWith("NX")) {
                    String identifier = arg.substring(2);
                    LoggerUtil.info("[FdoStreamExtractor] Extracted NX identifier from sm_send_token_arg: " + identifier);
                    return identifier;
                }
            }

            // Fallback: Look for de_data with format \x00\x00\x00{digit}
            var deDataAtom = stream.findFirst("de_data");
            if (deDataAtom.isPresent()) {
                FdoAtom atom = deDataAtom.get();
                String value = null;
                if (atom.isString()) {
                    value = atom.getString();
                }

                if (value != null && !value.isEmpty()) {
                    // The identifier is the last character (digit)
                    char lastChar = value.charAt(value.length() - 1);
                    if (Character.isDigit(lastChar)) {
                        String identifier = String.valueOf(lastChar);
                        LoggerUtil.info("[FdoStreamExtractor] Extracted NX identifier from de_data: " + identifier);
                        return identifier;
                    }
                }
            }

            LoggerUtil.warn("[FdoStreamExtractor] No NX identifier found in frame");
            return null;

        } catch (Exception e) {
            LoggerUtil.error("[FdoStreamExtractor] Failed to extract NX identifier: " + e.getMessage());
            return null;
        }
    }

    // ========== DOD Parameter Classes ==========

    /**
     * Pairs a GID with its transaction ID for multi-GID DOD requests.
     */
    public static class GidTransactionPair {
        public final String gid;
        public final String transactionId;

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
     * Parameters extracted from a DOD request FDO.
     */
    public static class DodParameters {
        public String formId;
        public List<GidTransactionPair> gidPairs;

        public boolean hasMultipleGids() {
            return gidPairs != null && gidPairs.size() > 1;
        }

        public boolean hasGids() {
            return gidPairs != null && !gidPairs.isEmpty();
        }

        public GidTransactionPair getFirstGid() {
            return (gidPairs != null && !gidPairs.isEmpty()) ? gidPairs.get(0) : null;
        }
    }
}
