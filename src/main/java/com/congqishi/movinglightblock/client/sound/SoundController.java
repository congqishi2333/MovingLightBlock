package com.congqishi.movinglightblock.client.sound;

import com.congqishi.movinglightblock.entity.RollingLightEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns the visible cube's motion into the four configurable sound phases.
 *
 * <p>Summon, landing and idle are one-shots fired when that state is entered. Moving is a
 * looping sound that starts on entry and stops as soon as the cube stops moving. Only the
 * local player's own cube is tracked.</p>
 */
public final class SoundController {
    private static final double MOVEMENT_EPSILON_SQ = 1.0E-4D;
    private static final double FALL_EPSILON = 1.0E-3D;

    private static final Map<UUID, Track> TRACKS = new HashMap<>();

    private enum Phase {
        NONE, SUMMON, MOVE, IDLE
    }

    private static final class Track {
        private boolean initialized;
        private Phase phase = Phase.NONE;
        private boolean falling;
        private double lastX;
        private double lastY;
        private double lastZ;
    }

    private SoundController() {
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            reset();
            return;
        }

        ExternalSoundPlayer.tick();
        SoundSettings settings = SoundSettings.get();
        Set<UUID> present = new HashSet<>();
        AABB search = minecraft.player.getBoundingBox().inflate(64.0D);

        for (RollingLightEntity entity :
                minecraft.level.getEntitiesOfClass(RollingLightEntity.class, search)) {
            UUID owner = entity.getOwner();
            if (owner == null || !owner.equals(minecraft.player.getUUID())) {
                continue;
            }

            UUID id = entity.getUUID();
            present.add(id);
            Track track = TRACKS.computeIfAbsent(id, key -> new Track());

            if (!track.initialized) {
                track.initialized = true;
                track.phase = Phase.SUMMON;
                track.lastX = entity.getX();
                track.lastY = entity.getY();
                track.lastZ = entity.getZ();
                ExternalSoundPlayer.playOnce(settings.summon);
                continue;
            }

            double dx = entity.getX() - track.lastX;
            double dy = entity.getY() - track.lastY;
            double dz = entity.getZ() - track.lastZ;
            boolean moving = dx * dx + dy * dy + dz * dz > MOVEMENT_EPSILON_SQ;
            boolean falling = dy < -FALL_EPSILON;

            if (track.falling && !falling && !moving && track.phase == Phase.MOVE) {
                ExternalSoundPlayer.playOnce(settings.land);
            }
            track.falling = falling;

            Phase next = moving ? Phase.MOVE : Phase.IDLE;
            if (next != track.phase) {
                if (track.phase == Phase.MOVE) {
                    ExternalSoundPlayer.stopLoop(id);
                }
                if (next == Phase.MOVE) {
                    ExternalSoundPlayer.startLoop(id, settings.move);
                } else if (next == Phase.IDLE) {
                    ExternalSoundPlayer.playOnce(settings.idle);
                }
                track.phase = next;
            }

            track.lastX = entity.getX();
            track.lastY = entity.getY();
            track.lastZ = entity.getZ();
        }

        Iterator<Map.Entry<UUID, Track>> iterator = TRACKS.entrySet().iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next().getKey();
            if (!present.contains(id)) {
                ExternalSoundPlayer.stopLoop(id);
                iterator.remove();
            }
        }
    }

    /** Called after the JSON is saved: stop current loops and let the next tick pick up new values. */
    public static void onSettingsChanged() {
        ExternalSoundPlayer.stopAll();
        for (Track track : TRACKS.values()) {
            track.phase = Phase.NONE;
            track.falling = false;
        }
    }

    public static void reset() {
        ExternalSoundPlayer.stopAll();
        TRACKS.clear();
    }
}
