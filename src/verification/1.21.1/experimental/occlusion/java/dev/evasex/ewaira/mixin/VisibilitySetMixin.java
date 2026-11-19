package dev.evasex.ewaira.mixin;

import dev.evasex.ewaira.occlusion.SectionOccluders;
import dev.evasex.ewaira.occlusion.VisibilityOccluders;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(VisibilitySet.class)
abstract class VisibilitySetMixin implements VisibilityOccluders {
    @Unique private SectionOccluders ewaira$occluders;
    public SectionOccluders ewaira$getOccluders() { return ewaira$occluders; }
    public void ewaira$setOccluders(SectionOccluders value) { ewaira$occluders = value; }
}
