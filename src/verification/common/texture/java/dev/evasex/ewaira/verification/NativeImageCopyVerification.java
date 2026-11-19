package dev.evasex.ewaira.verification;

import com.mojang.blaze3d.platform.NativeImage;
import dev.evasex.ewaira.mixin.NativeImagePixelsAccessor;
import dev.evasex.ewaira.startup.TexturePreparation;
import java.util.Random;
import org.lwjgl.system.MemoryUtil;

public final class NativeImageCopyVerification {
    private NativeImageCopyVerification() {}

    public static String verify() {
        Random random = new Random(1137);
        for (int fixture = 0; fixture < 256; fixture++) {
            try (NativeImage source = new NativeImage(64, 48, false);
                 NativeImage expected = new NativeImage(80, 60, true);
                 NativeImage actual = new NativeImage(80, 60, true)) {
                for (int y = 0; y < 48; y++) for (int x = 0; x < 64; x++) source.fillRect(x, y, 1, 1, random.nextInt());
                int sx = random.nextInt(32), sy = random.nextInt(24), tx = random.nextInt(40), ty = random.nextInt(30);
                int width = random.nextInt(1, 33), height = random.nextInt(1, 25);
                boolean flipX = (fixture & 1) != 0, flipY = (fixture & 2) != 0;
                TexturePreparation.setEnabled(false); source.copyRect(expected, sx, sy, tx, ty, width, height, flipX, flipY);
                TexturePreparation.setEnabled(true); source.copyRect(actual, sx, sy, tx, ty, width, height, flipX, flipY);
                if (!pixelsMatch(expected, actual)) throw new AssertionError("Texture copy mismatch=" + fixture);
                TexturePreparation.setEnabled(false); expected.copyRect(expected, 0, 0, 2, 3, 20, 20, flipX, flipY);
                TexturePreparation.setEnabled(true); actual.copyRect(actual, 0, 0, 2, 3, 20, 20, flipX, flipY);
                if (!pixelsMatch(expected, actual)) throw new AssertionError("Aliasing fallback mismatch=" + fixture);
            }
        }
        return "PASS: 256 native image rectangle copies match all pixels, both mirror axes, distinct strides/offsets, and overlapping same-image fallback.\n";
    }

    private static boolean pixelsMatch(NativeImage expected, NativeImage actual) {
        long left = ((NativeImagePixelsAccessor) (Object) expected).ewaira$getPixelsPointer();
        long right = ((NativeImagePixelsAccessor) (Object) actual).ewaira$getPixelsPointer();
        for (long offset = 0; offset < (long) expected.getWidth() * expected.getHeight() * 4; offset += 4) {
            if (MemoryUtil.memGetInt(left + offset) != MemoryUtil.memGetInt(right + offset)) return false;
        }
        return true;
    }
}
