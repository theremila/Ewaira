package remila.sex.cirrus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import remila.sex.cirrus.ModIdentity;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.List;

public final class ConfigStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final List<Path> readPaths;

    public ConfigStore(Path file) { this(List.of(file)); }

    private ConfigStore(List<Path> readPaths) {
        this.file = readPaths.getFirst();
        this.readPaths = readPaths;
    }

    public static ConfigStore inDirectory(Path directory) {
        return new ConfigStore(List.of(directory.resolve(ModIdentity.CONFIG_FILE),
                directory.resolve(ModIdentity.PREVIOUS_MOD_ID + ".json"),
                directory.resolve(ModIdentity.LEGACY_MOD_ID + ".json")));
    }

    public CirrusSettings load() throws IOException {
        for (Path candidate : readPaths) {
            try {
                return load(candidate);
            } catch (NoSuchFileException missing) {
                // Try the next configuration only if this one is absent.
            }
        }
        return CirrusSettings.defaults();
    }

    private static CirrusSettings load(Path file) throws IOException {
        String contents = Files.readString(file);
        try {
            JsonObject root = JsonParser.parseString(contents).getAsJsonObject();
            if (root.has("schema") && (root.get("schema").getAsInt() < 1 || root.get("schema").getAsInt() > 2)) throw new IllegalArgumentException("Unsupported schema");
            EnumMap<Optimization, Boolean> modules = new EnumMap<>(Optimization.class);
            JsonObject options = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
            for (Optimization module : Optimization.values()) modules.put(module, bool(options, module.key()));
            // Validate old numeric fields before migrating; malformed files must remain untouched.
            legacyNumber(root, "terrain_margin", Double.MIN_VALUE, 1);
            legacyNumber(root, "item_lod_distance", 8, 256);
            legacyNumber(root, "terrain_lod_distance", 32, 512);
            return new CirrusSettings(bool(root, "enabled"), root.has("debug") && bool(root, "debug"), modules);
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid " + ModIdentity.MOD_NAME + " configuration: " + file, invalid);
        }
    }

    public void save(CirrusSettings settings) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 2);
        root.addProperty("enabled", settings.enabled());
        root.addProperty("debug", settings.debug());
        JsonObject modules = new JsonObject();
        for (Optimization module : Optimization.values()) modules.addProperty(module.key(), settings.modules().get(module));
        root.add("modules", modules);
        Path directory = file.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ModIdentity.MOD_ID + "-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(root) + "\n");
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void legacyNumber(JsonObject root, String key, double min, double max) {
        if (!root.has(key)) return;
        double value = root.get(key).getAsDouble();
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException("Invalid " + key);
    }

    private static boolean bool(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null) return true;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Expected a boolean: " + key);
        }
        return value.getAsBoolean();
    }
}
