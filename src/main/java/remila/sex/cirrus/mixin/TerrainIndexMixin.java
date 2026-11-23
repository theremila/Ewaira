package remila.sex.cirrus.mixin;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import remila.sex.cirrus.terrain.TerrainIndexAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MeshData.class)
abstract class TerrainIndexMixin implements TerrainIndexAccess {
    @Shadow private ByteBufferBuilder.Result indexBuffer;
    @Shadow @Final @Mutable private MeshData.DrawState drawState;
    @Unique private ByteBufferBuilder cirrus$indexArena;

    @Override public void cirrus$indices(ByteBufferBuilder arena, MeshData.DrawState state) {
        if (indexBuffer != null || cirrus$indexArena != null) throw new IllegalStateException("Mesh already owns custom indices");
        indexBuffer = arena.build();
        drawState = state;
        cirrus$indexArena = arena;
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void cirrus$closeIndices(CallbackInfo ci) {
        if (cirrus$indexArena != null) {
            cirrus$indexArena.close();
            cirrus$indexArena = null;
        }
    }
}
