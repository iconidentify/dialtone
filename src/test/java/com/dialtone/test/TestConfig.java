/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Centralized test configuration utility.
 * Provides access to test configuration values from test.properties file.
 * Supports system property overrides for CI/CD environments.
 */
public class TestConfig {
    private static final String CONFIG_FILE = "/test.properties";
    private static Properties properties;

    static {
        properties = new Properties();
        try (InputStream input = TestConfig.class.getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(input);
            } else {
                // Fallback to default values if file not found
                properties.setProperty("atomforge.base.url", "http://localhost:8000");
                properties.setProperty("atomforge.timeout.ms", "5000");
            }
        } catch (IOException e) {
            // Fallback to default values if loading fails
            properties.setProperty("atomforge.base.url", "http://localhost:8000");
            properties.setProperty("atomforge.timeout.ms", "5000");
        }
    }

    /**
     * Get atomforge base URL with system property override support.
     * @return The atomforge base URL
     */
    public static String getAtomforgeUrl() {
        return System.getProperty("atomforge.base.url",
            properties.getProperty("atomforge.base.url", "http://localhost:8000"));
    }

    /**
     * Get atomforge timeout in milliseconds with system property override support.
     * @return The timeout value in milliseconds
     */
    public static int getAtomforgeTimeout() {
        return Integer.parseInt(System.getProperty("atomforge.timeout.ms",
            properties.getProperty("atomforge.timeout.ms", "5000")));
    }

    /**
     * Get all test properties with system property overrides applied.
     * @return Properties object containing all test configuration
     */
    public static Properties getAllProperties() {
        Properties allProps = new Properties();
        allProps.putAll(properties);

        // Override with system properties if set
        for (String key : properties.stringPropertyNames()) {
            String systemValue = System.getProperty(key);
            if (systemValue != null) {
                allProps.setProperty(key, systemValue);
            }
        }

        return allProps;
    }

    /**
     * Get a specific property by key with system property override support.
     * @param key The property key
     * @param defaultValue Default value if property not found
     * @return The property value
     */
    public static String getProperty(String key, String defaultValue) {
        return System.getProperty(key, properties.getProperty(key, defaultValue));
    }
}
