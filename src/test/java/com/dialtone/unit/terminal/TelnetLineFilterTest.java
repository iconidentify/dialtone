/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.terminal;

import com.dialtone.terminal.TelnetLineFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TelnetLineFilter")
class TelnetLineFilterTest {

    @Nested
    @DisplayName("passThrough")
    class PassThrough {

        @Test
        @DisplayName("Should return line unchanged")
        void shouldReturnLineUnchanged() {
            TelnetLineFilter filter = TelnetLineFilter.passThrough();

            assertEquals("Hello World", filter.filter("Hello World"));
            assertEquals("", filter.filter(""));
            assertEquals("Special chars: !@#$%", filter.filter("Special chars: !@#$%"));
        }
    }

    @Nested
    @DisplayName("Custom Filters")
    class CustomFilters {

        @Test
        @DisplayName("Should suppress lines matching pattern")
        void shouldSuppressLinesMatchingPattern() {
            TelnetLineFilter filter = line -> line.startsWith("SECRET") ? null : line;

            assertNull(filter.filter("SECRET - abc123"));
            assertEquals("Normal line", filter.filter("Normal line"));
        }

        @Test
        @DisplayName("Should modify lines")
        void shouldModifyLines() {
            TelnetLineFilter filter = line -> line.toUpperCase();

            assertEquals("HELLO", filter.filter("hello"));
            assertEquals("MIXED CASE", filter.filter("Mixed Case"));
        }

        @Test
        @DisplayName("Should handle empty lines")
        void shouldHandleEmptyLines() {
            TelnetLineFilter filter = line -> line.isEmpty() ? null : line;

            assertNull(filter.filter(""));
            assertEquals("non-empty", filter.filter("non-empty"));
        }
    }

    @Nested
    @DisplayName("andThen")
    class AndThen {

        @Test
        @DisplayName("Should chain filters in order")
        void shouldChainFiltersInOrder() {
            TelnetLineFilter first = line -> line + "-first";
            TelnetLineFilter second = line -> line + "-second";

            TelnetLineFilter combined = first.andThen(second);

            assertEquals("test-first-second", combined.filter("test"));
        }

        @Test
        @DisplayName("Should propagate null through chain")
        void shouldPropagateNullThroughChain() {
            TelnetLineFilter suppressor = line -> line.startsWith("SKIP") ? null : line;
            TelnetLineFilter modifier = line -> line.toUpperCase();

            TelnetLineFilter combined = suppressor.andThen(modifier);

            assertNull(combined.filter("SKIP this line"));
            assertEquals("NORMAL LINE", combined.filter("normal line"));
        }

        @Test
        @DisplayName("Should support multiple chained filters")
        void shouldSupportMultipleChainedFilters() {
            TelnetLineFilter addA = line -> line + "A";
            TelnetLineFilter addB = line -> line + "B";
            TelnetLineFilter addC = line -> line + "C";

            TelnetLineFilter combined = addA.andThen(addB).andThen(addC);

            assertEquals("testABC", combined.filter("test"));
        }

        @Test
        @DisplayName("Should work with passThrough in chain")
        void shouldWorkWithPassThroughInChain() {
            TelnetLineFilter modifier = line -> "[" + line + "]";

            TelnetLineFilter combined = TelnetLineFilter.passThrough().andThen(modifier);

            assertEquals("[hello]", combined.filter("hello"));
        }
    }

    @Nested
    @DisplayName("Functional Interface")
    class FunctionalInterface {

        @Test
        @DisplayName("Should work with lambda expressions")
        void shouldWorkWithLambdas() {
            TelnetLineFilter filter = line -> line.length() > 5 ? line : null;

            assertNull(filter.filter("short"));
            assertEquals("longer text", filter.filter("longer text"));
        }

        @Test
        @DisplayName("Should work with method references")
        void shouldWorkWithMethodReferences() {
            TelnetLineFilter filter = String::trim;

            assertEquals("trimmed", filter.filter("  trimmed  "));
        }
    }

    @Nested
    @DisplayName("Real World Scenarios")
    class RealWorldScenarios {

        @Test
        @DisplayName("AUTH line filter should suppress AUTH and pass other lines")
        void authLineFilterShouldSuppressAuthLines() {
            // Simulate the AUTH line filter used in SkalholtTokenHandler
            TelnetLineFilter authFilter = line -> {
                if (line != null && line.startsWith("AUTH - ")) {
                    // Would capture token here in real implementation
                    return null; // Suppress
                }
                return line;
            };

            assertNull(authFilter.filter("AUTH - base64token"));
            assertEquals("Welcome to the game!", authFilter.filter("Welcome to the game!"));
            assertEquals("You enter the tavern.", authFilter.filter("You enter the tavern."));
        }

        @Test
        @DisplayName("Combined ANSI stripping and AUTH filter")
        void combinedAnsiAndAuthFilter() {
            // Simulate combining ANSI stripper with AUTH filter
            TelnetLineFilter stripAnsi = line -> line.replaceAll("\u001B\\[[;\\d]*m", "");
            TelnetLineFilter suppressAuth = line -> line.startsWith("AUTH - ") ? null : line;

            TelnetLineFilter combined = stripAnsi.andThen(suppressAuth);

            // ANSI codes stripped, then AUTH checked
            assertEquals("Normal text", combined.filter("\u001B[32mNormal text\u001B[0m"));
            assertNull(combined.filter("AUTH - token123"));
        }
    }
}
