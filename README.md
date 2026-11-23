<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/banner-dark.png">
    <source media="(prefers-color-scheme: light)" srcset="assets/banner-light.png">
    <img alt="Cirrus" src="assets/banner-dark.png" height="72">
  </picture>

  <p>A lightweight Fabric optimization mod for Minecraft Java Edition.</p>

  <p>
    <img alt="Fabric" src="https://img.shields.io/badge/Fabric-1.21.1--26.3-1f2328?style=flat&labelColor=0d1117">
    <a href="LICENSE"><img alt="MIT License" src="https://img.shields.io/badge/License-MIT-1f2328?style=flat&labelColor=0d1117"></a>
  </p>
</div>

## What Cirrus is

Cirrus reduces CPU work, GPU upload stalls, and temporary heap allocations during rendering, chunk compilation, and biome sampling — ready to play out of the box, with optional Mod Menu settings.

## Optimizations

- **Off-heap memory & zero-alloc pipelines** move terrain sorting, LOD index building,
  section visibility flood-fill queues, and indirect draw command staging into manual
  native memory (`NativeArena` / `MemoryUtil`), cutting JVM GC pressure.
- **Terrain rendering** batches geometry, emits exact multi-bucket directional face ranges
  without hidden interior buckets, and adjusts detail based on screen size.
- **Entity & block-entity occlusion culling** skips mobs and block entities completely
  hidden behind solid walls using compiled section occupancy masks and conservative 3D DDA raycasting.
- **Model rendering and chunk rebuilds** reuse vertex buffers, cache lighting and
  block-face visibility, and use zero-allocation section visibility resolution.
- **Text, textures, and biomes** use cached text widths, faster texture copying,
  and cached biome sampling.
- **Dropped items** use less geometry for distant stacks.

### Version-specific optimizations

| Minecraft | Additional optimizations |
| --- | --- |
| 1.21.1 | Frustum culling for billboard particles; frustum + occlusion culling for bounded block entities; multi-range OpenGL terrain drawing |
| 1.21.11 and all listed 26.x versions | Faster texture preparation during resource loading |
| 26.3 | Off-heap indirect draw staging, exact multi-bucket indirect commands, bulk native `ChunkSectionInfo` upload without `Std140Builder`, and cached terrain draw preparation |
| 26.3 | Hardware backface-culled 16×16-chunk distant terrain up to 64 chunks away with asynchronous compressed `.clod` disk caching |
| 26.3 | Buffer reuse in climate sampling, world generation, and chunk palette packing |

## Supported versions

Fabric Loader 0.19.5 or newer is required. Each listed version has its own JAR.

| Minecraft | Java |
| --- | --- |
| 1.21.1, 1.21.10, 1.21.11 | 21+ |
| 26.1, 26.1.2, 26.2, 26.3 | 25+ |

## Build

Use JDK 25+ (tested on OpenJDK 27 with Gradle 9.9.2) and set `minecraft_version` to one of the versions above.

```sh
./gradlew setupTarget -Pminecraft_version=26.3
./gradlew build -Pminecraft_version=26.3
./gradlew verifyAllTargets -PwithAssets=false
```

The JAR is written to `build/cirrus/<minecraft_version>/libs/`.

[MIT License](LICENSE)
