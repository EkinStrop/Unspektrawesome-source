#version 450

layout(location = 0) in vec2 textureCoordinate;
layout(location = 0) out vec4 fragmentColor;

layout(set = 0, binding = 0) uniform sampler2D processedImage;

layout(push_constant) uniform PresentationParameters {
    float sourceAspect;
    float outputAspect;
    float srgbAttachment;
} presentation;

vec3 srgbToLinear(vec3 value)
{
    vec3 low = value / 12.92;
    vec3 high = pow((value + 0.055) / 1.055, vec3(2.4));
    return mix(low, high, step(vec3(0.04045), value));
}

void main()
{
    vec2 uv = textureCoordinate;
    if (presentation.outputAspect > presentation.sourceAspect) {
        float visibleHeight = presentation.sourceAspect / presentation.outputAspect;
        uv.y = (uv.y - 0.5) * visibleHeight + 0.5;
    } else {
        float visibleWidth = presentation.outputAspect / presentation.sourceAspect;
        uv.x = (uv.x - 0.5) * visibleWidth + 0.5;
    }
    vec4 sampled = texture(processedImage, clamp(uv, vec2(0.0), vec2(1.0)));
    if (presentation.srgbAttachment > 0.5)
        sampled.rgb = srgbToLinear(sampled.rgb);
    fragmentColor = sampled;
}
