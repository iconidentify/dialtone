# Art Assets Directory

This directory contains art assets for v3 Download on Demand (DOD) requests.

## File Structure

Each art asset consists of two files:
- `{mat_art_id}.[png|jpg|jpeg|gif]` - The source image (PNG, JPEG, or GIF format)
- `{mat_art_id}.json` - Metadata describing default rendering properties

### Supported Image Formats

The system automatically detects and loads:
- **PNG** - Recommended for icons, logos, images with transparency
- **JPEG/JPG** - Good for photos, no transparency support
- **GIF** - Supported as input format (converted to optimized GIF87a output)

All formats are processed through the same pipeline and converted to protocol-compatible GIF87a.

### mat_art_id Format

The mat_art_id follows the pattern: `{group}-{subgroup}-{id}`

Example: `1-0-34196`

## JSON Metadata Format

```json
{
  "transparency": true,
  "width": 42,
  "height": 42,
  "flagByte1": 128,
  "flagByte2": 253
}
```

### Fields

- **transparency** (boolean): Whether to enable GIF transparency
- **width** (integer): Target width in pixels for the rendered image
- **height** (integer): Target height in pixels for the rendered image
- **flagByte1** (integer, optional): Resource category flag (byte 36 in header)
  - Default: 128 (0x80) - most common category
  - Alternative: 0 (0x00) - alternate category
- **flagByte2** (integer, optional): Resource type code (byte 37 in header)
  - Default: 253 (0xFD) - most common type
  - Alternatives: 0 (0x00), 227 (0xE3), 30 (0x1E), 15 (0x0F), 42 (0x2A)
  - See flag byte patterns table above for typical usage

### Image Quality Enhancement Fields (NEW!)

- **enableDithering** (boolean, optional): Enable Floyd-Steinberg dithering for better detail preservation
  - Default: `true` (recommended for most images)
  - Spreads quantization errors to neighboring pixels, massively improving perceived detail
  - Before/After comparison: Smooth gradients vs. banding artifacts

- **enablePosterization** (boolean, optional): Enable posterization for crisp, bold retro-style graphics
  - Default: `false` (use adaptive palette without posterization)
  - Pre-reduces color depth before quantization, creating distinct color regions
  - Best for: Icons, logos, cartoon-style graphics

- **posterizationLevel** (integer, optional): Number of color levels per channel (2-256)
  - Default: 32 (5-bit color, standard AOL-style)
  - Recommended values:
    - **16**: Very crisp, cartoon-like (4-bit color)
    - **32**: Standard retro-style (5-bit color) - bold and distinctive
    - **48**: Moderate posterization - balanced
    - **64**: Subtle posterization (6-bit color) - smoother gradients
  - Only used when `enablePosterization` is `true`

## Image Processing Pipeline

The system uses advanced image processing to generate high-quality art:

1. **Load Source**: Load image in PNG, JPEG, or GIF format (any size, any color depth)
2. **Resize**: Scale to target dimensions (e.g., 42×42) using high-quality interpolation
3. **Optional Posterization** (if enabled): Pre-reduce colors for crisp retro-style look
4. **Adaptive Palette Generation**: MedianCut algorithm analyzes image and generates optimal 256-color palette
5. **Optional Dithering** (if enabled): Floyd-Steinberg error diffusion preserves detail
6. **GIF87a Encoding**: Convert to protocol-compatible GIF format with proper headers
7. **Delivery**: Encode as hex pairs and send to client

**Format Auto-Detection**: The system searches for images in this order: `.png` → `.jpg` → `.jpeg` → `.gif`

### Quality Improvements Over Static Palette

**Before (static web-safe palette)**:
- Fixed 216-color web-safe cube + 40 grayscale
- No adaptation to image content
- No dithering - visible color banding
- Poor reproduction of photos and complex images

**After (adaptive palette + dithering)**:
- MedianCut generates palette from actual image colors
- Floyd-Steinberg dithering spreads quantization errors
- Smooth gradients even with 256 colors
- Much better photo reproduction

## How It Works

1. Client sends DOD request with `dod_gid` containing mat_art_id
2. DodRequestHandler detects the mat_art_id pattern and loads the art
3. ArtService processes the image through the pipeline:
   - Loads the PNG image
   - Loads the JSON metadata
   - Resizes image to specified dimensions
   - Applies posterization (if enabled)
   - Generates adaptive 256-color palette using MedianCut
   - Applies Floyd-Steinberg dithering (if enabled, default: true)
   - Converts to GIF87a format with v3 specifications
   - Encodes as hex pairs and chunks into multiple `dod_data` lines
4. Response is sent back to client with art data

## Default Fallback

If a client requests a mat_art_id that doesn't exist, the system automatically falls back to `default.png` and `default.json`.

