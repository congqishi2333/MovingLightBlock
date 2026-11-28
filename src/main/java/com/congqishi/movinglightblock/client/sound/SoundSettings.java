package com.congqishi.movinglightblock.client.sound;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sound presets read from a dedicated config folder.
 *
 * <p>Nothing here creates the folder on startup: it is created only when the player opens
 * the sound settings screen. All audio information (file name, volume, speed) lives in the
 * JSON file; no sound is registered as a built-in resource.</p>
 */
public final class SoundSettings {
    public enum Phase {
        SUMMON, LAND, MOVE, IDLE;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final float MIN_VOLUME = 0.0F;
    public static final float MAX_VOLUME = 2.0F;
    public static final float MIN_SPEED = 0.25F;
    public static final float MAX_SPEED = 4.0F;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIR = FMLPaths.CONFIGDIR.get()
            .resolve("movinglightblock").resolve("sounds");
    private static final Path FILE = DIR.resolve("sounds.json");

    private static volatile SoundSettings instance;

    public Entry summon = new Entry();
    public Entry land = new Entry();
    public Entry move = new Entry();
    public Entry idle = new Entry();

    public static final class Entry {
        /** File name inside the sounds folder. Empty means this phase is silent. */
        public String file = "";
        public float volume = 1.0F;
        /** Playback rate; 1.0 is the original speed. */
        public float speed = 1.0F;

        public Entry copy() {
            Entry copy = new Entry();
            copy.file = this.file;
            copy.volume = this.volume;
            copy.speed = this.speed;
            return copy;
        }
    }

    public static SoundSettings get() {
        SoundSettings result = instance;
        if (result == null) {
            result = read();
            instance = result;
        }
        return result;
    }

    public Entry entry(Phase phase) {
        return switch (phase) {
            case SUMMON -> summon;
            case LAND -> land;
            case MOVE -> move;
            case IDLE -> idle;
        };
    }

    /** Creates the dedicated folder and a default JSON file. Called only by the settings screen. */
    public static void ensureCreated() {
        try {
            Files.createDirectories(DIR);
            if (!Files.exists(FILE)) {
                Files.writeString(FILE, GSON.toJson(new SoundSettings()), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            // Read-only config folder: the settings screen still works in memory.
        }
        reload();
    }

    public static void reload() {
        instance = read();
    }

    public static void save(SoundSettings settings) {
        try {
            Files.createDirectories(DIR);
            Files.writeString(FILE, GSON.toJson(settings), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Keep the in-memory values even when the file cannot be written.
        }
        instance = copyOf(settings);
    }

    private static SoundSettings copyOf(SoundSettings source) {
        SoundSettings copy = new SoundSettings();
        copy.summon = source.summon.copy();
        copy.land = source.land.copy();
        copy.move = source.move.copy();
        copy.idle = source.idle.copy();
        return copy;
    }

    private static SoundSettings read() {
        SoundSettings settings = new SoundSettings();
        if (!Files.isRegularFile(FILE)) {
            return settings;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (Phase phase : Phase.values()) {
                JsonElement element = root.get(phase.key());
                if (element != null && element.isJsonObject()) {
                    apply(element.getAsJsonObject(), settings.entry(phase));
                }
            }
        } catch (Exception ignored) {
            // A broken file must never disable the game; fall back to silent defaults.
        }
        return settings;
    }

    private static void apply(JsonObject json, Entry entry) {
        JsonElement file = json.get("file");
        if (file != null && file.isJsonPrimitive()) {
            entry.file = file.getAsString();
        }
        entry.volume = clamp(number(json, "volume", entry.volume), MIN_VOLUME, MAX_VOLUME);
        entry.speed = clamp(number(json, "speed", entry.speed), MIN_SPEED, MAX_SPEED);
    }

    private static float number(JsonObject json, String key, float fallback) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive()
                ? element.getAsFloat()
                : fallback;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Resolves a configured name to a file inside the dedicated folder, or null when unused. */
    public static Path file(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String clean = Path.of(name.trim()).getFileName().toString();
        if (!clean.toLowerCase(Locale.ROOT).endsWith(".ogg")) {
            clean += ".ogg";
        }
        return DIR.resolve(clean);
    }

    /** Existing .ogg names, for the settings screen's "list files" helper. */
    public static List<String> listFiles() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(DIR)) {
            return names;
        }
        try (var stream = Files.list(DIR)) {
            stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".ogg"))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(names::add);
        } catch (IOException ignored) {
            // Keep the empty list.
        }
        return names;
    }
}
