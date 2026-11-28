package com.congqishi.movinglightblock;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config (synced between server and client, stored in config/movinglightblock-common.toml).
 */
public final class Config {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue ANIMATION;
    public static final ModConfigSpec.BooleanValue PASS_THROUGH_WALLS;
    public static final ModConfigSpec.ConfigValue<String> RETRACT_KEY;
    public static final ModConfigSpec.IntValue LIGHT_LEVEL;
    public static final ModConfigSpec.IntValue Y_OFFSET;
    public static final ModConfigSpec.DoubleValue FOLLOW_DISTANCE;
    public static final ModConfigSpec.DoubleValue STOP_DISTANCE;
    public static final ModConfigSpec.DoubleValue CHASE_SPEED;
    public static final ModConfigSpec.DoubleValue TELEPORT_DISTANCE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Moving Light Block - a glowing block that follows each player.")
                .translation("movinglightblock.configuration.general")
                .push("general");

        ENABLED = builder
                .comment("Set to false to disable the following light block entirely.")
                .translation("movinglightblock.configuration.enabled")
                .define("enabled", true);

        ANIMATION = builder
                .comment("Make the visible cube tumble/roll as it follows the player.",
                         "Set to false to keep it upright while still following smoothly.")
                .translation("movinglightblock.configuration.animation")
                .define("animation", true);

        PASS_THROUGH_WALLS = builder
                .comment("If true, the cube ignores block collision and can fly through walls.",
                         "If false, it collides with blocks but still never blocks or pushes players.")
                .translation("movinglightblock.configuration.passThroughWalls")
                .define("passThroughWalls", true);

        RETRACT_KEY = builder
                .comment("Combination used while aiming at the cube to pick it back up as an item.",
                         "Format: modifiers + button/key, joined by '+'. Default 'shift+right'.",
                         "Examples: 'shift+right', 'ctrl+left', 'alt+middle', 'shift+key.keyboard.r'.")
                .translation("movinglightblock.configuration.retractKey")
                .define("retractKey", "shift+right");

        LIGHT_LEVEL = builder
                .comment("Initial light level of a newly summoned cube, from 0 (dark) to 15.",
                         "The cube has no light by default; put torches in its right-hand GUI slot",
                         "to raise the level by one per torch, up to 15. The GUI value is saved",
                         "with the cube and overrides this config after summoning.")
                .translation("movinglightblock.configuration.lightLevel")
                .defineInRange("initialLightLevel", 0, 0, 15);

        Y_OFFSET = builder
                .comment("Vertical offset from the player's feet block where the light is placed.",
                         "0 = at feet, 1 = at head, 2 = above head.")
                .translation("movinglightblock.configuration.yOffset")
                .defineInRange("yOffset", 0, -2, 3);

        FOLLOW_DISTANCE = builder
                .comment("How far the player must first walk away before the entity starts chasing.",
                         "After the first chase it keeps following as soon as the player leaves the stop radius.")
                .translation("movinglightblock.configuration.followDistance")
                .defineInRange("followDistance", 3.0D, 1.0D, 16.0D);

        STOP_DISTANCE = builder
                .comment("The entity stops chasing once it is this close again, so it does not",
                         "sit at the player's feet and block interactions.")
                .translation("movinglightblock.configuration.stopDistance")
                .defineInRange("stopDistance", 2.0D, 0.5D, 16.0D);

        CHASE_SPEED = builder
                .comment("Chase speed in blocks per second. Default 5.6 is roughly sprint speed.",
                         "The entity can fly, so it follows vertically as well.")
                .translation("movinglightblock.configuration.chaseSpeed")
                .defineInRange("chaseSpeed", 5.6D, 0.1D, 20.0D);

        TELEPORT_DISTANCE = builder
                .comment("If the entity ends up farther than this from its player, it is moved",
                         "back next to the player instead of being left behind or flying off.")
                .translation("movinglightblock.configuration.teleportDistance")
                .defineInRange("teleportDistance", 12.0D, 4.0D, 128.0D);

        builder.pop();
        SPEC = builder.build();
    }

    private Config() {
    }
}
