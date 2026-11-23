package remila.sex.cirrus.mixin;

import remila.sex.cirrus.occlusion.SectionOccluders;
import remila.sex.cirrus.occlusion.VisibilityOccluders;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(VisibilitySet.class)
abstract class VisibilitySetMixin implements VisibilityOccluders {
    @Unique private SectionOccluders cirrus$occluders;
    public SectionOccluders cirrus$getOccluders() { return cirrus$occluders; }
    public void cirrus$setOccluders(SectionOccluders value) { cirrus$occluders = value; }
}
