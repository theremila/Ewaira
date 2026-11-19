package dev.evasex.ewaira.terrain;

import dev.evasex.ewaira.debug.RenderDiagnostics;
import dev.evasex.ewaira.debug.RenderDiagnostics.Metric;
import java.nio.ByteBuffer;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Objects;
import java.util.RandomAccess;
import net.minecraft.client.renderer.DynamicGpuData.IndexedDraw;

/** Render-thread list that reuses encoded commands across unchanged and sparsely edited batches. */
public final class TerrainDrawCommands extends AbstractList<IndexedDraw> implements RandomAccess {
    public static final int COMMAND_BYTES = 5 * Integer.BYTES;
    private static final int MIN_CACHED_COMMANDS = 32;
    private static final int MAX_CACHED_COMMANDS = 8_192;
    private IndexedDraw[] commands = new IndexedDraw[0];
    private int size;
    private int previousSize;
    private boolean changed;
    private ByteBuffer encoded;
    private boolean validEncoding;
    private final BitSet dirtyCommands = new BitSet(0);

    void beginFrame() {
        previousSize = size;
        size = 0;
        changed = false;
        modCount++;
    }

    void append(IndexedDraw draw) {
        if (size == commands.length) commands = Arrays.copyOf(commands, Math.max(8, size + size / 2));
        if (size >= previousSize || commands[size] != draw) {
            commands[size] = draw;
            changed = true;
            if (validEncoding && size < MAX_CACHED_COMMANDS) dirtyCommands.set(size);
        }
        size++;
    }

    void finishFrame() {
        if (size < previousSize) {
            Arrays.fill(commands, size, previousSize, null);
            if (dirtyCommands.length() > size) dirtyCommands.clear(size, dirtyCommands.length());
        }
    }

    @Override public IndexedDraw get(int index) { return commands[Objects.checkIndex(index, size)]; }
    @Override public int size() { return size; }

    public boolean copyCachedBytes(ByteBuffer target, int offset) {
        int length = size * COMMAND_BYTES;
        if (size < MIN_CACHED_COMMANDS || size > MAX_CACHED_COMMANDS) {
            validEncoding = false;
            dirtyCommands.clear();
            return false;
        }
        boolean fits = encoded != null && encoded.capacity() >= length;
        boolean patchable = validEncoding && fits && encoded.order() == target.order();
        int dirty = patchable ? dirtyCommands.cardinality() : 0;
        if ((changed && !patchable) || dirty > size / 2) {
            validEncoding = false;
            dirtyCommands.clear();
            return false;
        }
        if (!fits) {
            int capacity = Integer.highestOneBit(size - 1) << 1;
            encoded = ByteBuffer.allocate(capacity * COMMAND_BYTES);
        }
        if (!patchable) {
            encoded.clear().order(target.order());
            for (int i = 0; i < size; i++) commands[i].write(encoded);
        } else if (dirty != 0) {
            for (int slot = dirtyCommands.nextSetBit(0); slot >= 0; slot = dirtyCommands.nextSetBit(slot + 1)) {
                encoded.position(slot * COMMAND_BYTES);
                commands[slot].write(encoded);
            }
            if (RenderDiagnostics.enabled()) {
                RenderDiagnostics.add(Metric.PATCHED_COMMANDS, dirty);
                RenderDiagnostics.add(Metric.REUSED_COMMANDS_IN_PATCHED_BATCHES, size - dirty);
            }
        }
        dirtyCommands.clear();
        validEncoding = true;
        target.put(offset, encoded.array(), 0, length);
        target.position(offset + length);
        return true;
    }
}
