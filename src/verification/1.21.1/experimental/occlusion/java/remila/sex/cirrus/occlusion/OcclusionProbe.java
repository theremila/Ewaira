package remila.sex.cirrus.occlusion;

import remila.sex.cirrus.mixin.CompiledSectionAccessor;
import remila.sex.cirrus.mixin.ViewAreaInvoker;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Render-thread scratch; never reads or loads world chunks. */
public final class OcclusionProbe {
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final BoxOcclusion.Occupancy occupancy = this::opaque;
    private ViewArea area;
    public boolean hidden(ViewArea area, Vec3 camera, AABB target) {
        this.area = area;
        try { return BoxOcclusion.hidden(camera, target, occupancy); }
        finally { this.area = null; }
    }
    private boolean opaque(int x, int y, int z) {
        pos.set(x, y, z);
        SectionRenderDispatcher.RenderSection section = ((ViewAreaInvoker) area).cirrus$sectionAt(pos);
        if (section == null || section.isDirty() || !((ReadyOccluders) section).cirrus$occlusionReady()) return false;
        VisibilitySet visibility = ((CompiledSectionAccessor) section.getCompiled()).cirrus$getVisibilitySet();
        if (visibility == null) return false;
        SectionOccluders snapshot = ((VisibilityOccluders) visibility).cirrus$getOccluders();
        return snapshot != null && snapshot.contains(x, y, z);
    }
}
