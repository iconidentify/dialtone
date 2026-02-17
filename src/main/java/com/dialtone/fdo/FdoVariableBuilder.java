/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo;

import com.dialtone.protocol.SessionContext;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Fluent builder for creating FDO template variables with proper null safety.
 * Provides centralized variable management and consistent naming conventions.
 */
public class FdoVariableBuilder {

    private final Map<String, String> variables = new HashMap<>();
    private static final String DEFAULT_GUEST_NAME = "Guest";

    /**
     * Add session-based variables (username, authentication status).
     * Handles null safety with appropriate fallbacks.
     *
     * @param session User session context (may be null)
     * @return this builder for chaining
     */
    public FdoVariableBuilder withSession(SessionContext session) {
        String username = getUsername(session);

        variables.put("USERNAME", username);
        variables.put("SCREEN_NAME", username);
        variables.put("SCREENNAME", username);  // Alias without underscore
        variables.put("IS_AUTHENTICATED", String.valueOf(
            session != null && session.isAuthenticated()));

        return this;
    }

    /**
     * Add today's date variable formatted as "Monday 02/13/24".
     *
     * @return this builder for chaining
     */
    public FdoVariableBuilder withTodaysDate() {
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE MM/dd/yy");
        String formattedDate = today.format(formatter);
        variables.put("TODAYS_DATE", formattedDate);
        return this;
    }

    /**
     * Add MOTD-specific variables.
     *
     * @param title Main title text
     * @param body Body content
     * @return this builder for chaining
     */
    public FdoVariableBuilder withMotd(String title, String body) {
        variables.put("TITLE", title != null ? title : "");
        variables.put("BODY", body != null ? body : "");
        return this;
    }

    /**
     * Add Terms of Service content from public/TOS.txt.
     *
     * @return this builder for chaining
     */
    public FdoVariableBuilder withTos() {
        try {
            // Load TOS content directly without loadStaticFile's \r\r\r conversion
            // Let hex conversion handle newlines cleanly
            String tosContent = loadRawStaticFile("public/TOS.txt");
            variables.put("TOS_DATA", tosContent);
        } catch (Exception e) {
            // Fallback to default message if TOS.txt cannot be loaded
            variables.put("TOS_DATA", "Terms of Service content is temporarily unavailable.");
        }
        return this;
    }

