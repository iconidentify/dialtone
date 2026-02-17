/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;

/**
 * Immutable data class representing parsed INIT packet data from AOL 3.0 client.
 *
 * <p>INIT packets (0xA3) are sent by the client during connection handshake
 * and contain detailed information about the client's hardware, OS, and configuration.
 *
 * <p>This implementation follows the 32-bit Windows client layout (52-byte payload).
 *
 * <p>Field Layout (offsets relative to payload start):
 * <pre>
 * Offset  Length  Type       Field
 * 0x00    0x01    uint8      platform
 * 0x01    0x01    uint8      version_num
 * 0x02    0x01    uint8      sub_version_num
 * 0x03    0x01    uint8      unused
 * 0x04    0x01    uint8      machine_memory
 * 0x05    0x01    uint8      app_memory
 * 0x06    0x02    uint16     pc_type
 * 0x08    0x01    uint8      release_month
 * 0x09    0x01    uint8      release_day
 * 0x0A    0x02    uint16     customer_class
 * 0x0C    0x04    uint32     udo_timestamp
 * 0x10    0x02    uint16     dos_version
 * 0x12    0x02    uint16     session_flags
 * 0x14    0x01    uint8      video_type
 * 0x15    0x01    uint8      processor_type
 * 0x16    0x04    uint32     media_type
 * 0x1A    0x04    uint32     windows_version
 * 0x1E    0x01    uint8      memory_mode
 * 0x1F    0x02    uint16     horizontal_res
 * 0x21    0x02    uint16     vertical_res
 * 0x23    0x02    uint16     num_colors
 * 0x25    0x01    uint8      filler
 * 0x26    0x02    uint16     region
 * 0x28    0x08    uint16[4]  language
 * 0x30    0x01    uint8      connect_speed
 * </pre>
 */
public class InitPacketData {

    // Basic client info
    private final int platform;              // 0x00: Platform ID (1=Windows, 2=Mac, 3=DOS)
    private final int versionNum;            // 0x01: Major version number
    private final int subVersionNum;         // 0x02: Minor version number
    private final int unused;                // 0x03: Reserved/unused

    // Memory and hardware
    private final int machineMemory;         // 0x04: Machine RAM in MB
    private final int appMemory;             // 0x05: Application memory in MB
    private final int pcType;                // 0x06: PC type identifier

    // Build date
    private final int releaseMonth;          // 0x08: Client release month (1-12)
    private final int releaseDay;            // 0x09: Client release day (1-31)

    // Customer info
    private final int customerClass;         // 0x0A: Customer classification
    private final long udoTimestamp;         // 0x0C: UDO timestamp (Unix time)

    // OS info
    private final int dosVersion;            // 0x10: DOS version (if applicable)
    private final int sessionFlags;          // 0x12: Session configuration flags

    // Video/display
    private final int videoType;             // 0x14: Video adapter type
    private final int processorType;         // 0x15: CPU type
    private final long mediaType;            // 0x16: Media type identifier
    private final long windowsVersion;       // 0x1A: Windows version code
    private final int memoryMode;            // 0x1E: Memory mode

    // Resolution
    private final int horizontalRes;         // 0x1F: Horizontal resolution (pixels)
    private final int verticalRes;           // 0x21: Vertical resolution (pixels)
    private final int numColors;             // 0x23: Number of colors supported

    // Locale
    private final int filler;                // 0x25: Padding/filler byte
    private final int region;                // 0x26: Geographic region code
    private final int[] language;            // 0x28: Language codes (4 uint16s)

    // Connection
    private final int connectSpeed;          // 0x30: Connection speed indicator

    // Parsing metadata
    private final boolean fullyParsed;       // True if full 52-byte Windows layout was parsed
    private final int actualPayloadSize;     // Actual payload size in bytes

    /**
     * Create InitPacketData with all fields.
     * Package-private constructor - use InitPacketParser.parse() to create instances.
     */
    InitPacketData(
            int platform, int versionNum, int subVersionNum, int unused,
            int machineMemory, int appMemory, int pcType,
            int releaseMonth, int releaseDay,
            int customerClass, long udoTimestamp,
            int dosVersion, int sessionFlags,
            int videoType, int processorType,
            long mediaType, long windowsVersion, int memoryMode,
            int horizontalRes, int verticalRes, int numColors,
            int filler, int region, int[] language, int connectSpeed,
            boolean fullyParsed, int actualPayloadSize) {

        this.platform = platform;
        this.versionNum = versionNum;
        this.subVersionNum = subVersionNum;
        this.unused = unused;
        this.machineMemory = machineMemory;
        this.appMemory = appMemory;
        this.pcType = pcType;
        this.releaseMonth = releaseMonth;
        this.releaseDay = releaseDay;
        this.customerClass = customerClass;
        this.udoTimestamp = udoTimestamp;
        this.dosVersion = dosVersion;
        this.sessionFlags = sessionFlags;
        this.videoType = videoType;
        this.processorType = processorType;
        this.mediaType = mediaType;
        this.windowsVersion = windowsVersion;
        this.memoryMode = memoryMode;
        this.horizontalRes = horizontalRes;
        this.verticalRes = verticalRes;
        this.numColors = numColors;
        this.filler = filler;
        this.region = region;
        this.language = language != null ? language.clone() : new int[4];
        this.connectSpeed = connectSpeed;
        this.fullyParsed = fullyParsed;
        this.actualPayloadSize = actualPayloadSize;
    }

    // Basic client info
    public int getPlatform() { return platform; }
    public int getVersionNum() { return versionNum; }
    public int getSubVersionNum() { return subVersionNum; }
    public int getUnused() { return unused; }

