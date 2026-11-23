package remila.sex.cirrus.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import remila.sex.cirrus.ModIdentity;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShaderManager.class)
abstract class FarTerrainShaderMixin {
    /** Vanilla with Fabric Loader alone never enumerates mod assets. Honor overrides when present. */
    @WrapOperation(method = "loadConfigs", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/packs/resources/ResourceManager;listResources(Ljava/lang/String;Lnet/minecraft/server/packs/resources/ResourceManager$Selector;)Ljava/util/Map;"))
    private static Map<Identifier, Resource> cirrus$shaderSources(ResourceManager manager, String prefix,
            ResourceManager.Selector selector, Operation<Map<Identifier, Resource>> list) {
        var sources = list.call(manager, prefix, selector);
        var vertex = Identifier.fromNamespaceAndPath(ModIdentity.MOD_ID, "shaders/core/far_terrain.vsh");
        var fragment = Identifier.fromNamespaceAndPath(ModIdentity.MOD_ID, "shaders/core/far_terrain.fsh");
        if (sources.containsKey(vertex) && sources.containsKey(fragment)) return sources;
        var mod = FabricLoader.getInstance().getModContainer(ModIdentity.MOD_ID).orElseThrow();
        var location = new PackLocationInfo(ModIdentity.MOD_ID, Component.literal(ModIdentity.MOD_NAME),
                PackSource.BUILT_IN, Optional.empty());
        var pack = new PathPackResources(location, mod.getRootPaths().getFirst());
        var merged = new HashMap<>(sources);
        for (Identifier id : new Identifier[]{vertex, fragment}) {
            if (merged.containsKey(id)) continue;
            var asset = mod.findPath("assets/" + id.getNamespace() + "/" + id.getPath());
            if (asset.isPresent()) {
                var path = asset.get();
                merged.put(id, new Resource(pack, () -> Files.newInputStream(path)));
            }
        }
        return merged;
    }
}
