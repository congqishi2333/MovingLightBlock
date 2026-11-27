package com.congqishi.movinglightblock.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Parses the configurable retract combination, e.g. {@code shift+right}, {@code ctrl+left},
 * {@code alt+middle} or {@code shift+key.keyboard.r}.
 *
 * <p>Only the client reads this; the server never trusts the combination itself, it only
 * validates that the targeted entity belongs to the player.</p>
 */
public record RetractBinding(boolean shift, boolean ctrl, boolean alt, boolean mouse, int code) {
    public static final RetractBinding DEFAULT = new RetractBinding(true, false, false, true, GLFW.GLFW_MOUSE_BUTTON_RIGHT);

    public static RetractBinding parse(String text) {
        if (text == null || text.isBlank()) {
            return DEFAULT;
        }

        boolean shift = false;
        boolean ctrl = false;
        boolean alt = false;
        boolean mouse = false;
        int code = -1;

        for (String raw : text.toLowerCase(Locale.ROOT).split("\\+")) {
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            switch (token) {
                case "shift", "sneak" -> shift = true;
                case "ctrl", "control" -> ctrl = true;
                case "alt" -> alt = true;
                case "right", "rmb", "mouse.right", "key.mouse.right" -> {
                    mouse = true;
                    code = GLFW.GLFW_MOUSE_BUTTON_RIGHT;
                }
                case "left", "lmb", "mouse.left", "key.mouse.left" -> {
                    mouse = true;
                    code = GLFW.GLFW_MOUSE_BUTTON_LEFT;
                }
                case "middle", "mmb", "mouse.middle", "key.mouse.middle" -> {
                    mouse = true;
                    code = GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
                }
                default -> {
                    InputConstants.Key key = InputConstants.getKey(
                            token.startsWith("key.") ? token : "key.keyboard." + token);
                    if (key.getValue() >= 0) {
                        mouse = false;
                        code = key.getValue();
                    }
                }
            }
        }

        return code < 0 ? DEFAULT : new RetractBinding(shift, ctrl, alt, mouse, code);
    }

    public boolean matchesMouse(int button, int modifiers) {
        return mouse && code == button && modifiersMatch(modifiers);
    }

    public boolean matchesKey(int key, int modifiers) {
        return !mouse && code == key && modifiersMatch(modifiers);
    }

    private boolean modifiersMatch(int modifiers) {
        if (shift && (modifiers & GLFW.GLFW_MOD_SHIFT) == 0) {
            return false;
        }
        if (ctrl && (modifiers & GLFW.GLFW_MOD_CONTROL) == 0) {
            return false;
        }
        return !alt || (modifiers & GLFW.GLFW_MOD_ALT) != 0;
    }
}
