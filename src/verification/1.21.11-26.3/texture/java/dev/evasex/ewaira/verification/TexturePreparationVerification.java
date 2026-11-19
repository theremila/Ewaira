package dev.evasex.ewaira.verification;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import dev.evasex.ewaira.startup.TexturePreparation;
import java.util.Arrays;
import java.util.Random;
import java.lang.management.ManagementFactory;
import com.sun.management.ThreadMXBean;

public final class TexturePreparationVerification {
    private TexturePreparationVerification() {}
    private static String benchmark() {
        try (NativeImage image = new NativeImage(64, 64, false)) {
            Random random = new Random(51);
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) image.setPixel(x, y, random.nextInt(4) == 0 ? random.nextInt() | 0xff000000 : random.nextInt() & 0xffffff);
            ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            long[] nativeNs = new long[11], optimizedNs = new long[11];
            long nativeBytes = 0, optimizedBytes = 0;
            for (int i = -10; i < 11; i++) for (int step = 0; step < 2; step++) {
                boolean enabled = ((i + step) & 1) == 0;
                TexturePreparation.setEnabled(enabled);
                long bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
                long start = System.nanoTime();
                for (int j = 0; j < 64; j++) TextureUtil.solidify(image);
                long elapsed = System.nanoTime() - start;
                bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - bytes;
                if (i >= 0) {
                    (enabled ? optimizedNs : nativeNs)[i] = elapsed / 64;
                    if (enabled) optimizedBytes += bytes; else nativeBytes += bytes;
                }
            }
            Arrays.sort(nativeNs); Arrays.sort(optimizedNs);
            return "CPU-only 64x64 cutout texture, 10 alternating warmup pairs, 11 sample pairs of 64 calls; not whole-client startup time.\n"
                    + "solidify_median_ns: native=" + nativeNs[5] + ", optimized=" + optimizedNs[5] + "\n"
                    + "solidify_allocated_bytes_per_call: native=" + nativeBytes / (11 * 64) + ", optimized=" + optimizedBytes / (11 * 64) + "\n";
        }
    }

    public static String verify() {
        Random random = new Random(392113);
        try {
            for (int fixture = 0; fixture < 360; fixture++) {
                int width = fixture % 59 + 1, height = fixture % 47 + 1;
                if (fixture == 359) { width = 257; height = 256; }
                try (NativeImage expected = new NativeImage(width, height, false);
                     NativeImage actual = new NativeImage(width, height, false)) {
                    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                        int color = random.nextInt();
                        int alpha = switch (fixture % 6) {
                            case 0 -> 255; case 1 -> 0;
                            case 2 -> x == width / 2 && y == height / 2 ? 1 : 0;
                            default -> random.nextInt(4) == 0 ? random.nextInt(1, 256) : 0;
                        };
                        color = (color & 0x00ffffff) | alpha << 24;
                        expected.setPixel(x, y, color); actual.setPixel(x, y, color);
                    }
                    TexturePreparation.setEnabled(false); TextureUtil.solidify(expected);
                    TexturePreparation.setEnabled(true); TextureUtil.solidify(actual);
                    if (!Arrays.equals(expected.getPixels(), actual.getPixels())) throw new AssertionError("Texture fill mismatch fixture=" + fixture);
                }
            }
            return "PASS: 360 native texture fills match every ARGB pixel: opaque/transparent, arbitrary invisible RGB, partial alpha, single seed, ties, rectangular/1px images and large-image fallback.\n" + benchmark();
        } finally { TexturePreparation.setEnabled(true); }
    }
}