    // Memory and hardware
    public int getMachineMemory() { return machineMemory; }
    public int getAppMemory() { return appMemory; }
    public int getPcType() { return pcType; }

    // Build date
    public int getReleaseMonth() { return releaseMonth; }
    public int getReleaseDay() { return releaseDay; }

    // Customer info
    public int getCustomerClass() { return customerClass; }
    public long getUdoTimestamp() { return udoTimestamp; }

    // OS info
    public int getDosVersion() { return dosVersion; }
    public int getSessionFlags() { return sessionFlags; }

    // Video/display
    public int getVideoType() { return videoType; }
    public int getProcessorType() { return processorType; }
    public long getMediaType() { return mediaType; }
    public long getWindowsVersion() { return windowsVersion; }
    public int getMemoryMode() { return memoryMode; }

    // Resolution
    public int getHorizontalRes() { return horizontalRes; }
    public int getVerticalRes() { return verticalRes; }
    public int getNumColors() { return numColors; }

    // Locale
    public int getFiller() { return filler; }
    public int getRegion() { return region; }
    public int[] getLanguage() { return language.clone(); }

    // Connection
    public int getConnectSpeed() { return connectSpeed; }

    // Parsing metadata
    public boolean isFullyParsed() { return fullyParsed; }
    public int getActualPayloadSize() { return actualPayloadSize; }

    /**
     * Get warning message if packet wasn't fully parsed.
     * Returns null if no warnings.
     *
     * @return Warning message or null
     */
    public String getWarningMessage() {
        if (fullyParsed) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("*** WARNING: Non-standard INIT packet size ***\\r");
        sb.append("Actual: ").append(actualPayloadSize).append(" bytes, ");
        sb.append("Expected: 52 bytes for full Windows layout.\\r");
        sb.append("Some fields may be unavailable or incorrect.");
        return sb.toString();
    }

    /**
     * Get human-readable platform name.
     *
     * @return Platform name (e.g., "Windows", "Macintosh", "DOS")
     */
    public String getPlatformName() {
        return switch (platform) {
            case 1 -> "Windows";
            case 2 -> "Macintosh";
            case 3 -> "DOS";
            default -> {
                // Platform values 127+ are also Macintosh
                if (platform >= 127) {
                    yield "Macintosh";
                }
                yield "Unknown (" + platform + ")";
            }
        };
    }

    /**
     * Get version string in format "major.minor".
     *
     * @return Version string (e.g., "3.0")
     */
    public String getVersionString() {
        return versionNum + "." + subVersionNum;
    }

    /**
     * Get resolution string with color depth.
     *
     * @return Resolution string (e.g., "800x600 (256 colors)")
     */
    public String getResolutionString() {
        return horizontalRes + "x" + verticalRes + " (" + numColors + " colors)";
    }

    /**
     * Get human-readable connection speed description.
     *
     * @return Connection speed description
     */
    public String getConnectSpeedDescription() {
        return switch (connectSpeed) {
            case 0x00 -> "Unknown";
            case 0x01 -> "9600 baud";
            case 0x02 -> "14400 baud";
            case 0x03 -> "28800 baud";
            case 0x04 -> "33600 baud";
            case 0x05 -> "56K modem";
            case 0x06 -> "ISDN";
            case 0x07 -> "Cable/DSL";
            default -> "Unknown (" + connectSpeed + ")";
        };
    }

    /**
     * Get human-readable processor type description.
     * Returns period-accurate names for known processors (e.g., PowerPC for Mac)
     * with raw hex value for debugging.
     *
     * @return Processor type description (e.g., "PowerPC (0x80)")
     */
    public String getProcessorTypeName() {
        return switch (processorType) {
            case 0x80 -> "PowerPC (0x80)";
            default -> String.format("Unknown (0x%02X)", processorType);
        };
    }

    @Override
    public String toString() {
        return String.format(
                "InitPacketData{platform=%s, version=%s, memory=%dMB, resolution=%s, speed=%s}",
                getPlatformName(),
                getVersionString(),
                machineMemory,
                getResolutionString(),
                getConnectSpeedDescription()
        );
    }

    /**
     * Get detailed string representation with all fields.
     *
     * @return Detailed multi-line string
     */
    public String toDetailedString() {
        return String.format(
                """
                InitPacketData:
                  Platform: %s (0x%02X)
                  Version: %s
                  Memory: Machine=%dMB, App=%dMB
                  PC Type: 0x%04X
                  Release Date: %d/%d
                  Customer Class: 0x%04X
                  UDO Timestamp: %d
                  DOS Version: 0x%04X
                  Session Flags: 0x%04X
                  Video Type: 0x%02X
                  Processor: 0x%02X
                  Media Type: 0x%08X
                  Windows Version: 0x%08X
                  Memory Mode: 0x%02X
                  Resolution: %dx%d (%d colors)
                  Region: 0x%04X
                  Language: [0x%04X, 0x%04X, 0x%04X, 0x%04X]
                  Connect Speed: %s (0x%02X)""",
                getPlatformName(), platform,
                getVersionString(),
                machineMemory, appMemory,
                pcType,
                releaseMonth, releaseDay,
                customerClass,
                udoTimestamp,
                dosVersion,
                sessionFlags,
                videoType,
                processorType,
                mediaType,
                windowsVersion,
                memoryMode,
                horizontalRes, verticalRes, numColors,
                region,
                language[0], language[1], language[2], language[3],
                getConnectSpeedDescription(), connectSpeed
        );
    }
}
