/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol.xfer;

import com.dialtone.aol.core.ProtocolConstants;
import com.dialtone.protocol.PacketType;
import com.dialtone.protocol.xfer.XferEncoder;
import com.dialtone.protocol.xfer.XferFrameBuilder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XFER frame building.
 */
class XferFrameBuilderTest {

    @Test
    void shouldBuildValidFrameWithMagicByte() {
        byte[] payload = new byte[] { 0x01, 0x02, 0x03 };
        byte[] frame = XferFrameBuilder.buildFrame(
            XferFrameBuilder.TOKEN_TJ_HI,
            XferFrameBuilder.TOKEN_TJ_LO,
            payload
        );

        assertEquals((byte) ProtocolConstants.AOL_FRAME_MAGIC, frame[ProtocolConstants.IDX_MAGIC]);
    }

    @Test
    void shouldSetDataPacketType() {
        byte[] frame = XferFrameBuilder.buildFrame(
            XferFrameBuilder.TOKEN_TJ_HI,
            XferFrameBuilder.TOKEN_TJ_LO,
            new byte[0]
        );

        assertEquals((byte) PacketType.DATA.getValue(), frame[ProtocolConstants.IDX_TYPE]);
    }

    @Test
    void shouldSetTokenBytes() {
        byte[] frame = XferFrameBuilder.buildFrame(
            XferFrameBuilder.TOKEN_TJ_HI,
            XferFrameBuilder.TOKEN_TJ_LO,
            new byte[0]
        );

        assertEquals(XferFrameBuilder.TOKEN_TJ_HI, frame[ProtocolConstants.IDX_TOKEN]);
        assertEquals(XferFrameBuilder.TOKEN_TJ_LO, frame[ProtocolConstants.IDX_TOKEN + 1]);
        assertEquals("tj", XferFrameBuilder.getTokenString(frame));
    }

    @Test
    void shouldTerminateFrameWithCR() {
        byte[] frame = XferFrameBuilder.buildFrame(
            XferFrameBuilder.TOKEN_TJ_HI,
            XferFrameBuilder.TOKEN_TJ_LO,
            new byte[] { 0x01, 0x02 }
        );

        assertEquals(0x0D, frame[frame.length - 1]);
    }

    @Test
    void shouldNotDoubleTerminate() {
        byte[] payload = new byte[] { 0x01, 0x02, 0x0D };  // Already ends with CR
        byte[] frame = XferFrameBuilder.buildFrame(
            XferFrameBuilder.TOKEN_TJ_HI,
            XferFrameBuilder.TOKEN_TJ_LO,
            payload
        );

        // Should not add extra terminator
        assertEquals(ProtocolConstants.MIN_FULL_FRAME_SIZE + payload.length, frame.length);
        assertEquals(0x0D, frame[frame.length - 1]);
    }

    @Test
    void shouldBuildTjStructWithCorrectSize() {
        byte[] struct = XferFrameBuilder.buildTjStruct(
            0x00,
            new byte[] { 0x01, 0x02, 0x03 },
            1234567890,
            5,
            "Applications",
            "Tiny example file"
        );

        assertEquals(XferFrameBuilder.TJ_IN_SIZE, struct.length);
    }

    @Test
    void shouldSetTjFileId() {
        byte[] fileId = new byte[] { 0x11, 0x22, 0x33 };
        byte[] struct = XferFrameBuilder.buildTjStruct(
            0x00, fileId, 0, 0, "", ""
        );

        assertEquals(0x11, struct[1]);
        assertEquals(0x22, struct[2]);
        assertEquals(0x33, struct[3]);
    }

    @Test
    void shouldSetTjFileSizeBigEndian() {
        int fileSize = 0x12345678;
        byte[] struct = XferFrameBuilder.buildTjStruct(
            0x00, new byte[3], 0, fileSize, "", ""
        );

        // Big-endian at bytes 8-11
        assertEquals((byte) 0x12, struct[8]);
        assertEquals((byte) 0x34, struct[9]);
        assertEquals((byte) 0x56, struct[10]);
        assertEquals((byte) 0x78, struct[11]);
    }

    @Test
    void shouldSetTjLibraryAndSubject() {
        byte[] struct = XferFrameBuilder.buildTjStruct(
            0x00, new byte[3], 0, 5, "TestLib", "TestSubject"
        );

        // Library starts at offset 12
        assertEquals('T', struct[12]);
        assertEquals('e', struct[13]);
        assertEquals('s', struct[14]);
        assertEquals('t', struct[15]);
        assertEquals('L', struct[16]);
        assertEquals('i', struct[17]);
        assertEquals('b', struct[18]);
        assertEquals(0x00, struct[19]); // Null separator

        // Subject follows
        assertEquals('T', struct[20]);
        assertEquals('e', struct[21]);
        assertEquals('s', struct[22]);
        assertEquals('t', struct[23]);
    }

    @Test
    void shouldBuildCompleteTjFrame() {
        byte[] frame = XferFrameBuilder.buildTjFrame(
            0x00,
            new byte[] { 0x01, 0x02, 0x03 },
            0,
            5,
            "Applications",
            "Test file"
        );

        // Should be header (10) + TJ_IN (67) + terminator (1) = 78 bytes
        assertEquals(78, frame.length);
        assertEquals("tj", XferFrameBuilder.getTokenString(frame));
    }

