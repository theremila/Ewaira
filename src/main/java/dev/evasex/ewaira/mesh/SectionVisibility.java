package dev.evasex.ewaira.mesh;

import dev.evasex.ewaira.ModIdentity;
import java.util.BitSet;

/** Section-local flood fill; at most 4,096 cells are queued once per component. */
public final class SectionVisibility {
    public static final int WEST = 1, EAST = 2, DOWN = 4, UP = 8, NORTH = 16, SOUTH = 32;
    private static final ThreadLocal<int[]> QUEUE = ThreadLocal.withInitial(() -> new int[4096]);
    private static volatile boolean enabled = !ModIdentity.disabledBySystemProperty();

    private SectionVisibility() {}
    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }

    public static int floodFill(BitSet visited, int start) {
        int[] queue = QUEUE.get();
        int head = 0, tail = 1, faces = 0;
        queue[0] = start;
        visited.set(start);
        while (head < tail) {
            int cell = queue[head++];
            int x = cell & 15, y = cell >>> 8 & 15, z = cell >>> 4 & 15;
            if (x == 0) faces |= WEST; else tail = enqueue(visited, queue, tail, cell - 1);
            if (x == 15) faces |= EAST; else tail = enqueue(visited, queue, tail, cell + 1);
            if (y == 0) faces |= DOWN; else tail = enqueue(visited, queue, tail, cell - 256);
            if (y == 15) faces |= UP; else tail = enqueue(visited, queue, tail, cell + 256);
            if (z == 0) faces |= NORTH; else tail = enqueue(visited, queue, tail, cell - 16);
            if (z == 15) faces |= SOUTH; else tail = enqueue(visited, queue, tail, cell + 16);
        }
        return faces;
    }

    private static int enqueue(BitSet visited, int[] queue, int tail, int cell) {
        if (!visited.get(cell)) {
            visited.set(cell);
            queue[tail++] = cell;
        }
        return tail;
    }
}
