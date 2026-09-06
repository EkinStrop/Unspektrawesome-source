#ifndef UNSPEKTRA_FRAME_GEOMETRY_GLSL
#define UNSPEKTRA_FRAME_GEOMETRY_GLSL

layout(set = 0, binding = 6, std140) uniform GeometryParameters {
    vec4 sourceCrop;
    vec4 activeArray;
    ivec4 transform;
    vec4 target;
} frameGeometry;

vec2 geometrySourceUv(ivec2 outputPosition)
{
    vec2 orientedUv = (vec2(outputPosition) + 0.5) /
                      vec2(frameGeometry.transform.zw);
    if (frameGeometry.transform.y != 0)
        orientedUv.x = 1.0 - orientedUv.x;

    vec2 sourceUv;
    if (frameGeometry.transform.x == 1)
        sourceUv = vec2(orientedUv.y, 1.0 - orientedUv.x);
    else if (frameGeometry.transform.x == 2)
        sourceUv = vec2(1.0) - orientedUv;
    else if (frameGeometry.transform.x == 3)
        sourceUv = vec2(1.0 - orientedUv.y, orientedUv.x);
    else
        sourceUv = orientedUv;
    return frameGeometry.sourceCrop.xy + sourceUv * frameGeometry.sourceCrop.zw;
}

ivec2 geometrySourcePosition(ivec2 outputPosition, ivec2 sourceDimensions)
{
    vec2 sourcePixel = geometrySourceUv(outputPosition) * vec2(sourceDimensions);
    return clamp(ivec2(floor(sourcePixel)), ivec2(0), sourceDimensions - 1);
}

vec2 geometryLensShadingUv(vec2 sourceUv)
{
    return clamp(
        (sourceUv - frameGeometry.activeArray.xy) /
            frameGeometry.activeArray.zw,
        vec2(0.0), vec2(1.0));
}

#endif
