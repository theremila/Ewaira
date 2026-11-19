package dev.evasex.ewaira.mixin;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SectionRenderDispatcher.CompiledSection.class)
public interface CompiledSectionAccessor {
    @Accessor("visibilitySet") VisibilitySet ewaira$getVisibilitySet();
}
