package dev.evasex.ewaira.mixin;

import com.mojang.blaze3d.vertex.MeshData;
import dev.evasex.ewaira.terrain.TerrainFaces;
import dev.evasex.ewaira.terrain.TerrainMeshAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(MeshData.class)
abstract class TerrainMeshDataMixin implements TerrainMeshAccess {
    @Unique private TerrainFaces ewaira$faces;
    @Override public TerrainFaces ewaira$terrainFaces() { return ewaira$faces; }
    @Override public void ewaira$terrainFaces(TerrainFaces faces) { ewaira$faces = faces; }
}
