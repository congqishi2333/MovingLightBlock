package com.congqishi.movinglightblock.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.congqishi.movinglightblock.MovingLightBlockMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Client-side rolling settings loaded from a standard resource pack:
 * {@code assets/movinglightblock/rolling.json}.
 *
 * <p>Missing or invalid files fall back to vanilla-like defaults, so the resource pack is
 * entirely optional. This is read again whenever the resource manager changes (F3 + T).</p>
 */
public final class RollingConfig {
    public enum Axis {
        AUTO, X, Y, Z
    }

    private static final ResourceLocation LOCATION =
            ResourceLocation.fromNamespaceAndPath(MovingLightBlockMod.MODID, "rolling.json");

    public boolean enabled = true;
    public Axis axis = Axis.AUTO;
    public float degreesPerBlock = 90.0F;
    public float speed = 1.0F;

    public static RollingConfig load(ResourceManager manager) {
        RollingConfig config = new RollingConfig();
        manager.getResource(LOCATION).ifPresent(resource -> {
            try (var reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                config.enabled = bool(json, "enabled", config.enabled);
                config.axis = axis(json, "axis", config.axis);
                config.degreesPerBlock = clamp(floatValue(json, "degreesPerBlock", config.degreesPerBlock), 0.0F, 720.0F);
                config.speed = clamp(floatValue(json, "speed", config.speed), 0.0F, 10.0F);
                if (config.axis == null) {
                    config.enabled = false;
                    config.axis = Axis.AUTO;
                }
            } catch (Exception ignored) {
                // Keep defaults; a broken JSON must never break rendering.
            }
        });
        return config;
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsBoolean() : fallback;
    }

    private static float floatValue(JsonObject json, String key, float fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsFloat() : fallback;
    }

    private static Axis axis(JsonObject json, String key, Axis fallback) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) {
            return fallback;
        }
        String value = json.get(key).getAsString().trim().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "auto" -> Axis.AUTO;
            case "x" -> Axis.X;
            case "y" -> Axis.Y;
            case "z" -> Axis.Z;
            case "none" -> null;
            default -> fallback;
        };
    }

    private static float clamp(float value, float min, float max) {
        return Math.min(Math.max(value, min), max);
    }

    private RollingConfig() {
    }
}
