package com.congqishi.movinglightblock.client;

import com.congqishi.movinglightblock.MovingLightBlockMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * Client-side rolling settings loaded from a standard resource pack:
 * {@code assets/movinglightblock/rolling.json}.
 *
 * <p>Missing or invalid files fall back to vanilla-like defaults, so the resource pack is
 * entirely optional. This is read again whenever the resource manager changes (F3 + T).</p>
 */
public final class RollingConfig {
    public enum Axis {
        AUTO, X, Y, Z, FREE, EULER, NONE
    }

    private static final ResourceLocation LOCATION =
            ResourceLocation.fromNamespaceAndPath(MovingLightBlockMod.MODID, "rolling.json");
    private static final float AXIS_EPS = 1.0E-6F;

    public boolean enabled = true;
    public Axis axis = Axis.AUTO;

    /** Unit direction used when {@link #axis} is {@link Axis#FREE}. */
    public Vector3f freeAxis = new Vector3f(0.0F, 1.0F, 0.0F);

    /** Degrees per block around world X/Y/Z when {@link #axis} is {@link Axis#EULER}. */
    public Vector3f eulerDegrees = new Vector3f(90.0F, 0.0F, 0.0F);

    public float degreesPerBlock = 90.0F;
    public float speed = 1.0F;

    public static RollingConfig load(ResourceManager manager) {
        RollingConfig config = new RollingConfig();
        manager.getResource(LOCATION).ifPresent(resource -> {
            try (var reader = resource.openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                config.enabled = bool(json, "enabled", config.enabled);
                config.speed = clamp(floatValue(json, "speed", config.speed), 0.0F, 10.0F);
                config.degreesPerBlock = clamp(
                        floatValue(json, "degreesPerBlock", config.degreesPerBlock), 0.0F, 720.0F);

                parseAxis(json, config);

                // "degreesPerBlock": [x, y, z] is the shorthand for Euler mode.
                // An explicit "euler" object/array wins over it.
                Vector3f euler = vector(json.get("degreesPerBlock"), null);
                Vector3f explicitEuler = vector(json.get("euler"), null);
                if (explicitEuler != null) {
                    euler = explicitEuler;
                }
                if (euler != null) {
                    config.eulerDegrees = clampVector(euler, -720.0F, 720.0F);
                }

                Vector3f axisVector = vector(json.get("freeAxis"), null);
                if (axisVector == null) {
                    axisVector = vector(json.get("axisVector"), null);
                }
                if (axisVector != null && lengthSquared(axisVector) > AXIS_EPS) {
                    config.freeAxis = axisVector.normalize();
                }
                if (config.axis == Axis.FREE && lengthSquared(config.freeAxis) <= AXIS_EPS) {
                    config.axis = Axis.AUTO;
                }
                if (config.axis == Axis.NONE) {
                    config.enabled = false;
                }
            } catch (Exception ignored) {
                // Keep defaults; a broken JSON must never break rendering.
            }
        });
        return config;
    }

    private static void parseAxis(JsonObject json, RollingConfig config) {
        JsonElement element = json.get("axis");
        if (element == null || element.isJsonNull()) {
            return;
        }

        // "axis": [x, y, z] means a free/custom axis.
        if (element.isJsonArray()) {
            Vector3f vector = vector(element, null);
            if (vector != null && lengthSquared(vector) > AXIS_EPS) {
                config.axis = Axis.FREE;
                config.freeAxis = vector.normalize();
            }
            return;
        }

        if (!element.isJsonPrimitive()) {
            return;
        }

        String value = element.getAsString().trim().toLowerCase(Locale.ROOT);
        switch (value) {
            case "auto" -> config.axis = Axis.AUTO;
            case "x" -> config.axis = Axis.X;
            case "y" -> config.axis = Axis.Y;
            case "z" -> config.axis = Axis.Z;
            case "none" -> config.axis = Axis.NONE;
            case "free", "custom" -> {
                config.axis = Axis.FREE;
                Vector3f vector = vector(json.get("freeAxis"), null);
                if (vector == null) {
                    vector = vector(json.get("axisVector"), null);
                }
                if (vector != null && lengthSquared(vector) > AXIS_EPS) {
                    config.freeAxis = vector.normalize();
                }
            }
            case "euler", "xyz" -> config.axis = Axis.EULER;
            default -> {
                // Unknown values keep the current default.
            }
        }
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsBoolean()
                : fallback;
    }

    private static float floatValue(JsonObject json, String key, float fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsFloat()
                : fallback;
    }

    /** Reads either [x, y, z] or {"x": x, "y": y, "z": z}. */
    private static Vector3f vector(JsonElement element, Vector3f fallback) {
        if (element == null || element.isJsonNull()) {
            return fallback;
        }

        Vector3f base = fallback == null ? new Vector3f() : new Vector3f(fallback);

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.size() < 3) {
                return fallback;
            }
            try {
                return new Vector3f(
                        array.get(0).getAsFloat(),
                        array.get(1).getAsFloat(),
                        array.get(2).getAsFloat());
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            return new Vector3f(
                    number(object, "x", base.x),
                    number(object, "y", base.y),
                    number(object, "z", base.z));
        }

        return fallback;
    }

    private static float number(JsonObject json, String key, float fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive()
                ? json.get(key).getAsFloat()
                : fallback;
    }

    private static Vector3f clampVector(Vector3f value, float min, float max) {
        return new Vector3f(
                clamp(value.x, min, max),
                clamp(value.y, min, max),
                clamp(value.z, min, max));
    }

    private static float lengthSquared(Vector3f value) {
        return value.x * value.x + value.y * value.y + value.z * value.z;
    }

    private static float clamp(float value, float min, float max) {
        return Math.min(Math.max(value, min), max);
    }

    private RollingConfig() {
    }
}
