package dev.evasex.ewaira.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.serialization.DataResult;
import dev.evasex.ewaira.worldgen.PalettePacking;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PaletteResize;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO.PackedData;
import net.minecraft.world.level.chunk.Strategy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PalettedContainer.class)
abstract class PalettePackingMixin<T> {
    // Vanilla pack/unpack consume the temporary IDs before returning. Keep the lease
    // across that consumption, including when a palette or registry call reenters.
    @WrapMethod(method = "pack")
    private PackedData<T> ewaira$pack(Strategy<T> strategy, Operation<PackedData<T>> vanilla) {
        if (!PalettePacking.isEnabled()) return vanilla.call(strategy);
        PalettePacking.Scratch scratch = PalettePacking.scratch();
        scratch.enter();
        try {
            return vanilla.call(strategy);
        } finally {
            scratch.leave();
        }
    }

    @WrapMethod(method = "unpack")
    private static <T> DataResult<PalettedContainer<T>> ewaira$unpack(Strategy<T> strategy, PackedData<T> data,
            Operation<DataResult<PalettedContainer<T>>> vanilla) {
        if (!PalettePacking.isEnabled()) return vanilla.call(strategy, data);
        PalettePacking.Scratch scratch = PalettePacking.scratch();
        scratch.enter();
        try {
            return vanilla.call(strategy, data);
        } finally {
            scratch.leave();
        }
    }

    @Inject(method = "reencodeContents", at = @At("HEAD"), cancellable = true)
    private static <T> void ewaira$reencode(BitStorage storage, Palette<T> source, Palette<T> destination,
            CallbackInfoReturnable<int[]> callback) {
        if (!PalettePacking.isEnabled() ||
                (storage.getClass() != SimpleBitStorage.class && storage.getClass() != ZeroBitStorage.class)) return;
        int[] entries = PalettePacking.scratch().entries(storage.getSize());
        if (entries == null) return;
        storage.unpack(entries);
        PaletteResize<T> resize = PaletteResize.noResizeExpected();
        int previous = -1;
        int mapped = -1;
        for (int i = 0; i < entries.length; i++) {
            int original = entries[i];
            if (original != previous) {
                previous = original;
                mapped = destination.idFor(source.valueFor(original), resize);
            }
            entries[i] = mapped;
        }
        callback.setReturnValue(entries);
    }
}
