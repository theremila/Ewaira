package remila.sex.cirrus.mixin;

import remila.sex.cirrus.culling.SectionOccupancyAccess;
import remila.sex.cirrus.mesh.SectionVisibility;
import java.util.BitSet;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(VisibilitySet.class)
abstract class VisibilityOccupancyMixin implements SectionOccupancyAccess {
    @Unique
    private static final int[] CIRRUS$MASK_ORDINALS = {
        Direction.WEST.ordinal(),
        Direction.EAST.ordinal(),
        Direction.DOWN.ordinal(),
        Direction.UP.ordinal(),
        Direction.NORTH.ordinal(),
        Direction.SOUTH.ordinal()
    };

    @Shadow @Final private static int FACINGS;
    @Shadow @Final private BitSet data;
    @Unique private long[] cirrus$occupancy;

    @Override public long[] cirrus$occupancy() { return cirrus$occupancy; }
    @Override public void cirrus$occupancy(long[] mask) { cirrus$occupancy = mask; }

    @Override
    public void cirrus$addFaceMask(int faceMask) {
        if (faceMask == 0) return;
        int m1 = faceMask;
        while (m1 != 0) {
            int b1 = Integer.numberOfTrailingZeros(m1);
            m1 &= m1 - 1;
            int o1 = CIRRUS$MASK_ORDINALS[b1];
            int row = o1 * FACINGS;
            int m2 = faceMask;
            while (m2 != 0) {
                int b2 = Integer.numberOfTrailingZeros(m2);
                m2 &= m2 - 1;
                int o2 = CIRRUS$MASK_ORDINALS[b2];
                data.set(o1 + o2 * FACINGS);
                data.set(o2 + row);
            }
        }
    }
}
