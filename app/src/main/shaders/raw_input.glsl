#ifndef UNSPEKTRA_RAW_INPUT_GLSL
#define UNSPEKTRA_RAW_INPUT_GLSL

const int RAW_FORMAT_SENSOR = 0;
const int RAW_FORMAT_10 = 1;
const int RAW_FORMAT_12 = 2;

const int CFA_RGGB = 0;
const int CFA_GRBG = 1;
const int CFA_GBRG = 2;
const int CFA_BGGR = 3;

const int CFA_RED = 0;
const int CFA_GREEN = 1;
const int CFA_BLUE = 2;

#ifdef UNSPEKTRA_RAW_IMAGE_INPUT
layout(set = 0, binding = 5) uniform usampler2D rawInputImage;
#else
layout(set = 0, binding = 0, std430) readonly buffer RawInputBuffer {
    uint words[];
} rawInput;
#endif

layout(set = 0, binding = 1, std140) uniform RawParameters {
    ivec4 dimensions; // RAW width, RAW height, output width, output height.
    ivec4 layoutInfo; // Row stride in bytes, format, CFA, frame seed.
    vec4 blackLevel;  // R, G1, G2, B.
    vec4 whiteLevel;  // R, G1, G2, B.
} rawParams;

uint rawByte(uint byteOffset)
{
#ifdef UNSPEKTRA_RAW_IMAGE_INPUT
    return 0u;
#else
    uint packed = rawInput.words[byteOffset >> 2u];
    return (packed >> ((byteOffset & 3u) * 8u)) & 0xffu;
#endif
}

ivec2 clampRawCoordinate(ivec2 position)
{
    return clamp(position, ivec2(0), rawParams.dimensions.xy - ivec2(1));
}

ivec2 clampRawCoordinateToParity(ivec2 position)
{
    ivec2 parity = position & ivec2(1);
    ivec2 maximum = rawParams.dimensions.xy - ivec2(1);
    maximum -= (maximum & ivec2(1)) ^ parity;
    maximum = max(maximum, parity);
    return clamp(position, parity, maximum);
}

uint rawInteger(ivec2 requestedPosition)
{
    ivec2 position = clampRawCoordinateToParity(requestedPosition);
#ifdef UNSPEKTRA_RAW_IMAGE_INPUT
    return texelFetch(rawInputImage, position, 0).r;
#else
    uint rowOffset = uint(position.y * rawParams.layoutInfo.x);
    uint x = uint(position.x);

    if (rawParams.layoutInfo.y == RAW_FORMAT_10) {
        uint groupOffset = rowOffset + (x >> 2u) * 5u;
        uint pixel = x & 3u;
        return (rawByte(groupOffset + pixel) << 2u) |
               ((rawByte(groupOffset + 4u) >> (pixel * 2u)) & 3u);
    }

    if (rawParams.layoutInfo.y == RAW_FORMAT_12) {
        uint groupOffset = rowOffset + (x >> 1u) * 3u;
        uint pixel = x & 1u;
        return (rawByte(groupOffset + pixel) << 4u) |
               ((rawByte(groupOffset + 2u) >> (pixel * 4u)) & 15u);
    }

    uint byteOffset = rowOffset + x * 2u;
    return rawByte(byteOffset) | (rawByte(byteOffset + 1u) << 8u);
#endif
}

uint rawIntegerClamped(ivec2 requestedPosition)
{
    ivec2 position = clampRawCoordinate(requestedPosition);
#ifdef UNSPEKTRA_RAW_IMAGE_INPUT
    return texelFetch(rawInputImage, position, 0).r;
#else
    uint rowOffset = uint(position.y * rawParams.layoutInfo.x);
    uint x = uint(position.x);

    if (rawParams.layoutInfo.y == RAW_FORMAT_10) {
        uint groupOffset = rowOffset + (x >> 2u) * 5u;
        uint pixel = x & 3u;
        return (rawByte(groupOffset + pixel) << 2u) |
               ((rawByte(groupOffset + 4u) >> (pixel * 2u)) & 3u);
    }

    if (rawParams.layoutInfo.y == RAW_FORMAT_12) {
        uint groupOffset = rowOffset + (x >> 1u) * 3u;
        uint pixel = x & 1u;
        return (rawByte(groupOffset + pixel) << 4u) |
               ((rawByte(groupOffset + 2u) >> (pixel * 4u)) & 15u);
    }

    uint byteOffset = rowOffset + x * 2u;
    return rawByte(byteOffset) | (rawByte(byteOffset + 1u) << 8u);
#endif
}

int cfaColor(ivec2 position)
{
    ivec2 parity = position & ivec2(1);
    int site = parity.y * 2 + parity.x;

    if (rawParams.layoutInfo.z == CFA_RGGB) {
        const int colors[4] = int[4](CFA_RED, CFA_GREEN, CFA_GREEN, CFA_BLUE);
        return colors[site];
    }
    if (rawParams.layoutInfo.z == CFA_GRBG) {
        const int colors[4] = int[4](CFA_GREEN, CFA_RED, CFA_BLUE, CFA_GREEN);
        return colors[site];
    }
    if (rawParams.layoutInfo.z == CFA_GBRG) {
        const int colors[4] = int[4](CFA_GREEN, CFA_BLUE, CFA_RED, CFA_GREEN);
        return colors[site];
    }

    const int colors[4] = int[4](CFA_BLUE, CFA_GREEN, CFA_GREEN, CFA_RED);
    return colors[site];
}

int cfaLevelIndex(ivec2 position)
{
    ivec2 parity = position & ivec2(1);
    int site = parity.y * 2 + parity.x;

    if (rawParams.layoutInfo.z == CFA_RGGB) {
        const int indices[4] = int[4](0, 1, 2, 3);
        return indices[site];
    }
    if (rawParams.layoutInfo.z == CFA_GRBG) {
        const int indices[4] = int[4](1, 0, 3, 2);
        return indices[site];
    }
    if (rawParams.layoutInfo.z == CFA_GBRG) {
        const int indices[4] = int[4](1, 3, 0, 2);
        return indices[site];
    }

    const int indices[4] = int[4](3, 1, 2, 0);
    return indices[site];
}

float rawNormalized(ivec2 position)
{
    int levelIndex = cfaLevelIndex(position);
    float black = rawParams.blackLevel[levelIndex];
    float white = rawParams.whiteLevel[levelIndex];
    float value = float(rawInteger(position));
    return clamp((value - black) / max(white - black, 1.0), 0.0, 1.0);
}

float rawNormalizedClamped(ivec2 position)
{
    position = clampRawCoordinate(position);
    int levelIndex = cfaLevelIndex(position);
    float black = rawParams.blackLevel[levelIndex];
    float white = rawParams.whiteLevel[levelIndex];
    float value = float(rawIntegerClamped(position));
    return clamp((value - black) / max(white - black, 1.0), 0.0, 1.0);
}

#endif
