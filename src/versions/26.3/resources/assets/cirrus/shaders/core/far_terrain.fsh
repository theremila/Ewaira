#version 330
#extension GL_ARB_separate_shader_objects : require
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:fog.glsl>

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec3 relativePosition;
layout(location = 0) out vec4 fragColor;

void main() {
    float inner = ColorModulator.x;
    float outer = ColorModulator.y;
    float squareDistance = max(abs(relativePosition.x), abs(relativePosition.z));
    float distance = length(relativePosition.xz);
    if (squareDistance <= inner || distance >= outer) discard;

    float edgeFog = 1.0 - smoothstep(inner, inner + 48.0, squareDistance);
    float horizonFog = smoothstep(outer * 0.55, outer, distance);
    float coverageFog = (1.0 - vertexColor.a) * 0.94;
    float haze = max(coverageFog, max(edgeFog, max(horizonFog, 0.33)));
    fragColor = vec4(mix(vertexColor.rgb, FogColor.rgb, haze), 1.0);
}
