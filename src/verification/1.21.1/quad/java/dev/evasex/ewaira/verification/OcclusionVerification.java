package dev.evasex.ewaira.verification;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.evasex.ewaira.mixin.CompiledSectionAccessor;
import dev.evasex.ewaira.occlusion.ReadyOccluders;
import dev.evasex.ewaira.occlusion.SectionOccluders;
import dev.evasex.ewaira.occlusion.VisibilityOccluders;
import dev.evasex.ewaira.occlusion.EntityOcclusion;
import java.lang.reflect.Constructor;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class OcclusionVerification {
    public static boolean active;
    private OcclusionVerification() {}
    public static BlockState state(BlockPos pos) {
        return switch (pos.getX() & 3) {
            case 0 -> Blocks.STONE.defaultBlockState();
            case 1 -> Blocks.GLASS.defaultBlockState();
            case 2 -> Blocks.OAK_SLAB.defaultBlockState();
            default -> Blocks.AIR.defaultBlockState();
        };
    }
    public static String verify() throws ReflectiveOperationException {
        if (EntityOcclusion.isEnabled()) throw new AssertionError("Occlusion must default to OFF");
        Object chunks = Array.newInstance(Class.forName("net.minecraft.client.renderer.chunk.RenderChunk"), 9);
        Constructor<RenderChunkRegion> constructor = RenderChunkRegion.class.getDeclaredConstructor(
                Level.class, int.class, int.class, chunks.getClass());
        constructor.setAccessible(true);
        RenderChunkRegion region = constructor.newInstance(null, 0, 0, chunks);
        BlockRenderDispatcher dispatcher = new BlockRenderDispatcher(null, null, null) {
            @Override public void renderBatched(BlockState state, BlockPos pos, BlockAndTintGetter world,
                    PoseStack poses, VertexConsumer vertices, boolean cull, RandomSource random) {
                // Only compilation control flow/occupancy is under test; models/textures are not initialized.
            }
        };
        SectionCompiler compiler = new SectionCompiler(dispatcher, null);
        SectionPos section = SectionPos.of(-1, -4, 3);
        SectionOccluders frozen = null;
        int checked = 0;
        try (SectionBufferBuilderPack buffers = new SectionBufferBuilderPack()) {
            active = true;
            for (boolean enabled : new boolean[]{false, true}) {
                EntityOcclusion.setEnabled(enabled);
                SectionCompiler.Results result = compiler.compile(section, region, VertexSorting.DISTANCE_TO_ORIGIN, buffers);
                try {
                    SectionOccluders snapshot = ((VisibilityOccluders) result.visibilitySet).ewaira$getOccluders();
                    if (!enabled && snapshot != null) throw new AssertionError("Disabled compilation captured occupancy");
                    if (enabled) {
                        if (snapshot == null) throw new AssertionError("Compilation did not publish occupancy");
                        for (BlockPos pos : BlockPos.betweenClosed(section.origin(), section.origin().offset(15, 15, 15))) {
                            if (snapshot.contains(pos.getX(), pos.getY(), pos.getZ()) != ((pos.getX() & 3) == 0)) {
                                throw new AssertionError("Wrong opaque mask at " + pos);
                            }
                            checked++;
                        }
                        SectionRenderDispatcher.CompiledSection compiled = new SectionRenderDispatcher.CompiledSection();
                        Field visibility = compiled.getClass().getDeclaredField("visibilitySet");
                        visibility.setAccessible(true);
                        visibility.set(compiled, result.visibilitySet);
                        if (((VisibilityOccluders) ((CompiledSectionAccessor) compiled).ewaira$getVisibilitySet())
                                .ewaira$getOccluders() != snapshot) throw new AssertionError("Mesh lost its matching snapshot");
                        frozen = snapshot;
                    }
                } finally { result.release(); buffers.clearAll(); }
            }
        } finally {
            active = false;
            EntityOcclusion.setEnabled(false);
        }
        if (frozen == null || !frozen.contains(-16, -64, 48) || frozen.contains(0, -64, 48)) {
            throw new AssertionError("Snapshot did not survive compile-buffer release or accepted a recycled origin");
        }
        if (!ReadyOccluders.class.isAssignableFrom(SectionRenderDispatcher.RenderSection.class)) {
            throw new AssertionError("Render-section readiness mixin missing");
        }
        if (Arrays.stream(LevelRenderer.class.getDeclaredMethods()).noneMatch(m -> m.getName().contains("skipHiddenMob"))) {
            throw new AssertionError("World entity-render hook missing");
        }
        ViewArea.class.getDeclaredMethods();
        return "PASS: actual transformed SectionCompiler; " + checked + " snapshot cells, stone/glass/slab/air.\n"
                + "PASS: disabled capture, negative section origin, immutable result ownership, compiled-section lookup,\n"
                + "readiness interface and world-render hook transformation. Model drawing stubbed; no GPU or window.\n"
                + "World rendering, upload races, resource packs and FPS are not verified. Occlusion defaults OFF.\n";
    }
}