    @Test
    void shouldBuildTfStructWithCorrectSize() {
        byte[] struct = XferFrameBuilder.buildTfStruct(
            XferFrameBuilder.TF_FLAG_PROGRESS_METER,
            5,
            0,
            0,
            "tiny.txt"
        );

        assertEquals(XferFrameBuilder.TF_IN_SIZE, struct.length);
    }

    @Test
    void shouldSetTfFlags() {
        byte[] struct = XferFrameBuilder.buildTfStruct(
            XferFrameBuilder.TF_FLAG_PROGRESS_METER,
            0, 0, 0, ""
        );

        assertEquals(XferFrameBuilder.TF_FLAG_PROGRESS_METER, struct[0]);
    }

    @Test
    void shouldSetTfFileSizeLittleEndian() {
        int fileSize = 0x00ABCDEF;
        byte[] struct = XferFrameBuilder.buildTfStruct(
            0, fileSize, 0, 0, ""
        );

        // Little-endian at bytes 1-3
        assertEquals((byte) 0xEF, struct[1]);
        assertEquals((byte) 0xCD, struct[2]);
        assertEquals((byte) 0xAB, struct[3]);
    }

    @Test
    void shouldSetTfFilename() {
        byte[] struct = XferFrameBuilder.buildTfStruct(
            0, 0, 0, 0, "tiny.txt"
        );

        // Filename starts at offset 19
        assertEquals('t', struct[19]);
        assertEquals('i', struct[20]);
        assertEquals('n', struct[21]);
        assertEquals('y', struct[22]);
        assertEquals('.', struct[23]);
        assertEquals('t', struct[24]);
        assertEquals('x', struct[25]);
        assertEquals('t', struct[26]);

        // NUL terminator after filename, then SEP_CHAR
        // Layout: [filename][0x00][0x90] - Windows client does name[FindByte(SEP)-1]=0
        // which writes to the NUL position (no-op, already NUL)
        assertEquals(0x00, struct[27]);
        assertEquals(XferFrameBuilder.TF_SEP_CHAR, struct[28]);
    }

    @Test
    void shouldBuildCompleteTfFrame() {
        byte[] frame = XferFrameBuilder.buildTfFrame(
            XferFrameBuilder.TF_FLAG_PROGRESS_METER,
            5,
            0,
            0,
            "tiny.txt"
        );

        // Should be header (10) + TF_IN (87) + terminator (1) = 98 bytes
        assertEquals(98, frame.length);
        assertEquals("tf", XferFrameBuilder.getTokenString(frame));
    }

    @Test
    void shouldBuildF7DataFrame() {
        byte[] data = "test".getBytes(StandardCharsets.US_ASCII);
        byte[] encoded = XferEncoder.encode(data);
        byte[] frame = XferFrameBuilder.buildDataFrame(encoded, false);

        assertEquals("F7", XferFrameBuilder.getTokenString(frame));
        assertEquals(0x0D, frame[frame.length - 1]);
    }

    @Test
    void shouldBuildF9DataFrame() {
        byte[] data = "tiny\n".getBytes(StandardCharsets.US_ASCII);
        byte[] encoded = XferEncoder.encode(data);
        byte[] frame = XferFrameBuilder.buildDataFrame(encoded, true);

        assertEquals("F9", XferFrameBuilder.getTokenString(frame));
        assertEquals(0x0D, frame[frame.length - 1]);

        // Data should be at offset 10
        assertEquals('t', frame[10]);
        assertEquals('i', frame[11]);
        assertEquals('n', frame[12]);
        assertEquals('y', frame[13]);
        assertEquals('\n', frame[14]);
    }

    @Test
    void shouldHandleTinyFileMvpScenario() {
        // The exact MVP scenario: tiny.txt with "tiny\n" (5 bytes)
        byte[] fileData = "tiny\n".getBytes(StandardCharsets.US_ASCII);
        assertEquals(5, fileData.length);

        byte[] fileId = new byte[] { 0x00, 0x01, 0x02 };
        int timestamp = (int) (System.currentTimeMillis() / 1000);

        // Build tj frame
        byte[] tjFrame = XferFrameBuilder.buildTjFrame(
            0x00,
            fileId,
            timestamp,
            5,
            "Applications",
            "Tiny example file"
        );
        assertEquals("tj", XferFrameBuilder.getTokenString(tjFrame));

        // Build tf frame
        byte[] tfFrame = XferFrameBuilder.buildTfFrame(
            XferFrameBuilder.TF_FLAG_PROGRESS_METER,
            5,
            timestamp,
            timestamp,
            "tiny.txt"
        );
        assertEquals("tf", XferFrameBuilder.getTokenString(tfFrame));

        // Build F9 frame (final data)
        byte[] encoded = XferEncoder.encode(fileData);
        byte[] f9Frame = XferFrameBuilder.buildDataFrame(encoded, true);
        assertEquals("F9", XferFrameBuilder.getTokenString(f9Frame));

        // All frames should have valid magic byte
        assertEquals((byte) 0x5A, tjFrame[0]);
        assertEquals((byte) 0x5A, tfFrame[0]);
        assertEquals((byte) 0x5A, f9Frame[0]);
    }
}
