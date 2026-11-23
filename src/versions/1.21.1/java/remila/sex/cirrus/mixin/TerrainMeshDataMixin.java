package remila.sex.cirrus.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import remila.sex.cirrus.terrain.TerrainFaces;
import remila.sex.cirrus.terrain.TerrainMeshAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MeshData.class)
abstract class TerrainMeshDataMixin implements TerrainMeshAccess {
    @Unique private TerrainFaces cirrus$faces;
    @Override public TerrainFaces cirrus$terrainFaces() { return cirrus$faces; }
    @Override public void cirrus$terrainFaces(TerrainFaces faces) { cirrus$faces = faces; }
}
