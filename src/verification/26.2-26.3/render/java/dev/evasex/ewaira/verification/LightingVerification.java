package dev.evasex.ewaira.verification;

import dev.evasex.ewaira.mesh.ChunkLighting;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.SharedConstants;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/** Uses real transformed BlockModelLighter.Cache and vanilla light/shade functions, without a window. */
public final class LightingVerification {
    private LightingVerification() {}

    public static String verify() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Class<?> type = Class.forName("net.minecraft.client.renderer.block.BlockModelLighter$Cache");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object cache = constructor.newInstance();
        Method enable = method(type, "enable"), disable = method(type, "disable");
        Method light = method(type, "getLightCoords", BlockState.class, BlockAndTintGetter.class, BlockPos.class);
        Method shade = method(type, "getShadeBrightness", BlockState.class, BlockAndTintGetter.class, BlockPos.class);
        World reference = new World(7);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        long[] lookups = new long[2];
        int checked = 0;
        try {
            for (int mode = 0; mode < 2; mode++) {
                ChunkLighting.setEnabled(mode == 1);
                World region = new World(7);
                enable.invoke(cache);
                for (int z = 0; z < 16; z++) for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                    for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                        pos.set(x + dx - 100000, y + dy, z + dz + 100000);
                        check(cache, light, shade, region, reference, pos);
                        checked++;
                    }
                }
                lookups[mode] = region.lookups;
                disable.invoke(cache);
            }
            if (lookups[1] >= lookups[0]) throw new AssertionError("Section working set did not reduce raw light queries");
            // Emission/occlusion belong to each supplied state, not to the cached raw brightness.
            ChunkLighting.setEnabled(true);
            enable.invoke(cache);
            pos.set(-21, 70, 46);
            for (BlockState state : new BlockState[]{Blocks.STONE.defaultBlockState(),
                    Blocks.SEA_LANTERN.defaultBlockState(), Blocks.GLASS.defaultBlockState()}) {
                int expected = LightCoordsUtil.getLightCoords(LightCoordsUtil.BrightnessGetter.DEFAULT, reference, state, pos);
                if ((int) light.invoke(cache, state, reference, pos) != expected) {
                    throw new AssertionError("A cached raw light query changed block-state emission/occlusion");
                }
            }
            disable.invoke(cache);
            // Same coordinates in a different view must invalidate even without a disable call.
            ChunkLighting.setEnabled(true);
            enable.invoke(cache);
            World first = new World(3), second = new World(11);
            pos.set(-17, 64, 31);
            check(cache, light, shade, first, first, pos);
            check(cache, light, shade, second, second, pos);
            disable.invoke(cache);
            // A light update between rebuilds at the same view identity must not survive enable/disable.
            first.seed = 14;
            enable.invoke(cache);
            check(cache, light, shade, first, first, pos);
            disable.invoke(cache);
            // Outside vanilla's cache-enabled scope, mutable lighting must be read on every query.
            first.seed = 1;
            check(cache, light, shade, first, first, pos);
            first.seed = 9;
            check(cache, light, shade, first, first, pos);
            return "PASS: " + checked + " section-neighborhood light/shade pairs match vanilla functions exactly.\n"
                    + "raw_light_queries_vanilla=" + lookups[0] + ", optimized=" + lookups[1] + "\n"
                    + "PASS: changed view, rebuild/light update, disabled scope, negative/distant coordinates.\n"
                    + "Synthetic query counts, not chunk throughput or gameplay FPS.\n";
        } finally {
            disable.invoke(cache);
            ChunkLighting.setEnabled(true);
        }
    }

    private static void check(Object cache, Method light, Method shade, World region, World reference,
            BlockPos pos) throws ReflectiveOperationException {
        BlockState state = region.getBlockState(pos);
        int expectedLight = LightCoordsUtil.getLightCoords(LightCoordsUtil.BrightnessGetter.DEFAULT, reference, state, pos);
        float expectedShade = state.getShadeBrightness(reference, pos);
        int actualLight = (int) light.invoke(cache, state, region, pos);
        float actualShade = (float) shade.invoke(cache, state, region, pos);
        if (actualLight != expectedLight || Float.floatToRawIntBits(actualShade) != Float.floatToRawIntBits(expectedShade)) {
            throw new AssertionError("Lighting changed at " + pos + ": " + actualLight + " != " + expectedLight);
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... arguments) throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(name, arguments);
        method.setAccessible(true);
        return method;
    }

    private static final class World implements BlockAndTintGetter {
        private int seed;
        private long lookups;
        private World(int seed) { this.seed = seed; }
        public int getBrightness(LightLayer layer, BlockPos pos) {
            lookups++;
            return Math.floorMod(pos.getX() * 13 + pos.getY() * 7 + pos.getZ() * 3 + seed
                    + (layer == LightLayer.SKY ? 2 : 5), 16);
        }
        public BlockState getBlockState(BlockPos pos) {
            return switch (Math.floorMod(pos.getX() + pos.getY() + pos.getZ(), 3)) {
                case 0 -> Blocks.STONE.defaultBlockState();
                case 1 -> Blocks.GLASS.defaultBlockState();
                default -> Blocks.SEA_LANTERN.defaultBlockState();
            };
        }
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        public int getHeight() { return 384; }
        public int getMinY() { return -64; }
        public net.minecraft.world.level.CardinalLighting cardinalLighting() { return BlockAndTintGetter.EMPTY.cardinalLighting(); }
        public int getBlockTint(BlockPos pos, ColorResolver resolver) { return 0xFFFFFF; }
        public LevelLightEngine getLightEngine() { throw new AssertionError("Unexpected light engine access"); }
    }
}