    /**
     * Load a static file without AOL protocol newline conversion.
     * Used for content that will be hex-encoded where we want clean newlines.
     */
    private String loadRawStaticFile(String filename) throws java.io.IOException {
        String resourcePath = "static/" + filename;
        try (java.io.InputStream inputStream = FdoVariableBuilder.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new java.io.IOException("Static file not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }


    /**
     * Add news headline variable.
     *
     * @param headline News headline (12-15 words, descriptive summary of top story)
     * @return this builder for chaining
     */
    public FdoVariableBuilder withNewsHeadline(String headline) {
        variables.put("NEWS_HEADLINE", headline != null ? headline : "News unavailable");
        return this;
    }

    /**
     * Add entertainment headline variable.
     *
     * @param headline Entertainment headline (12-15 words, engaging and descriptive)
     * @return this builder for chaining
     */
    public FdoVariableBuilder withEntertainmentHeadline(String headline) {
        variables.put("ENTERTAINMENT_HEADLINE", headline != null ? headline : "Entertainment unavailable");
        return this;
    }

    /**
     * Add financial news headline variable.
     *
     * @param headline Financial news headline (10-12 words, concise to fit alongside BTC price)
     * @return this builder for chaining
     */
    public FdoVariableBuilder withFinancialNewsHeadline(String headline) {
        variables.put("FINANCIAL_HEADLINE", headline != null ? headline : "Financial news unavailable");
        return this;
    }

    /**
     * Add sports headline variable.
     *
     * @param headline Sports headline (12-15 words, includes score or result)
     * @return this builder for chaining
     */
    public FdoVariableBuilder withSportsHeadline(String headline) {
        variables.put("SPORTS_HEADLINE", headline != null ? headline : "Sports unavailable");
        return this;
    }

    /**
     * Add tech/AI news headline variable.
     *
     * @param headline Tech news headline (12-15 words, AI/ML breakthroughs and innovation)
     * @return this builder for chaining
     */
    public FdoVariableBuilder withTechHeadline(String headline) {
        variables.put("TECH_HEADLINE", headline != null ? headline : "Tech news unavailable");
        return this;
    }

    /**
     * Add full story variables for news story popup window.
     *
     * @param windowTitle Window title (e.g., "News Update", "Sports Update")
     * @param headline Category headline for display
     * @param fullReport Full report text content
     * @return this builder for chaining
     */
    public FdoVariableBuilder withFullStory(String windowTitle, String headline, String fullReport) {
        variables.put("WINDOW_TITLE", windowTitle != null ? windowTitle : "Story");
        variables.put("CATEGORY_HEADLINE", headline != null ? headline : "");
        variables.put("FULLREPORT_DATA", fullReport != null ? fullReport : "Content unavailable");
        return this;
    }

    /**
     * Add custom variable.
     *
     * @param key Variable name (will be used as {{KEY}} in template)
     * @param value Variable value
     * @return this builder for chaining
     */
    public FdoVariableBuilder with(String key, String value) {
        variables.put(key, value != null ? value : "");
        return this;
    }

    /**
     * Add button theme variables from properties.
     * Enables consistent button styling across all FDO interfaces.
     *
     * <p>Template variables created:
     * <ul>
     *   <li>{{BUTTON_COLOR_FACE}} - main button background color</li>
     *   <li>{{BUTTON_COLOR_TEXT}} - button label text color</li>
     *   <li>{{BUTTON_COLOR_TOP_EDGE}} - 3D highlight edge color</li>
     *   <li>{{BUTTON_COLOR_BOTTOM_EDGE}} - 3D shadow edge color</li>
     * </ul>
     *
     * @param props Properties containing button.color.* settings
     * @return this builder for chaining
     */
    public FdoVariableBuilder withButtonTheme(Properties props) {
        variables.put("BUTTON_COLOR_FACE",
            props.getProperty("button.color.face", "252, 157, 44"));
        variables.put("BUTTON_COLOR_TEXT",
            props.getProperty("button.color.text", "0, 30, 55"));
        variables.put("BUTTON_COLOR_TOP_EDGE",
            props.getProperty("button.color.top.edge", "255, 200, 100"));
        variables.put("BUTTON_COLOR_BOTTOM_EDGE",
            props.getProperty("button.color.bottom.edge", "150, 90, 20"));
        return this;
    }

    /**
     * Build and return the variables map.
     *
     * @return Immutable copy of variables
     */
    public Map<String, String> build() {
        return new HashMap<>(variables);
    }

    /**
     * Build and return the variables map as Map<String, Object>.
     * This allows the result to be used with byte[] values for template substitution.
     *
     * @return Immutable copy of variables as Object values
     */
    public Map<String, Object> buildAsObjects() {
        return new HashMap<>(variables);
    }

    /**
     * Extract username from session with null safety.
     *
     * @param session Session context (may be null)
     * @return Username or default guest name
     */
    private String getUsername(SessionContext session) {
        if (session == null) {
            return DEFAULT_GUEST_NAME;
        }

        String username = session.getUsername();
        if (username != null && !username.trim().isEmpty()) {
            return username;
        }

        // For unauthenticated sessions, generate unique guest name
        if (!session.isAuthenticated()) {
            return generateGuestName(session);
        }

        // Authenticated but no username - fallback
        return DEFAULT_GUEST_NAME;
    }

    /**
     * Generate unique guest name for unauthenticated sessions.
     *
     * @param session Session to derive guest name from
     * @return Unique guest name
     */
    private String generateGuestName(SessionContext session) {
        return DEFAULT_GUEST_NAME + Integer.toHexString(session.hashCode()).toUpperCase();
    }
}
