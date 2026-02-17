/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.migration;

import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.DodResponseFdoBuilder;
import com.dialtone.fdo.dsl.builders.F1AtomStreamResponseFdoBuilder;
import com.dialtone.fdo.dsl.builders.F2IdbResponseFdoBuilder;
import org.junit.jupiter.api.Test;

class DslDebugTest {

    @Test
    void printDodResponseSource() {
        DodResponseFdoBuilder builder = new DodResponseFdoBuilder(
            42, "32-1234", FdoGid.of(1, 0, 5678), "HELLO".getBytes());
        String source = builder.toSource(RenderingContext.DEFAULT);
        System.out.println("=== DodResponse DSL Output ===");
        System.out.println(source);
        System.out.println("=== END ===");
    }

    @Test
    void printF2IdbResponseSource() {
        F2IdbResponseFdoBuilder builder = new F2IdbResponseFdoBuilder(
            "p", FdoGid.of(69, 420), 100, new byte[]{(byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE});
        String source = builder.toSource(RenderingContext.DEFAULT);
        System.out.println("=== F2IdbResponse DSL Output ===");
        System.out.println(source);
        System.out.println("=== END ===");
    }

    @Test
    void printF1AtomStreamResponseSource() {
        F1AtomStreamResponseFdoBuilder builder = new F1AtomStreamResponseFdoBuilder(
            FdoGid.of(32, 256), 50, new byte[]{(byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF});
        String source = builder.toSource(RenderingContext.DEFAULT);
        System.out.println("=== F1AtomStreamResponse DSL Output ===");
        System.out.println(source);
        System.out.println("=== END ===");
    }
}
