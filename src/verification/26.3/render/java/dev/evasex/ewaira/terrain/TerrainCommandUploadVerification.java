package dev.evasex.ewaira.terrain;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice.MappedView;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.GpuFence;
import com.mojang.renderpearl.api.device.GpuDevice;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.renderer.DynamicGpuData.IndexedDraw;
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped;

public final class TerrainCommandUploadVerification {
    private static final int COMMAND_BYTES = TerrainDrawCommands.COMMAND_BYTES;
    private static volatile GpuBufferSlice[] measuredSlices;

    private TerrainCommandUploadVerification() {}

    public static String verify() throws ReflectiveOperationException {
        Field deviceField = RenderSystem.class.getDeclaredField("DEVICE");
        deviceField.setAccessible(true);
        Object oldDevice = deviceField.get(null);
        MappedBuffers writes = new MappedBuffers();
        deviceField.set(null, writes.device());
        try (var storage = new DynamicGpuDataStorageMapped<IndexedDraw>(
                "Ewaira command fixture", COMMAND_BYTES, GpuBuffer.USAGE_INDIRECT_PARAMETERS, 4_096)) {
            List<IndexedDraw> first = draws(853);
            List<IndexedDraw> second = draws(133);
            List<IndexedDraw> prefix = draws(5);
            List<IndexedDraw> suffix = draws(39);
            TerrainDrawCommands cachedFirst = new TerrainDrawCommands();
            TerrainDrawCommands cachedSecond = new TerrainDrawCommands();
            for (int frame = 0; frame < 10; frame++) {
                if (frame == 3) {
                    first.set(121, new IndexedDraw(41, 1, -61, 17, 264));
                    first.set(743, new IndexedDraw(37, 1, -82, 17, 264));
                }
                if (frame == 5) second = second.subList(0, 42);
                if (frame == 6) second.add(new IndexedDraw(19, 1, 23, 37, 41));
                if (frame == 7) first = first.reversed();
                fill(cachedFirst, first);
                fill(cachedSecond, second);
                List<List<IndexedDraw>> plain = List.of(prefix, first, second, suffix);
                List<List<IndexedDraw>> cached = List.of(prefix, cachedFirst, cachedSecond, suffix);
                storage.endFrame();
                storage.writeDataBatchedMultiple(List.of(prefix));
                GpuBufferSlice[] expected = storage.writeDataBatchedMultiple(plain);
                byte[] expectedBytes = writes.bytes;
                storage.endFrame();
                storage.writeDataBatchedMultiple(List.of(prefix));
                GpuBufferSlice[] actual = storage.writeDataBatchedMultiple(cached);
                if (!sameRanges(expected, actual) || !Arrays.equals(expectedBytes, writes.bytes)) {
                    throw new AssertionError("Native upload mismatch in frame " + frame);
                }
            }
            verifySparseReuse();
            writes.capture = false;
            String timing = benchmark(storage, List.of(first, second), List.of(cachedFirst, cachedSecond));
            return "PASS: native indirect staging preserves bytes and offsets across sparse edits, reorder, shrink and extension, with native batches before and after cached groups.\n" + timing;
        } finally {
            deviceField.set(null, oldDevice);
        }
    }

    private static void verifySparseReuse() {
        List<IndexedDraw> source = draws(256);
        TerrainDrawCommands commands = new TerrainDrawCommands();
        ByteBuffer target = ByteBuffer.allocate(source.size() * COMMAND_BYTES + 12).order(ByteOrder.nativeOrder());
        fill(commands, source);
        fill(commands, source);
        if (!commands.copyCachedBytes(target, 12)) throw new AssertionError("Stable batch did not warm its encoding");
        source.set(12, new IndexedDraw(43, 1, 25, 16, 27));
        fill(commands, source);
        source.set(237, new IndexedDraw(47, 1, 29, 18, 31));
        fill(commands, source);
        if (!commands.copyCachedBytes(target, 12)) throw new AssertionError("Sparse edits did not reuse the encoding");
        ByteBuffer reference = ByteBuffer.allocate(target.capacity()).order(target.order()).position(12);
        for (IndexedDraw draw : source) draw.write(reference);
        if (!Arrays.equals(target.array(), reference.array()) || target.position() != reference.position()) {
            throw new AssertionError("Cached bytes after multiple edits differ from native bytes");
        }
        fill(commands, source.reversed());
        if (commands.copyCachedBytes(target, 12)) throw new AssertionError("Fully reordered batch should use native staging");
    }

