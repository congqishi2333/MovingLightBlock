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
    public static final ModConfigSpec.DoubleValue FOLLOW_DISTANCE;
    public static final ModConfigSpec.DoubleValue STOP_DISTANCE;
    public static final ModConfigSpec.DoubleValue CHASE_SPEED;

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

        FOLLOW_DISTANCE = builder
                .comment("How far the player must move away before the entity starts chasing (in blocks).")
                .defineInRange("followDistance", 3.0D, 1.0D, 16.0D);

        STOP_DISTANCE = builder
                .comment("The entity stops chasing once it is this close again, so it does not",
                         "sit at the player's feet and block interactions.")
                .defineInRange("stopDistance", 2.0D, 0.5D, 16.0D);

        CHASE_SPEED = builder
                .comment("Chase speed in blocks per second. Default 5.6 is roughly sprint speed.",
                         "The entity can fly, so it follows vertically as well.")
                .defineInRange("chaseSpeed", 5.6D, 0.1D, 20.0D);

        builder.pop();
        SPEC = builder.build();
    }

    private Config() {
    }
}
