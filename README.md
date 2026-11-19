# Ewaira

A Fabric mod for Minecraft Java Edition that reduces CPU work and temporary
allocations during rendering and chunk processing.

## Optimizations

- **Terrain rendering** batches geometry, culls back-facing terrain, and adjusts
  detail based on how large terrain appears on screen.
- **Model rendering and chunk rebuilds** reuse vertex buffers and cache lighting
  and block-face visibility. Section visibility traversal reuses its work queue.
- **Text, textures, and biomes** use cached text widths, faster texture copying,
  and cached biome sampling.
- **Dropped items** use less geometry for distant stacks.

### Version-specific optimizations

| Minecraft | Additional optimizations |
| --- | --- |
| 1.21.1 | Frustum culling for billboard particles and block entities |
| 1.21.11 and all listed 26.x versions | Faster texture preparation during resource loading |
| 26.3 | Cached terrain draw preparation, plus reuse of encoded draw commands during upload |
| 26.3 | Simplified terrain from previously loaded chunks, up to 64 chunks away |
| 26.3 | Buffer reuse in climate sampling, world generation, and chunk palette packing |

## Supported versions

Fabric Loader 0.19.5 or newer is required. Each listed version has its own JAR.

| Minecraft | Java |
| --- | --- |
| 1.21.1, 1.21.10, 1.21.11 | 21+ |
| 26.1, 26.1.2, 26.2, 26.3 | 25+ |

## Build

Use JDK 25 and set `minecraft_version` to one of the versions above.

```sh
./gradlew build -Pminecraft_version=26.3
```

The JAR is written to `build/ewaira/<minecraft_version>/libs/`.

[MIT License](LICENSE)
