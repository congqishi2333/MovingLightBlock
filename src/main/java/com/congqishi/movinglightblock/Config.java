package com.congqishi.movinglightblock;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config (synced between server and client, stored in config/movinglightblock-common.toml).
 */
public final class Config {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue ANIMATION;
    public static final ModConfigSpec.IntValue LIGHT_LEVEL;
    public static final ModConfigSpec.IntValue Y_OFFSET;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Moving Light Block - a glowing block that follows each player.").push("general");

        ENABLED = builder
                .comment("Set to false to disable the following light block entirely.")
                .define("enabled", true);

        ANIMATION = builder
                .comment("Make the visible cube tumble/roll as it follows the player.",
                         "Set to false to keep it upright while still following smoothly.")
                .define("animation", true);

        LIGHT_LEVEL = builder
                .comment("Real light level emitted by the block, from 0 (dark) to 15 (glowstone).",
                         "Changing this value updates already-placed blocks on config reload.")
                .defineInRange("lightLevel", 15, 0, 15);

        Y_OFFSET = builder
                .comment("Vertical offset from the player's feet block where the light is placed.",
                         "0 = at feet, 1 = at head, 2 = above head.")
                .defineInRange("yOffset", 0, -2, 3);

        builder.pop();
        SPEC = builder.build();
    }

    private Config() {
    }
}
