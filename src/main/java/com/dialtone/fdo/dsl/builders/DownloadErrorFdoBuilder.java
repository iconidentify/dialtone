/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.atoms.AsyncAtom;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for download error alert.
 *
 * <p>Generates an FDO stream that displays an error message to the user
 * when a download fails. This is used for file transfer or DOD errors.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   async_alert &lt;info, "error message"&gt;
 * uni_wait_off
 * uni_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/download_error.fdo.txt}</p>
 */
public final class DownloadErrorFdoBuilder implements FdoDslBuilder {

    private static final String GID = "download_error";
    private static final String DEFAULT_ERROR = "Download failed. Please try again.";

    /**
     * Configuration for download error.
     *
     * @param errorMessage The error message to display
     */
    public record Config(String errorMessage) {
        public Config {
            if (errorMessage == null || errorMessage.isBlank()) {
                errorMessage = DEFAULT_ERROR;
            }
        }
    }

    private final Config config;

    /**
     * Create a new DownloadError builder with the given configuration.
     *
     * @param config The error configuration
     */
    public DownloadErrorFdoBuilder(Config config) {
        this.config = config != null ? config : new Config(DEFAULT_ERROR);
    }

    /**
     * Create a new DownloadError builder with the given error message.
     *
     * @param errorMessage The error message to display
     */
    public DownloadErrorFdoBuilder(String errorMessage) {
        this(new Config(errorMessage));
    }

    /**
     * Create a new DownloadError builder with default error message.
     */
    public DownloadErrorFdoBuilder() {
        this(new Config(DEFAULT_ERROR));
    }

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "Download error alert dialog";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .uniStartStream()
                .atom(AsyncAtom.ALERT, "info", config.errorMessage)
                .uniWaitOff()
                .uniEndStream()
                .toSource();
    }
}
