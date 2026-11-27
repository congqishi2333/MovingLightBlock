package com.congqishi.movinglightblock.client;

import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * In-game selectable model presets.
 *
 * <p>Presets are JSON files using the same schema as {@code assets/movinglightblock/rolling.json}.
 * They live in {@code config/movinglightblock/models/} and the chosen file name is stored in
 * {@code config/movinglightblock/active_model.txt}. "default" means the resource pack file is used.</p>
 */
public final class ModelManager {
    public static final String DEFAULT = "default";

    private static final Path DIR = FMLPaths.CONFIGDIR.get()
            .resolve("movinglightblock").resolve("models");
    private static final Path ACTIVE = FMLPaths.CONFIGDIR.get()
            .resolve("movinglightblock").resolve("active_model.txt");

    private static final String[] BUNDLED = {
            "auto.json", "x-axis.json", "y-axis.json", "z-axis.json",
            "free-axis.json", "euler.json", "none.json"
    };

    /** Bumped whenever the selection changes so the renderer can refresh immediately. */
    private static volatile int revision;

    private ModelManager() {
    }

    public static int revision() {
        return revision;
    }

    /** Makes sure the built-in presets exist, then returns all selectable names. */
    public static List<String> list() {
        try {
            Files.createDirectories(DIR);
            for (String name : BUNDLED) {
                Path target = DIR.resolve(name);
                if (!Files.exists(target)) {
                    try (InputStream in = ModelManager.class.getResourceAsStream(
                            "/assets/movinglightblock/models/" + name)) {
                        if (in != null) {
                            Files.copy(in, target);
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            // A read-only config folder simply means no presets are available.
        }

        List<String> names = new ArrayList<>();
        names.add(DEFAULT);
        if (Files.isDirectory(DIR)) {
            try (Stream<Path> stream = Files.list(DIR)) {
                stream.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.toLowerCase().endsWith(".json"))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(names::add);
            } catch (IOException ignored) {
                // Keep just the default entry.
            }
        }
        return names;
    }

    public static String active() {
        try {
            if (Files.exists(ACTIVE)) {
                String name = Files.readString(ACTIVE).trim();
                if (!name.isEmpty() && name.toLowerCase().endsWith(".json")
                        && Files.isRegularFile(DIR.resolve(name))) {
                    return name;
                }
            }
        } catch (IOException ignored) {
            // Fall through to the default.
        }
        return DEFAULT;
    }

    public static void select(String name) {
        try {
            Files.createDirectories(DIR);
            if (DEFAULT.equals(name)) {
                Files.deleteIfExists(ACTIVE);
            } else {
                Files.writeString(ACTIVE, name);
            }
        } catch (IOException ignored) {
            // The in-memory revision still changes so the renderer re-reads what it can.
        }
        revision++;
    }

    /** Loads the selected preset, or the resource pack file when using the default. */
    public static RollingConfig load(ResourceManager manager) {
        String name = active();
        if (!DEFAULT.equals(name)) {
            try {
                String text = Files.readString(DIR.resolve(name));
                return RollingConfig.parse(JsonParser.parseString(text).getAsJsonObject());
            } catch (Exception ignored) {
                // Broken preset: fall back to the resource pack configuration.
            }
        }
        return RollingConfig.load(manager);
    }
}
