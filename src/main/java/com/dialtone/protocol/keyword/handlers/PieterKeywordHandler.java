/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.keyword.handlers;

import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.dsl.ContentWindowConfig;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.keyword.handlers.base.AbstractContentKeywordHandler;

/**
 * Keyword handler for the "Pieter" retro computing content window.
 *
 * <p><b>Keyword:</b> "pieter" (case-insensitive)
 *
 * <p><b>Functionality:</b>
 * <ul>
 *   <li>Displays content about a fictional 1990s retro computer called "Pieter"</li>
 *   <li>Shows scrollable text content with retro computing theme</li>
 *   <li>Uses placeholder logo art (to be replaced with actual branding)</li>
 *   <li>Supports color and BW rendering modes</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>
 * pieter     -> Displays Pieter content window
 * PIETER     -> Case-insensitive, works the same
 * Pieter     -> Also works
 * </pre>
 *
 * <p><b>Art Assets:</b>
 * <ul>
 *   <li>Logo: 1-69-40001 (placeholder - user will provide final art)</li>
 *   <li>Background: 1-69-27256 (shared welcome window background)</li>
 * </ul>
 */
public final class PieterKeywordHandler extends AbstractContentKeywordHandler {

    private static final String KEYWORD = "pieter";
    private static final String DESCRIPTION = "Pieter retro computing content window";
    private static final String LOG_PREFIX = "PIETER";

    /** Logo placeholder art ID */
    private static final String LOGO_ART_ID = "1-69-40001";

    /** Static placeholder content about the fictional Pieter computer */
    private static final String PIETER_CONTENT = """
        PRESS RELEASE - Dec 31 2025

        PIETER.com now offers Dialtone connectivity for
        its high performance Windows 3.11 environments.

        Boot into a modern, state-of-the-art Windows 3.11
        desktop (with Workgroups) right from PIETER.com,
        then connect to DIALTONE for a true cross-platform
        AOL 3.0 compatible experience.

        HIGHLIGHTS:
        - High performance Windows 3.11 with TCP/IP
        - Dialtone connectivity (AOL 3.0 compatible)
        - True 90s Windows desktop experience
        - Built for modern reliability and speed

        Windows users: this one's for you. Fire it up,
        connect, and relive the classic AOL vibe.
        """;

    /**
     * Create a Pieter keyword handler.
     *
     * @param fdoCompiler the FDO compiler for template compilation
     */
    public PieterKeywordHandler(FdoCompiler fdoCompiler) {
        super(fdoCompiler);
    }

    @Override
    public String getKeyword() {
        return KEYWORD;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    protected ContentWindowConfig getConfig() {
        return ContentWindowConfig.withDefaults(
            KEYWORD,
            "Pieter Retro Computing",
            LOGO_ART_ID
        );
    }

    @Override
    protected String getContent(SessionContext session) {
        return PIETER_CONTENT;
    }

    @Override
    protected String getLogPrefix() {
        return LOG_PREFIX;
    }
}
