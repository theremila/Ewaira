package dev.evasex.ewaira.mixin;

import dev.evasex.ewaira.mesh.SectionVisibility;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VisGraph.class)
abstract class SectionVisibilityMixin {
    @Shadow @Final private BitSet bitSet;

    @Inject(method = "floodFill", at = @At("HEAD"), cancellable = true)
    private void ewaira$connectedFaces(int start, CallbackInfoReturnable<Set<Direction>> cir) {
        if (!SectionVisibility.isEnabled()) return;
        int mask = SectionVisibility.floodFill(bitSet, start);
        EnumSet<Direction> faces = EnumSet.noneOf(Direction.class);
        if ((mask & SectionVisibility.WEST) != 0) faces.add(Direction.WEST);
        if ((mask & SectionVisibility.EAST) != 0) faces.add(Direction.EAST);
        if ((mask & SectionVisibility.DOWN) != 0) faces.add(Direction.DOWN);
        if ((mask & SectionVisibility.UP) != 0) faces.add(Direction.UP);
        if ((mask & SectionVisibility.NORTH) != 0) faces.add(Direction.NORTH);
        if ((mask & SectionVisibility.SOUTH) != 0) faces.add(Direction.SOUTH);
        cir.setReturnValue(faces);
    }
}
