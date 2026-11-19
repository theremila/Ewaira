package dev.evasex.ewaira.startup;

import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.system.MemoryUtil;
import dev.evasex.ewaira.mixin.NativeImagePixelsAccessor;

/** Bulk rows for distinct RGBA images; native pixel order remains authoritative for aliasing/mirroring X. */
public final class TextureCopies {
    private TextureCopies() {}
    public static boolean copy(NativeImage source, NativeImage target, int sx, int sy, int tx, int ty,
            int width, int height, boolean flipX, boolean flipY) {
        if (source == target || flipX || target == null || width <= 0 || height <= 0
                || source.format() != NativeImage.Format.RGBA || target.format() != NativeImage.Format.RGBA
                || sx < 0 || sy < 0 || tx < 0 || ty < 0
                || (long) sx + width > source.getWidth() || (long) sy + height > source.getHeight()
                || (long) tx + width > target.getWidth() || (long) ty + height > target.getHeight()) return false;
        long src = ((NativeImagePixelsAccessor) (Object) source).ewaira$getPixelsPointer();
        long dst = ((NativeImagePixelsAccessor) (Object) target).ewaira$getPixelsPointer();
        if (src == 0 || dst == 0) return false;
        long srcEnd = src + (long) source.getWidth() * source.getHeight() * 4;
        long dstEnd = dst + (long) target.getWidth() * target.getHeight() * 4;
        if (src < dstEnd && dst < srcEnd) return false;
        long rowBytes = (long) width * 4;
        for (int row = 0; row < height; row++) {
            long from = src + ((long) (sy + row) * source.getWidth() + sx) * 4;
            long to = dst + ((long) (ty + (flipY ? height - 1 - row : row)) * target.getWidth() + tx) * 4;
            MemoryUtil.memCopy(from, to, rowBytes);
        }
        return true;
    }
}
