/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.tools;

import com.atomforge.fdo.codegen.CodeGenConfig;
import com.atomforge.fdo.codegen.DslCodeGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * One-time tool to generate DSL code for 69-420.fdo.txt.
 *
 * Run this to see the generated DSL code, then adapt it for
 * the Gid69_420FdoBuilder class.
 */
public class GenerateDsl69420 {

    public static void main(String[] args) throws Exception {
        // Load the FDO source from resources
        String fdoSource = loadResource("replace_client_fdo/69-420.fdo.txt");

        System.out.println("=== Original FDO Source ===");
        System.out.println(fdoSource);
        System.out.println();

        // Generate DSL code
        DslCodeGenerator generator = new DslCodeGenerator();
        String dslCode = generator.generate(
            fdoSource,
            CodeGenConfig.fullClass("com.dialtone.fdo.dsl.builders", "Gid69_420FdoBuilder")
        );

        System.out.println("=== Generated DSL Code ===");
        System.out.println(dslCode);
    }

    private static String loadResource(String path) throws IOException {
        try (InputStream in = GenerateDsl69420.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
