#ifndef UNSPEKTRA_RAW_COLOR_GLSL
#define UNSPEKTRA_RAW_COLOR_GLSL

layout(set = 0, binding = 3, std140) uniform RawColorParameters {
    vec4 whiteBalanceExposure;
    vec4 colorMatrix0;
    vec4 colorMatrix1;
    vec4 colorMatrix2;
    vec4 reserved0;
    vec4 lensControls;
    vec4 reserved1;
    vec4 reserved2;
    vec4 geometryControls;
} rawColor;

layout(set = 0, binding = 4, std430) readonly buffer LensShadingMap {
    vec4 gain[];
} lensShading;

vec4 rawLensGainAt(ivec2 position, ivec2 dimensions)
{
    ivec2 clamped = clamp(position, ivec2(0), dimensions - 1);
    return lensShading.gain[clamped.y * dimensions.x + clamped.x];
}

vec3 rawLensGain(vec2 normalizedPosition)
{
    ivec2 dimensions = ivec2(round(rawColor.geometryControls.zw));
    if (any(lessThan(dimensions, ivec2(1))))
        return vec3(1.0);
    vec2 mapPosition = clamp(normalizedPosition, vec2(0.0), vec2(1.0)) *
                       vec2(max(dimensions - 1, ivec2(0)));
    ivec2 lower = ivec2(floor(mapPosition));
    ivec2 upper = min(lower + 1, dimensions - 1);
    vec2 fraction = fract(mapPosition);
    vec4 top = mix(rawLensGainAt(lower, dimensions),
                   rawLensGainAt(ivec2(upper.x, lower.y), dimensions),
                   fraction.x);
    vec4 bottom = mix(rawLensGainAt(ivec2(lower.x, upper.y), dimensions),
                      rawLensGainAt(upper, dimensions), fraction.x);
    vec4 gain = mix(top, bottom, fraction.y);
    return vec3(gain.r, 0.5 * (gain.g + gain.b), gain.a);
}

vec3 rawCameraWhiteNormalized()
{
    vec3 gains = max(rawColor.whiteBalanceExposure.rgb, vec3(0.001));
    vec3 cameraWhite = 1.0 / gains;
    return cameraWhite / max(max(cameraWhite.r, cameraWhite.g), cameraWhite.b);
}

vec3 rawNeutralizeClippedHighlights(vec3 cameraRgb)
{
    float clipLevel = clamp(rawColor.geometryControls.y, 0.5, 1.0);
    float blendStart = clipLevel * 0.70710678118;
    vec3 channelBlend = smoothstep(
        vec3(blendStart),
        vec3(clipLevel),
        cameraRgb);
    float blend = max(max(channelBlend.r, channelBlend.g), channelBlend.b);
    return mix(cameraRgb, rawCameraWhiteNormalized(), blend);
}

vec3 rawColorProcess(vec3 rawRgb, vec2 normalizedPosition, float frameSeed)
{
    rawRgb *= rawLensGain(normalizedPosition);
    rawRgb = rawNeutralizeClippedHighlights(rawRgb);
    rawRgb *= rawColor.whiteBalanceExposure.rgb;
    mat3 cameraToWorking = mat3(
        rawColor.colorMatrix0.xyz,
        rawColor.colorMatrix1.xyz,
        rawColor.colorMatrix2.xyz);
    return max(cameraToWorking * rawRgb, vec3(0.0));
}

#endif
