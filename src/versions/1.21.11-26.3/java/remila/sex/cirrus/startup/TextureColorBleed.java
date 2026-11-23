package remila.sex.cirrus.startup;

import com.mojang.blaze3d.platform.NativeImage;

/** Same breadth-first tie order as TextureUtil.solidify, without a distance array or opaque-interior queue. */
public final class TextureColorBleed {
    private static final int MAX_PIXELS = 65_536;
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private TextureColorBleed() {}

    public static boolean fill(NativeImage image) {
        int width = image.getWidth(), height = image.getHeight();
        long size = (long) width * height;
        if (width <= 0 || height <= 0 || size > MAX_PIXELS || image.format() != NativeImage.Format.RGBA) return false;
        int count = (int) size;
        Scratch scratch = SCRATCH.get();
        scratch.ensure(count);
        int[] colors = scratch.colors, queue = scratch.queue;
        int opaque = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int color = image.getPixel(x, y);
            colors[x + y * width] = color;
            if ((color >>> 24) != 0) opaque++;
        }
        if (opaque == count) return true;
        if (opaque == 0) {
            // Native nearestColor starts at zero even if invisible source pixels have RGB data.
            image.fillRect(0, 0, width, height, 0);
            return true;
        }
        int tail = 0;
        // Native seeds are x-major; preserving this order preserves the winner at equal distances.
        for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
            int i = x + y * width;
            if ((colors[i] >>> 24) != 0 && ((x > 0 && transparent(colors[i - 1]))
                    || (x + 1 < width && transparent(colors[i + 1]))
                    || (y > 0 && transparent(colors[i - width]))
                    || (y + 1 < height && transparent(colors[i + width])))) queue[tail++] = i;
        }
        for (int head = 0; head < tail; head++) {
            int i = queue[head], x = i % width, y = i / width, color = colors[i];
            // Exactly native +X, -X, +Y, -Y discovery order.
            if (x + 1 < width && transparent(colors[i + 1])) { colors[i + 1] = color; queue[tail++] = i + 1; }
            if (x > 0 && transparent(colors[i - 1])) { colors[i - 1] = color; queue[tail++] = i - 1; }
            if (y + 1 < height && transparent(colors[i + width])) { colors[i + width] = color; queue[tail++] = i + width; }
            if (y > 0 && transparent(colors[i - width])) { colors[i - width] = color; queue[tail++] = i - width; }
        }
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            if ((image.getPixel(x, y) >>> 24) == 0) image.setPixel(x, y, colors[x + y * width] & 0x00ffffff);
        }
        return true;
    }
    private static boolean transparent(int color) { return (color >>> 24) == 0; }
    private static final class Scratch {
        int[] colors = new int[0], queue = new int[0];
        void ensure(int size) {
            if (colors.length < size) {
                int capacity = Math.min(MAX_PIXELS, Math.max(256, Integer.highestOneBit(size - 1) << 1));
                colors = new int[capacity]; queue = new int[capacity];
            }
        }
    }
}