    private static boolean sameRanges(GpuBufferSlice[] expected, GpuBufferSlice[] actual) {
        if (expected.length != actual.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if (expected[i].offset() != actual[i].offset() || expected[i].length() != actual[i].length()) return false;
        }
        return true;
    }

    private static String benchmark(DynamicGpuDataStorageMapped<IndexedDraw> storage,
            List<List<IndexedDraw>> plain, List<List<IndexedDraw>> cached) {
        int repeats = 128;
        long[] plainNs = new long[11];
        long[] cachedNs = new long[11];
        for (int run = -20; run < plainNs.length; run++) {
            for (int step = 0; step < 2; step++) {
                boolean reuse = ((run + step) & 1) == 0;
                List<List<IndexedDraw>> input = reuse ? cached : plain;
                long start = System.nanoTime();
                for (int i = 0; i < repeats; i++) {
                    storage.endFrame();
                    measuredSlices = storage.writeDataBatchedMultiple(input);
                }
                if (run >= 0) (reuse ? cachedNs : plainNs)[run] = (System.nanoTime() - start) / repeats;
            }
        }
        Arrays.sort(plainNs);
        Arrays.sort(cachedNs);
        return "command_staging_median_ns: native=" + plainNs[5] + ", reused=" + cachedNs[5] + "\n";
    }

    private static void fill(TerrainDrawCommands list, List<IndexedDraw> commands) {
        list.beginFrame();
        for (IndexedDraw command : commands) list.append(command);
        list.finishFrame();
    }

    private static List<IndexedDraw> draws(int count) {
        List<IndexedDraw> commands = new ArrayList<>(count);
        for (int i = 0; i < count; i++) commands.add(new IndexedDraw(i + 13, 1, i * 7, i - 400, i));
        return commands;
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static final class MappedBuffers {
        private boolean capture = true;
        private byte[] bytes;

        GpuDevice device() {
            GpuFence fence = proxy(GpuFence.class, (object, method, args) -> switch (method.getName()) {
                case "awaitCompletion" -> true;
                case "close" -> null;
                default -> throw new AssertionError(method);
            });
            CommandEncoder encoder = proxy(CommandEncoder.class, (object, method, args) -> {
                if (!method.getName().equals("createFence")) throw new AssertionError(method);
                return fence;
            });
            return proxy(GpuDevice.class, (object, method, args) -> switch (method.getName()) {
                case "createCommandEncoder" -> encoder;
                case "createBuffer" -> buffer(Math.toIntExact((long) args[2]));
                default -> throw new AssertionError(method);
            });
        }

        private GpuBuffer buffer(int size) {
            ByteBuffer storage = ByteBuffer.allocateDirect(size);
            return proxy(GpuBuffer.class, (object, method, args) -> switch (method.getName()) {
                case "size" -> (long) size;
                case "slice" -> new GpuBufferSlice((GpuBuffer) object, (long) args[0], (long) args[1]);
                case "map" -> {
                    long offset = (long) args[0], length = (long) args[1];
                    var slice = new GpuBufferSlice((GpuBuffer) object, offset, length);
                    ByteBuffer data = storage.slice(Math.toIntExact(offset), Math.toIntExact(length)).order(ByteOrder.nativeOrder());
                    yield new MappedView(slice, data, () -> capture(data));
                }
                case "close" -> null;
                default -> throw new AssertionError(method);
            });
        }

        private void capture(ByteBuffer data) {
            if (!capture) return;
            ByteBuffer source = data.duplicate().clear();
            bytes = new byte[source.remaining()];
            source.get(bytes);
        }
    }
}