This ensures the server always returns a valid response, even for unknown art IDs.

**Required Files:**
- `default.png` - Fallback image (must exist)
- `default.json` - Fallback metadata (must exist)

If the default files are missing, the server will throw an error.

**Example:**
- Client requests: `1-0-99999` (doesn't exist)
- Server logs: "Art asset not found: 1-0-99999 - falling back to default"
- Server returns: `default.png` converted to GIF87a with `default.json` settings

## Protocol Art Format Specifications

The encoder generates art data in the v3 format, discovered through reverse engineering of 4,156 GIF samples. The complete payload structure is:

```
[40-byte Protocol Header] + [GIF87a Data] + [Padding]
```

### 1. Protocol Art Header (40 bytes) - Complete Specification

The client requires a specific 40-byte header before the GIF data. All multi-byte values are **little-endian**.

#### Byte-by-Byte Breakdown

| Offset | Size | Field Name | Value | Description |
|--------|------|------------|-------|-------------|
| 0-1 | 2 | Version Marker Part 1 | `01 00` | Always this value |
| 2-3 | 2 | Version Marker Part 2 | `01 00` | Always this value |
| 4-5 | 2 | Flag Field | `01 00` | Always this value |
| 6-7 | 2 | **Size A** | Variable | GIF_size + Padding_size + 36 |
| 8-11 | 4 | Variable Flags | `00 00 01 00` | Fixed pattern |
| 12-13 | 2 | Unknown Field | `00 00` | Reserved |
| 14-15 | 2 | **Magic Constant A** | `05 02` | ALWAYS 0x0205 |
| 16-17 | 2 | Image Width | Variable | Width in pixels (little-endian) |
| 18-19 | 2 | Image Height | Variable | Height in pixels (little-endian) |
| 20-21 | 2 | Null Padding | `00 00` | Fixed zeros |
| 22-23 | 2 | **Size B** | Variable | GIF_size + Padding_size |
| 24-25 | 2 | Null Padding | `00 00` | Fixed zeros |
| 26 | 1 | **Magic Marker B Start** | `24` | ALWAYS 0x24 |
| 27-35 | 9 | Magic Marker B Rest | `00...` | Nine null bytes |
| 36 | 1 | Flag Byte 1 | Variable | Resource category (see table) |
| 37 | 1 | Flag Byte 2 | Variable | Resource type code (see table) |
| 38-39 | 2 | Reserved | `00 00` | Fixed zeros |

#### Mathematical Relationships (100% Validated)

The header size fields follow these precise relationships:

```
Total_Payload_Length = Size_A + 4
Size_A = Size_B + 36
Size_B = GIF_size + Padding_size
```

**Example Validation** (from reverse engineering):
```
GIF size:      921 bytes
Padding size:  1574 bytes
Size B:        2495 bytes (921 + 1574) ✓
Size A:        2531 bytes (2495 + 36) ✓
Total length:  2535 bytes (2531 + 4) ✓
```

#### Flag Byte Patterns

Six distinct flag patterns were discovered across 4,156 samples:

| Pattern | Byte 36 | Byte 37 | Frequency | Typical Use |
|---------|---------|---------|-----------|-------------|
| Pattern 1 | `0x80` | `0xFD` | 55% | Default/most common |
| Pattern 2 | `0x80` | `0x00` | 13% | Alternative type |
| Pattern 3 | `0x00` | `0xE3` | 13% | Small icons |
| Pattern 4 | `0x80` | `0x1E` | 11% | Medium icons |
| Pattern 5 | `0x00` | `0x0F` | 4% | Large graphics |
| Pattern 6 | `0x80` | `0x2A` | 4% | Tiny icons |

**Interpretation**:
- **Byte 36 (0x80 vs 0x00)**: Likely indicates resource category
- **Byte 37**: Resource type code specific to the category

These can be configured per-asset in the JSON metadata file.

### 2. GIF87a Image Data

Following the header, standard GIF87a format with these specifications:

- **Header**: GIF87a
- **Global Color Table**: 256 colors (8 bits per pixel)
- **Graphic Control Extension**: `21 F9 04 01 00 00 00 00` (transparency enabled when configured)
- **Single frame**: Static image (delay time: 0)
- **LZW compression**: Minimum code size 08
- **Terminator**: 0x3B

### 3. Padding (Optional)

After the GIF terminator (0x3B), padding bytes **may** be appended, but are not required:

- **Default**: 0 bytes (no padding) - proven to work with v3 clients
- **Range observed in archives**: 94-1574 bytes in some historical files
- **Content**: Null bytes (0x00) when present
- **Purpose**: Unknown (possibly memory alignment or buffer allocation in original tools)
- **Requirement**: OPTIONAL - Size B calculation works correctly with or without padding

**Important Discovery**:
- Working v3 transmissions confirm padding is **NOT required**
- Size B field (bytes 22-23) = `GIF_size + Padding_size` (where Padding_size can be 0)
- The mathematical relationships work perfectly with no padding:
  ```
  Size B = GIF_size + 0 = GIF_size
  Size A = Size B + 36 = GIF_size + 36
  Total = Size A + 4 = GIF_size + 40
  ```

**Example** (working transmission, 42×42 image):
```
GIF size:      1883 bytes
Padding size:  0 bytes (none!)
Size B:        1883 bytes (1883 + 0) ✓
Size A:        1919 bytes (1883 + 36) ✓
Total length:  1923 bytes (1919 + 4) ✓
Ends with:     00 00 3B (no data after GIF terminator)
```

## Adding New Art Assets

To add a new art asset:

1. Create an image file: `{mat_art_id}.[png|jpg|jpeg|gif]`
   - Any size (will be resized according to JSON metadata)
   - Any color depth (will be quantized to 256 colors)
   - Supported formats: PNG, JPEG, or GIF

2. Create a JSON file: `{mat_art_id}.json`
   - Specify desired width and height
   - Enable/disable transparency as needed
   - Configure quality options (dithering, posterization)

3. Place both files in this directory

4. Test with v3 client by requesting the mat_art_id

**Format Recommendations**:
- Use **PNG** for icons, logos, or images needing transparency
- Use **JPEG** for photos (smaller source files, no transparency)
- Use **GIF** if you already have GIF assets (will be re-optimized)

## Configuration Examples

### Example 1: High-Quality Photo (Default Settings)

For mat_art_id `1-0-34196`:

**1-0-34196.jpg**: Source photo (e.g., 1024×1024 JPEG)

**1-0-34196.json**:
```json
{
  "transparency": true,
  "width": 42,
  "height": 42
}
```

Result:
- Adaptive palette derived from photo colors
- Floyd-Steinberg dithering enabled (default: true)
- Smooth gradients, good photo reproduction
- File size: ~1800-2000 bytes

### Example 2: Crisp Icon (Posterized Style)

For mat_art_id `icon-logo`:

**icon-logo.png**: Logo/icon with solid colors

**icon-logo.json**:
```json
{
  "transparency": true,
  "width": 32,
  "height": 32,
  "enableDithering": false,
  "enablePosterization": true,
  "posterizationLevel": 16
}
```

Result:
- Bold, distinct color regions
- No dithering (crisp edges)
- Classic retro icon look
- Smaller file size due to less color variation

### Example 3: Balanced Photo (Subtle Posterization)

For mat_art_id `profile-pic`:

**profile-pic.png**: Profile photo

**profile-pic.json**:
```json
{
  "transparency": false,
  "width": 48,
  "height": 48,
  "enableDithering": true,
  "enablePosterization": true,
  "posterizationLevel": 64
}
```

Result:
- Subtle posterization reduces file size slightly
- Dithering preserves skin tone gradients
- Good balance of quality vs. size
- File size: ~1600-1900 bytes

## Format Compliance & Validation

This implementation is based on:
- Reverse engineering of **4,156 archived GIF samples**
- Validation against **working v3 client transmissions**

Achieving:
- 100% format compliance across all samples
- 100% magic constant validation (0x0205, 0x24 marker)
- 100% mathematical relationship accuracy
- Proven compatibility with v3 clients

### Key Discoveries

1. **Two Magic Constants**
   - Magic Constant A (0x0205): Format version identifier
   - Magic Marker B (0x24 + 9 nulls): Boundary marker

2. **Mathematical Precision**
   - All size relationships hold without exception
   - Validated across samples ranging from 100 bytes to 65KB
   - Works perfectly with or without padding

3. **Flag Byte Patterns**
   - Six distinct patterns discovered in archives
   - Correlate with image dimensions and resource types
   - Now configurable per-asset

4. **Padding Discovery** (Critical Finding!)
   - Present in 100% of archived samples (94-1574 bytes)
   - **BUT: Working transmission proves padding is OPTIONAL**
   - Default implementation: 0 bytes (no padding)
   - Mathematical relationships work correctly either way

### Testing

Comprehensive test coverage ensures continued compliance:

- **AolArtHeaderTest**: 29 tests validating header structure
  - Includes test with 0 padding (matching working transmission)
- **GifEncoderTest**: 12 tests validating complete payload
  - Validates default behavior (no padding)
  - Verifies GIF terminator is last byte
- All mathematical relationships validated with and without padding
- All 6 flag byte patterns tested

Run tests:
```bash
mvn test -Dtest=AolArtHeaderTest,GifEncoderTest
# Expected: 41 tests, 0 failures
```
