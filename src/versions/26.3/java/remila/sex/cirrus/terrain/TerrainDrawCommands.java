package remila.sex.cirrus.terrain;

import remila.sex.cirrus.debug.RenderDiagnostics;
import remila.sex.cirrus.debug.RenderDiagnostics.Metric;
import remila.sex.cirrus.memory.NativeArena;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.AbstractList;
import java.util.Objects;
import java.util.RandomAccess;
import net.minecraft.client.renderer.DynamicGpuData.IndexedDraw;
import org.lwjgl.system.MemoryUtil;

/**
 * Off-heap indirect draw command list backed by a {@link NativeArena}.
 * Stores raw 20-byte {@code VkDrawIndexedIndirectCommand} structs directly in native memory
 * with zero {@link IndexedDraw} heap allocations on the hot render path, and uploads via native {@code memCopy}.
 */
public final class TerrainDrawCommands extends AbstractList<IndexedDraw> implements RandomAccess, AutoCloseable {
    public static final int COMMAND_BYTES = 5 * Integer.BYTES;
    private static final int MIN_CACHED_COMMANDS = 32;
    private static final int MAX_CACHED_COMMANDS = 16_384;
    private static final long MAX_RETAINED_BYTES = (long) MAX_CACHED_COMMANDS * COMMAND_BYTES;

    private final NativeArena nativeBuffer = new NativeArena(0, MAX_RETAINED_BYTES);
    private int size;
    private int previousSize;
    private int dirtyCount;
    private boolean warmed;

    void beginFrame() {
        previousSize = size;
        size = 0;
        dirtyCount = 0;
        modCount++;
    }

    /** Fast zero-allocation write directly into off-heap native memory without reading back previous bytes. */
    void appendDirect(int indexCount, int instanceCount, int firstIndex, int baseVertex, int baseInstance) {
        long offset = (long) size * COMMAND_BYTES;
        long base = nativeBuffer.ensure(offset + COMMAND_BYTES);
        writeCommand(base + offset, indexCount, instanceCount, firstIndex, baseVertex, baseInstance);
        dirtyCount++;
        size++;
    }

    /** Zero-allocation native command append tracking sparse diffs across frames. */
    void appendRaw(int indexCount, int instanceCount, int firstIndex, int baseVertex, int baseInstance) {
        long offset = (long) size * COMMAND_BYTES;
        long base = nativeBuffer.ensure(offset + COMMAND_BYTES);
        long ptr = base + offset;
        if (size < previousSize) {
            if (MemoryUtil.memGetInt(ptr) != indexCount
                    || MemoryUtil.memGetInt(ptr + 4) != instanceCount
                    || MemoryUtil.memGetInt(ptr + 8) != firstIndex
                    || MemoryUtil.memGetInt(ptr + 12) != baseVertex
                    || MemoryUtil.memGetInt(ptr + 16) != baseInstance) {
                writeCommand(ptr, indexCount, instanceCount, firstIndex, baseVertex, baseInstance);
                dirtyCount++;
            }
        } else {
            writeCommand(ptr, indexCount, instanceCount, firstIndex, baseVertex, baseInstance);
            dirtyCount++;
        }
        size++;
    }

    void append(IndexedDraw draw) {
        appendRaw(draw.indexCount(), draw.instanceCount(), draw.firstIndex(), draw.baseVertex(), draw.baseInstance());
    }

    void finishFrame() {
        if (size != previousSize) {
            dirtyCount += Math.abs(previousSize - size);
        }
    }

    private static void writeCommand(long ptr, int indexCount, int instanceCount, int firstIndex, int baseVertex, int baseInstance) {
        MemoryUtil.memPutInt(ptr, indexCount);
        MemoryUtil.memPutInt(ptr + 4, instanceCount);
        MemoryUtil.memPutInt(ptr + 8, firstIndex);
        MemoryUtil.memPutInt(ptr + 12, baseVertex);
        MemoryUtil.memPutInt(ptr + 16, baseInstance);
    }

    @Override
    public IndexedDraw get(int index) {
        Objects.checkIndex(index, size);
        long ptr = nativeBuffer.address() + (long) index * COMMAND_BYTES;
        return new IndexedDraw(
                MemoryUtil.memGetInt(ptr),
                MemoryUtil.memGetInt(ptr + 4),
                MemoryUtil.memGetInt(ptr + 8),
                MemoryUtil.memGetInt(ptr + 12),
                MemoryUtil.memGetInt(ptr + 16));
    }

    @Override
    public int size() {
        return size;
    }

    public boolean copyCachedBytes(ByteBuffer target, int offset) {
        int length = size * COMMAND_BYTES;
        if (size < MIN_CACHED_COMMANDS || size > MAX_CACHED_COMMANDS
                || target.order() != ByteOrder.nativeOrder()) {
            warmed = false;
            dirtyCount = 0;
            return false;
        }
        boolean direct = target.isDirect();
        // On direct GPU mapped buffers our off-heap NativeArena already holds the exact binary commands,
        // so native memCopy beats vanilla per-record Java encoding even when every command changed.
        if (!direct && ((dirtyCount != 0 && !warmed) || dirtyCount > size / 2)) {
            warmed = false;
            dirtyCount = 0;
            return false;
        }
        if (dirtyCount > 0 && RenderDiagnostics.enabled()) {
            RenderDiagnostics.add(Metric.PATCHED_COMMANDS, dirtyCount);
            RenderDiagnostics.add(Metric.REUSED_COMMANDS_IN_PATCHED_BATCHES, Math.max(0, size - dirtyCount));
        }
        // Reset dirtyCount after upload so stationary snapshot replays do not re-count stale edits.
        dirtyCount = 0;
        warmed = true;
        long srcAddr = nativeBuffer.address();
        if (direct) {
            MemoryUtil.memCopy(srcAddr, MemoryUtil.memAddress0(target) + offset, length);
        } else {
            ByteBuffer view = MemoryUtil.memByteBuffer(srcAddr, length);
            target.put(offset, view, 0, length);
        }
        target.position(offset + length);
        return true;
    }

    @Override
    public void close() {
        size = 0;
        previousSize = 0;
        dirtyCount = 0;
        warmed = false;
        nativeBuffer.close();
    }
}
