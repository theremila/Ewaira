package remila.sex.cirrus.lod;

import remila.sex.cirrus.ModIdentity;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Asynchronous compressed binary cache (.clod) for 16x16-chunk distant terrain regions.
 * Eliminates horizon holes across sessions without blocking the render thread.
 */
final class FarTerrainDiskCache {
    private static final int MAGIC = 0x434C4F44; // "CLOD"
    private static final int VERSION = 1;
    private static final ExecutorService IO_WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, ModIdentity.MOD_NAME + "-lod-io");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private final Path directory;

    FarTerrainDiskCache(Path directory) {
        this.directory = directory;
    }

    void saveAsync(int regionChunkX, int regionChunkZ, List<FarTerrainTile> tiles) {
        if (directory == null || tiles.isEmpty()) return;
        List<FarTerrainTile> copy = List.copyOf(tiles);
        IO_WORKER.execute(() -> writeRegion(regionChunkX, regionChunkZ, copy));
    }

    void loadAsync(int regionChunkX, int regionChunkZ, java.util.function.Consumer<List<FarTerrainTile>> onLoaded) {
        if (directory == null) return;
        Path file = regionFile(regionChunkX, regionChunkZ);
        IO_WORKER.execute(() -> {
            if (!Files.isRegularFile(file)) return;
            List<FarTerrainTile> loaded = readRegion(file);
            if (!loaded.isEmpty()) onLoaded.accept(loaded);
        });
    }

    private Path regionFile(int rx, int rz) {
        return directory.resolve("r." + rx + "." + rz + ".clod");
    }

    private void writeRegion(int rx, int rz, List<FarTerrainTile> tiles) {
        Path temp = directory.resolve("r." + rx + "." + rz + ".clod.tmp");
        try {
            Files.createDirectories(directory);
            Path target = regionFile(rx, rz);
            try (var out = new DataOutputStream(new BufferedOutputStream(new DeflaterOutputStream(Files.newOutputStream(temp))))) {
                out.writeInt(MAGIC);
                out.writeByte(VERSION);
                out.writeShort(tiles.size());
                for (FarTerrainTile tile : tiles) {
                    out.writeInt(tile.x);
                    out.writeInt(tile.z);
                    short[] heights = tile.rawHeights();
                    int[] colors = tile.rawColors();
                    for (int i = 0; i < FarTerrainTile.GRID_LEN; i++) out.writeShort(heights[i]);
                    for (int i = 0; i < FarTerrainTile.GRID_LEN; i++) out.writeInt(colors[i]);
                }
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Disk cache is opportunistic; never disturb gameplay if read-only or unavailable.
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // Best-effort temp cleanup.
            }
        }
    }

    private static List<FarTerrainTile> readRegion(Path file) {
        List<FarTerrainTile> result = new ArrayList<>();
        try (var in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(Files.newInputStream(file))))) {
            if (in.readInt() != MAGIC || in.readByte() != VERSION) return List.of();
            int count = in.readUnsignedShort();
            if (count > FarTerrainCache.REGION_CHUNKS * FarTerrainCache.REGION_CHUNKS) return List.of();
            short[] heights = new short[FarTerrainTile.GRID_LEN];
            int[] colors = new int[FarTerrainTile.GRID_LEN];
            for (int t = 0; t < count; t++) {
                int cx = in.readInt();
                int cz = in.readInt();
                for (int i = 0; i < FarTerrainTile.GRID_LEN; i++) heights[i] = in.readShort();
                for (int i = 0; i < FarTerrainTile.GRID_LEN; i++) colors[i] = in.readInt();
                result.add(new FarTerrainTile(cx, cz, heights, colors));
            }
        } catch (IOException ignored) {
            return List.of();
        }
        return result;
    }
}
