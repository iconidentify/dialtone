/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo.dsl;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.EphemeralWelcomeFdoBuilder;
import com.dialtone.protocol.ClientPlatform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EphemeralWelcomeFdoBuilder")
class EphemeralWelcomeFdoBuilderTest {

    private FdoCompiler compiler;

    public EphemeralWelcomeFdoBuilderTest() {
        Properties props = new Properties();
        compiler = new FdoCompiler(props);
    }

    @Test
    @DisplayName("Should generate FDO source without format errors")
    void shouldGenerateFdoSourceWithoutFormatErrors() {
        EphemeralWelcomeFdoBuilder builder = new EphemeralWelcomeFdoBuilder("~Guest1234");
        String source = builder.toSource(RenderingContext.DEFAULT);
        
        assertNotNull(source);
        assertFalse(source.isEmpty());
        
        // Should not contain format specifier errors
        assertFalse(source.contains("%s"), 
            "FDO source should not contain unsubstituted format specifiers");
        
        // Should contain the screenname
        assertTrue(source.contains("~Guest1234"), 
            "FDO source should contain the guest screenname");
    }

    @Test
    @DisplayName("Should compile to valid binary chunks")
    void shouldCompileToValidBinaryChunks() throws Exception {
        String fdoSource = EphemeralWelcomeFdoBuilder.generateSource(
            "~Guest5678", RenderingContext.DEFAULT);
        
        java.util.List<FdoChunk> chunks = compiler.compileFdoScriptToP3Chunks(
            fdoSource, "At", -1);
        
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());
        
        // Verify chunks have binary data
        for (FdoChunk chunk : chunks) {
            assertNotNull(chunk.getBinaryData());
            assertTrue(chunk.getBinaryData().length > 0);
        }
    }

    @Test
    @DisplayName("Should handle various screenname formats")
    void shouldHandleVariousScreennameFormats() {
        String[] screennames = {
            "~Guest1234",
            "~GuestABCD",
            "~Guest9999",
            "~Guest0001"
        };
        
        for (String screenname : screennames) {
            String source = EphemeralWelcomeFdoBuilder.generateSource(
                screenname, RenderingContext.DEFAULT);
            
            assertNotNull(source);
            assertTrue(source.contains(screenname),
                "FDO source should contain screenname: " + screenname);
            
            // Should compile without errors
            assertDoesNotThrow(() -> {
                compiler.compileFdoScriptToP3Chunks(source, "At", -1);
            }, "Should compile for screenname: " + screenname);
        }
    }

    @Test
    @DisplayName("Should handle null screenname gracefully")
    void shouldHandleNullScreennameGracefully() {
        EphemeralWelcomeFdoBuilder builder = new EphemeralWelcomeFdoBuilder(null);
        String source = builder.toSource(RenderingContext.DEFAULT);
        
        assertNotNull(source);
        // Should use default "Guest" placeholder
        assertTrue(source.contains("Guest") || source.contains("guest"));
    }

    @Test
    @DisplayName("Should handle empty screenname gracefully")
    void shouldHandleEmptyScreennameGracefully() {
        // Empty string is kept as-is (not converted to "Guest" - only null is)
        // But the FDO should still compile without format errors
        EphemeralWelcomeFdoBuilder builder = new EphemeralWelcomeFdoBuilder("");
        String source = builder.toSource(RenderingContext.DEFAULT);
        
        assertNotNull(source);
        assertFalse(source.isEmpty());
        
        // Should compile successfully (empty string in format is valid)
        assertDoesNotThrow(() -> {
            compiler.compileFdoScriptToP3Chunks(source, "At", -1);
        }, "Should compile FDO with empty screenname");
    }

    @Test
    @DisplayName("Message should contain registration instructions")
    void messageShouldContainRegistrationInstructions() {
        String source = EphemeralWelcomeFdoBuilder.generateSource(
            "~Guest1234", RenderingContext.DEFAULT);
        
        // Should mention dialtone.live
        assertTrue(source.contains("dialtone.live") || 
                   source.toLowerCase().contains("dialtone"),
            "FDO source should mention dialtone.live");
        
        // Should mention permanent screenname
        assertTrue(source.contains("permanent") || 
                   source.contains("Permanent"),
            "FDO source should mention permanent screenname");
    }

    @Test
    @DisplayName("Should work with different rendering contexts")
    void shouldWorkWithDifferentRenderingContexts() {
        String screenname = "~Guest1234";
        
        RenderingContext[] contexts = {
            RenderingContext.DEFAULT,
            new RenderingContext(ClientPlatform.WINDOWS, false),
            new RenderingContext(ClientPlatform.MAC, false),
            new RenderingContext(ClientPlatform.UNKNOWN, true)
        };
        
        for (RenderingContext ctx : contexts) {
            String source = EphemeralWelcomeFdoBuilder.generateSource(screenname, ctx);
            assertNotNull(source);
            assertFalse(source.isEmpty());
            
            // Should compile for all contexts
            assertDoesNotThrow(() -> {
                compiler.compileFdoScriptToP3Chunks(source, "At", -1);
            }, "Should compile for context: " + ctx);
        }
    }
}

