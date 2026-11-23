package remila.sex.cirrus.mixin;

import remila.sex.cirrus.terrain.ChunkDrawPreparation;
import remila.sex.cirrus.terrain.TerrainBatching;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.DynamicGpuData.ChunkSectionInfo;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LevelRenderer.ChunkDrawGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
abstract class TerrainPreparationMixin {
    @Shadow @Final private ObjectArrayList<RenderSection> visibleSections;
    @Shadow @Final private LevelRenderState levelRenderState;
    @Shadow @Final private OptionsRenderState optionsRenderState;
    @Shadow private SectionRenderDispatcher sectionRenderDispatcher;
    @Unique private final ChunkDrawPreparation cirrus$preparation = new ChunkDrawPreparation();

    @Inject(method = "extractSectionDrawGroups", at = @At("HEAD"), cancellable = true)
    private void cirrus$prepareChunks(boolean ordered, List<ChunkSectionInfo> infos,
            Map<ChunkSectionLayer, List<ChunkDrawGroup>> groups, CallbackInfoReturnable<Integer> cir) {
        if (!TerrainBatching.isEnabled()) return;
        int largest = cirrus$preparation.extract(ordered, infos, groups, visibleSections,
                sectionRenderDispatcher, levelRenderState, optionsRenderState);
        if (largest >= 0) cir.setReturnValue(largest);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void cirrus$closePreparation(CallbackInfo ci) { cirrus$preparation.clear(); }
    @Inject(method = "clearVisibleSections", at = @At("HEAD"))
    private void cirrus$clearPreparation(CallbackInfo ci) { cirrus$preparation.clear(); }
}
