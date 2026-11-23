package remila.sex.cirrus.mixin;

import remila.sex.cirrus.culling.EntityOcclusionEngine;
import remila.sex.cirrus.culling.SectionOccupancyAccess;
import remila.sex.cirrus.culling.SectionOccupancyMap;
import remila.sex.cirrus.mesh.SectionVisibility;
import java.util.BitSet;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VisGraph.class)
abstract class SectionVisibilityMixin {
    @Shadow @Final private static int[] INDEX_OF_EDGES;
    @Shadow @Final private BitSet bitSet;
    @Shadow private int empty;

    @Unique private long[] cirrus$capturedOccupancy;

    @Inject(method = "resolve", at = @At("HEAD"), cancellable = true)
    private void cirrus$resolveZeroAlloc(CallbackInfoReturnable<VisibilitySet> cir) {
        long[] occupancy = null;
        if (EntityOcclusionEngine.isEnabled() && empty < 4096) {
            occupancy = empty <= 0 ? SectionOccupancyMap.ALL_SOLID : bitSet.toLongArray();
        }
        if (!SectionVisibility.isEnabled()) {
            cirrus$capturedOccupancy = occupancy;
            return;
        }
        VisibilitySet result = new VisibilitySet();
        if (4096 - empty < 256) {
            result.setAll(true);
        } else if (empty == 0) {
            result.setAll(false);
        } else {
            SectionOccupancyAccess access = (SectionOccupancyAccess) result;
            for (int edge : INDEX_OF_EDGES) {
                if (!bitSet.get(edge)) {
                    access.cirrus$addFaceMask(SectionVisibility.floodFill(bitSet, edge));
                }
            }
        }
        if (occupancy != null) {
            ((SectionOccupancyAccess) result).cirrus$occupancy(occupancy);
        }
        cir.setReturnValue(result);
    }

    @Inject(method = "resolve", at = @At("RETURN"))
    private void cirrus$attachFallbackOccupancy(CallbackInfoReturnable<VisibilitySet> cir) {
        long[] mask = cirrus$capturedOccupancy;
        cirrus$capturedOccupancy = null;
        if (mask != null && cir.getReturnValue() instanceof SectionOccupancyAccess access) {
            access.cirrus$occupancy(mask);
        }
    }
}
