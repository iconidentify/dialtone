/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for file transfer announcement.
 *
 * <p>Generates an FDO stream that announces a file transfer to the recipient.
 * Contains file metadata like name, size, and creation date.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   xfer_start_object &lt;0&gt;
 *     xfer_atr_library &lt;"library"&gt;
 *     xfer_bool_mail &lt;no&gt;
 *     xfer_atr_request_id &lt;requestId&gt;
 *     xfer_atr_title &lt;"title"&gt;
 *     xfer_atr_file_size &lt;fileSize&gt;
 *     xfer_atr_file_name &lt;"fileName"&gt;
 *     xfer_atr_create_date &lt;createDate&gt;
 *   xfer_end_object
 * uni_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/xfer_announce.fdo.txt}</p>
 */
public final class XferAnnounceFdoBuilder implements FdoDslBuilder, FdoBuilder.Dynamic<XferAnnounceFdoBuilder.Config> {

    private static final String GID = "xfer_announce";

    /**
     * Configuration for file transfer announcement.
     *
     * @param library    The transfer library name
     * @param requestId  The transfer request ID
     * @param title      The display title for the transfer
     * @param fileSize   The file size in bytes
     * @param fileName   The file name
     * @param createDate The file creation date (epoch seconds or AOL date format)
     */
    public record Config(
            String library,
            int requestId,
            String title,
            long fileSize,
            String fileName,
            long createDate
    ) {
        public Config {
            if (library == null) library = "";
            if (title == null) title = "";
            if (fileName == null) fileName = "";
        }
    }

    private final Config config;

    /**
     * Create a new XferAnnounce builder with the given configuration.
     *
     * @param config The transfer announcement configuration
     */
    public XferAnnounceFdoBuilder(Config config) {
        if (config == null) throw new IllegalArgumentException("config cannot be null");
        this.config = config;
    }

    /**
     * Create a new XferAnnounce builder with individual parameters.
     */
    public XferAnnounceFdoBuilder(String library, int requestId, String title,
                                   long fileSize, String fileName, long createDate) {
        this(new Config(library, requestId, title, fileSize, fileName, createDate));
    }

    /**
     * Factory method for creating a file transfer announcement.
     *
     * @param library    The transfer library name
     * @param requestId  The transfer request ID
     * @param title      The display title
     * @param fileSize   The file size in bytes
     * @param fileName   The file name
     * @param createDate The creation date
     * @return new builder instance
     */
    public static XferAnnounceFdoBuilder announce(String library, int requestId, String title,
                                                   long fileSize, String fileName, long createDate) {
        return new XferAnnounceFdoBuilder(library, requestId, title, fileSize, fileName, createDate);
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "File transfer announcement";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        // Note: The original template has all xfer atoms commented out.
        // When xfer protocol support is needed, uncomment and implement xfer atoms.
        // For now, output an empty stream matching the original template behavior.
        return FdoScript.stream()
                .uniStartStream()
                .uniEndStream()
                .toSource();
    }
}
