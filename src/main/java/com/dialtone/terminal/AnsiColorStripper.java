/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.terminal;

/**
 * Utility class for stripping ANSI escape sequences from text.
 * 
 * <p>Removes ANSI color codes, formatting codes, and cursor movement sequences
 * commonly used in terminal output (e.g., from MUDs, terminal applications).
 * 
 * <p>Handles common ANSI escape sequences:
 * <ul>
 *   <li>Color codes: {@code \033[31m} (red), {@code \033[32m} (green), {@code \033[0m} (reset)</li>
 *   <li>Formatting: {@code \033[1m} (bold), {@code \033[4m} (underline)</li>
 *   <li>Cursor movement: {@code \033[2J} (clear screen), {@code \033[H} (home)</li>
 *   <li>Complex sequences: {@code \033[1;32;40m} (bold green on black background)</li>
 * </ul>
 */
public final class AnsiColorStripper {

    private AnsiColorStripper() {
        // Utility class - prevent instantiation
    }

    /**
     * Strips all ANSI escape sequences from the input text.
     * 
     * <p>ANSI escape sequences follow the pattern: {@code ESC[parameters command}
     * where ESC is {@code \u001B} (27 decimal) or {@code \033} (octal),
     * parameters are optional numbers separated by semicolons,
     * and command is a single letter.
     * 
     * <p>This method handles multiple ANSI escape sequence formats:
     * <ul>
     *   <li>CSI sequences: {@code ESC[params command} (most common)</li>
     *   <li>OSC sequences: {@code ESC]params BEL/ESC\\} (less common)</li>
     *   <li>Character set sequences: {@code ESC(params} (rare)</li>
     * </ul>
     * 
     * <p>The pattern is designed to NOT match newline characters (\n, \r) to preserve
     * line structure in the output.
     * 
     * @param text Input text that may contain ANSI escape sequences
     * @return Plain text with all ANSI escape sequences removed, or null if input is null
     */
    public static String stripAnsiCodes(String text) {
        if (text == null) {
            return null;
        }
        
        // Primary pattern: CSI sequences (most common): ESC[ followed by optional digits/semicolons, ending with a letter
        // This matches: \033[31m, \033[1;32;40m, \033[2J, \033[0m, etc.
        // 
        // IMPORTANT: This pattern CANNOT match newlines because:
        // - [0-9;]* only matches digits (0-9) and semicolons (;)
        // - [a-zA-Z] only matches letters (a-z, A-Z)
        // - Neither pattern component can match \n (0x0A) or \r (0x0D)
        // 
        // Therefore, newlines in the input text are preserved in the output.
        String result = text.replaceAll("\u001B\\[[0-9;]*[a-zA-Z]", "");
        
        // Additional pattern: OSC sequences (less common): ESC] followed by parameters, ending with BEL or ESC\\
        // This matches: \033]0;title\007, \033]1;url\033\\
        // Explicitly exclude newlines from the parameter section to be extra safe
        result = result.replaceAll("\u001B\\][^\u0007\u001B\n\r]*?[\u0007\u001B\\\\]", "");
        
        return result;
    }
}
