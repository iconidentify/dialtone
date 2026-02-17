/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.atoms.UniAtom;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for the welcome screen after login.
 *
 * <p>Generates an FDO stream that displays the personalized welcome screen
 * with the user's screenname, news headlines in various categories, and
 * clickable icons that send token requests.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   uni_wait_on
 *   uni_invoke_local &lt;32-5447&gt;
 *   mat_title &lt;"Welcome, screenname!"&gt;
 *   man_set_context_globalid &lt;32-30&gt;
 *   man_make_focus
 *   [news headlines in relative contexts 3, 10, 12, 14, 16]
 *   [icon art + actions in contexts 17, 18, 19, 20, 21]
 *   man_force_update
 *   man_update_display
 *   sm_send_token_raw &lt;"TO"&gt;
 * uni_wait_off_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/welcome_screen.fdo.txt}</p>
 */
public final class WelcomeScreenFdoBuilder
        implements FdoDslBuilder, FdoBuilder.Dynamic<WelcomeScreenFdoBuilder.Config> {

    private static final String GID = "welcome_screen";
    private static final FdoGid INVOKE_GID = FdoGid.of(32, 5447);
    private static final FdoGid CONTEXT_GID = FdoGid.of(32, 30);

    // Art GIDs for category icons
    private static final FdoGid ART_TECH = FdoGid.of(1, 0, 21029);
    private static final FdoGid ART_NEWS = FdoGid.of(1, 0, 21030);
    private static final FdoGid ART_FINANCIAL = FdoGid.of(1, 0, 21016);
    private static final FdoGid ART_SPORTS = FdoGid.of(1, 0, 21031);
    private static final FdoGid ART_CULTURE = FdoGid.of(1, 0, 21025);

    /**
     * Configuration for welcome screen.
     *
     * @param screenname The user's screen name
     * @param techHeadline Tech news headline
     * @param newsHeadline General news headline
     * @param financialHeadline Financial news headline
     * @param sportsHeadline Sports headline
     * @param entertainmentHeadline Entertainment/culture headline
     */
    public record Config(
            String screenname,
            String techHeadline,
            String newsHeadline,
            String financialHeadline,
            String sportsHeadline,
            String entertainmentHeadline
    ) {
        public Config {
            if (screenname == null) screenname = "Guest";
            if (techHeadline == null) techHeadline = "";
            if (newsHeadline == null) newsHeadline = "";
            if (financialHeadline == null) financialHeadline = "";
            if (sportsHeadline == null) sportsHeadline = "";
            if (entertainmentHeadline == null) entertainmentHeadline = "";
        }
    }

    private final Config config;

    /**
     * Create a new builder with the given configuration.
     *
     * @param config The welcome screen configuration
     */
    public WelcomeScreenFdoBuilder(Config config) {
        this.config = config != null ? config : new Config("Guest", "", "", "", "", "");
    }

    /**
     * Create a new builder with individual parameters.
     */
    public WelcomeScreenFdoBuilder(String screenname, String tech, String news,
                                    String financial, String sports, String entertainment) {
        this(new Config(screenname, tech, news, financial, sports, entertainment));
    }

    /**
     * Factory method for type-safe builder creation.
     */
    public static WelcomeScreenFdoBuilder create(String screenname, String tech, String news,
                                                  String financial, String sports, String entertainment) {
        return new WelcomeScreenFdoBuilder(screenname, tech, news, financial, sports, entertainment);
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
        return "Welcome screen with personalized greeting and news headlines";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .uniStartStream()
                    .uniWaitOn()
                    .uniInvokeLocal(INVOKE_GID)
                    .matTitle("Welcome, " + config.screenname + "!")
                    .manSetContextGlobalId(CONTEXT_GID)
                    .manMakeFocus()
                    // Tech headline (context 3)
                    .manSetContextRelative(3)
                        .manReplaceData(formatHeadline("TECH", config.techHeadline))
                        .manEndData()
                    .manEndContext()
                    // Tech icon (context 21)
                    .manSetContextRelative(21)
                        .artId(ART_TECH)
                    .manEndContext()
                    .manSetContextRelative(21)
                        .actReplaceSelectAction(action -> {
                            action.atom(UniAtom.START_STREAM_WAIT_ON, "00x")
                                    .smSendTokenArg("NX5")
                                    .uniEndStream("00x");
                        })
                    .manEndContext()
                    // News headline (context 10)
                    .manSetContextRelative(10)
                        .manReplaceData(formatHeadline("NEWS", config.newsHeadline))
                    .manEndContext()
                    // Financial headline (context 12)
                    .manSetContextRelative(12)
                        .manReplaceData(formatHeadline("FINANCIAL", config.financialHeadline))
                    .manEndContext()
                    // Sports headline (context 14)
                    .manSetContextRelative(14)
                        .manReplaceData(formatHeadline("SPORTS", config.sportsHeadline))
                    .manEndContext()
                    // Culture headline (context 16)
                    .manSetContextRelative(16)
                        .manReplaceData(formatHeadline("CULTURE", config.entertainmentHeadline))
                    .manEndContext()
                    // News icon (context 17)
                    .manSetContextRelative(17)
                        .artId(ART_NEWS)
                    .manEndContext()
                    .manSetContextRelative(17)
                        .actReplaceSelectAction(action -> {
                            action.atom(UniAtom.START_STREAM_WAIT_ON, "00x")
                                    .smSendTokenArg("NX1")
                                    .uniEndStream("00x");
                        })
                    .manEndContext()
                    // Financial icon (context 18)
                    .manSetContextRelative(18)
                        .artId(ART_FINANCIAL)
                    .manEndContext()
                    .manSetContextRelative(18)
                        .actReplaceSelectAction(action -> {
                            action.atom(UniAtom.START_STREAM_WAIT_ON, "00x")
                                    .smSendTokenArg("NX3")
                                    .uniEndStream("00x");
                        })
                    .manEndContext()
                    // Sports icon (context 19)
                    .manSetContextRelative(19)
                        .artId(ART_SPORTS)
                    .manEndContext()
                    .manSetContextRelative(19)
                        .actReplaceSelectAction(action -> {
                            action.atom(UniAtom.START_STREAM_WAIT_ON, "00x")
                                    .smSendTokenArg("NX2")
                                    .uniEndStream("00x");
                        })
                    .manEndContext()
                    // Culture icon (context 20)
                    .manSetContextRelative(20)
                        .artId(ART_CULTURE)
                    .manEndContext()
                    .manSetContextRelative(20)
                        .actReplaceSelectAction(action -> {
                            action.atom(UniAtom.START_STREAM_WAIT_ON, "00x")
                                    .smSendTokenArg("NX4")
                                    .uniEndStream("00x");
                        })
                    .manEndContext()
                    .manForceUpdate()
                    .manUpdateDisplay()
                    .uniStartStream()
                        .smSendTokenRaw("TO")
                    .uniEndStream()
                .uniWaitOffEndStream()
                .toSource();
    }

    private static String formatHeadline(String category, String headline) {
        return "<HTML><P ALIGN=LEFT><FONT SIZE=2><b>" + category + "</b> " + headline + "</FONT></P><br></HTML>";
    }
}
