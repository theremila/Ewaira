package remila.sex.cirrus.memory;

import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.system.MemoryUtil;

/**
 * Manual off-heap native memory block / bump arena backed by LWJGL {@link MemoryUtil}.
 * Bypasses JVM garbage collection completely while tracking live native allocations.
 */
public final class NativeArena implements AutoCloseable {
    private static final Cleaner CLEANER = Cleaner.create();
    private static final AtomicLong LIVE_BYTES = new AtomicLong();
    private static final AtomicLong LIVE_ALLOCATIONS = new AtomicLong();

    private final State state;
    private final Cleaner.Cleanable cleanable;
    private final long maxRetainedBytes;
    private long cursor;

    public NativeArena(long initialCapacity, long maxRetainedBytes) {
        if (initialCapacity < 0 || maxRetainedBytes < 0) {
            throw new IllegalArgumentException("Negative arena capacity");
        }
        this.maxRetainedBytes = maxRetainedBytes;
        this.state = new State();
        this.cleanable = CLEANER.register(this, state);
        if (initialCapacity > 0) {
            ensure(initialCapacity);
        }
    }

    public NativeArena(long initialCapacity) {
        this(initialCapacity, Long.MAX_VALUE);
    }

    public static long liveBytes() {
        return LIVE_BYTES.get();
    }

    public static long liveAllocations() {
        return LIVE_ALLOCATIONS.get();
    }

    public long address() {
        return state.address;
    }

    public long capacity() {
        return state.capacity;
    }

    public long used() {
        return cursor;
    }

    public boolean isClosed() {
        return state.closed;
    }

    /** Ensures at least {@code requiredBytes} contiguous native bytes are allocated and returns the base address. */
    public long ensure(long requiredBytes) {
        if (state.closed) throw new IllegalStateException("NativeArena is closed");
        if (requiredBytes <= 0) return state.address;
        if (requiredBytes <= state.capacity) return state.address;

        long target = state.capacity == 0 ? 64L : state.capacity;
        while (target < requiredBytes) {
            long doubled = target << 1;
            if (doubled <= 0) {
                target = requiredBytes;
                break;
            }
            target = doubled;
        }
        long previousAddress = state.address;
        long previousCapacity = state.capacity;
        long next = previousAddress == 0L
                ? MemoryUtil.nmemAlloc(target)
                : MemoryUtil.nmemRealloc(previousAddress, target);
        if (next == 0L) {
            throw new OutOfMemoryError("Failed to allocate " + target + " off-heap bytes");
        }
        state.address = next;
        state.capacity = target;
        if (previousAddress == 0L) {
            LIVE_ALLOCATIONS.incrementAndGet();
        }
        LIVE_BYTES.addAndGet(target - previousCapacity);
        return next;
    }

    /** Bump-allocates {@code bytes} aligned to a power-of-two {@code alignment} and returns the native pointer. */
    public long allocate(long bytes, int alignment) {
        if (bytes < 0 || alignment <= 0 || (alignment & (alignment - 1)) != 0) {
            throw new IllegalArgumentException("Invalid allocation size/alignment");
        }
        long mask = alignment - 1L;
        long aligned = (cursor + mask) & ~mask;
        long next = aligned + bytes;
        if (next < 0) throw new OutOfMemoryError("NativeArena offset overflow");
        long base = ensure(next);
        cursor = next;
        return base + aligned;
    }

    public void reset() {
        cursor = 0L;
        if (state.capacity > maxRetainedBytes && maxRetainedBytes > 0) {
            shrinkTo(maxRetainedBytes);
        }
    }

    public void trim(long retainedBytes) {
        if (state.closed || state.address == 0L || state.capacity <= retainedBytes) return;
        if (retainedBytes <= 0) {
            state.free();
            cursor = 0L;
            return;
        }
        shrinkTo(retainedBytes);
    }

    private void shrinkTo(long target) {
        long previousCapacity = state.capacity;
        long shrunk = MemoryUtil.nmemRealloc(state.address, target);
        if (shrunk != 0L) {
            state.address = shrunk;
            state.capacity = target;
            if (cursor > target) cursor = target;
            LIVE_BYTES.addAndGet(target - previousCapacity);
        }
    }

    @Override
    public void close() {
        cursor = 0L;
        cleanable.clean();
    }

    private static final class State implements Runnable {
        long address;
        long capacity;
        boolean closed;

        void free() {
            if (address != 0L) {
                MemoryUtil.nmemFree(address);
                LIVE_BYTES.addAndGet(-capacity);
                LIVE_ALLOCATIONS.decrementAndGet();
                address = 0L;
                capacity = 0L;
            }
        }

        @Override
        public void run() {
            closed = true;
            free();
        }
    }
}
