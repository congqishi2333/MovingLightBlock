package com.congqishi.movinglightblock;

import com.congqishi.movinglightblock.entity.RollingLightEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives summoned light entities and places one hidden real-light block for each.
 *
 * <p>The entity is the visible, rolling object. The block is invisible and only exists so
 * Minecraft's vanilla lighting engine sees a real block light source. Block lighting is
 * grid based, so the light snaps between block positions even though the entity moves
 * smoothly.</p>
 */
public final class LightFollower {
    private static final Map<UUID, Placement> PLACEMENTS = new ConcurrentHashMap<>();
    private static final Map<UUID, RollingLightEntity> ENTITIES = new ConcurrentHashMap<>();
    private static final int[][] SEARCH_OFFSETS = buildSearchOffsets();

    private record Placement(Level level, BlockPos pos, BlockState previous) {
    }

    private LightFollower() {
    }

    /**
     * Cells tried around the cube, in priority order: its own cell, nearby horizontal cells,
     * then vertical neighbours. Incomplete blocks (slabs, stairs, fences, snow, grass, ...)
     * often have canBeReplaced() == false, so looking only above/below used to leave no target
     * and the real light vanished while the visible cube remained.
     */
    private static int[][] buildSearchOffsets() {
        List<int[]> offsets = new ArrayList<>();
        offsets.add(new int[] {0, 0, 0});

        for (int radius = 1; radius <= 2; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == radius) {
                        offsets.add(new int[] {dx, 0, dz});
                    }
                }
            }
        }

        for (int dy : new int[] {1, -1, 2, -2, 3, -3}) {
            offsets.add(new int[] {0, dy, 0});
        }

        for (int dy : new int[] {1, -1, 2, -2}) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        offsets.add(new int[] {dx, dy, dz});
                    }
                }
            }
        }

        return offsets.toArray(new int[0][]);
    }

    public static void register(RollingLightEntity entity) {
        ENTITIES.put(entity.getUUID(), entity);
    }

    /** The cube currently owned by this player, if any. */
    public static RollingLightEntity getOwned(UUID playerId) {
        for (RollingLightEntity entity : ENTITIES.values()) {
            if (playerId.equals(entity.getOwner())) {
                return entity;
            }
        }
        return null;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID, RollingLightEntity>> iterator = ENTITIES.entrySet().iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next().getKey();
            RollingLightEntity entity = ENTITIES.get(id);

            if (entity == null || entity.isRemoved() || !(entity.level() instanceof ServerLevel level)) {
                clearBlock(id);
                iterator.remove();
                continue;
            }

            Placement placement = PLACEMENTS.get(id);
            if (placement != null
                    && (placement.level() != level
                    || !level.getBlockState(placement.pos()).is(MovingLightBlockMod.MOVING_LIGHT.get()))) {
                // Someone replaced or removed our hidden block; forget it so it can be re-placed.
                PLACEMENTS.remove(id);
                placement = null;
            }

            if (!Config.ENABLED.get()) {
                clearBlock(id);
                entity.stopFollowing();
                continue;
            }

            ServerPlayer target = findFollowTarget(level, entity);
            if (target == null) {
                // Owner is offline: keep the cube and its light where they are. Re-login
                // resumes following; clearing the block here caused a visible light pop.
                entity.stopFollowing();
                continue;
            }

            // Hard leash: never leave the cube far away. Only rescue from a solid block
            // after it has actually stayed buried while idle - flying through blocks is
            // normal, so an instant check here teleported it on every step up.
            if (entity.distanceTo(target) > Config.TELEPORT_DISTANCE.get()
                    || entity.shouldRescueFromSolid()) {
                entity.teleportTo(findTeleportPos(level, target));
            }

            double startDistance = Config.FOLLOW_DISTANCE.get();
            double stopDistance = Math.min(Config.STOP_DISTANCE.get(), startDistance - 0.1D);
            entity.follow(target, Config.Y_OFFSET.get(), Config.ANIMATION.get(),
                    startDistance, stopDistance, Config.CHASE_SPEED.get());

            BlockPos targetPos = findTarget(level, entity.blockPosition());
            if (targetPos == null) {
                // Temporarily surrounded by non-replaceable blocks: keep the last valid light
                // instead of making it disappear while the visible cube is still there.
                continue;
            }

            BlockState desiredState = MovingLightBlock.stateForLevel(entity.getLightLevel());

            // Keep the block in place while the cube stays in the same cell. Re-creating it
            // every tick made the real light flicker and caused unnecessary lighting updates.
            if (placement == null
                    || placement.level() != level
                    || !placement.pos().equals(targetPos)) {
                if (placement != null) {
                    restore(placement);
                }

                BlockState oldState = level.getBlockState(targetPos);
                if (oldState.is(MovingLightBlockMod.MOVING_LIGHT.get())) {
                    // Adopt an orphaned light block instead of leaking it forever, and make
                    // sure it immediately matches this cube's torch count.
                    PLACEMENTS.put(id, new Placement(level, targetPos, Blocks.AIR.defaultBlockState()));
                    if (!oldState.equals(desiredState)) {
                        level.setBlock(targetPos, desiredState, Block.UPDATE_ALL);
                    }
                } else {
                    level.setBlock(targetPos, desiredState, Block.UPDATE_ALL);
                    PLACEMENTS.put(id, new Placement(level, targetPos, oldState));
                }
            } else {
                BlockState current = level.getBlockState(targetPos);
                if (current.is(MovingLightBlockMod.MOVING_LIGHT.get())
                        && current.getValue(MovingLightBlock.LEVEL)
                        != desiredState.getValue(MovingLightBlock.LEVEL)) {
                    // Torch count changed in the cube's GUI: refresh just the light property.
                    level.setBlock(targetPos, desiredState, Block.UPDATE_ALL);
                }
            }
        }
    }

    private static ServerPlayer findFollowTarget(ServerLevel level, RollingLightEntity entity) {
        UUID owner = entity.getOwner();
        if (owner != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
            if (player != null && player.level() == level) {
                return player;
            }
        }

        ServerPlayer nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            double distance = player.distanceToSqr(entity);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private static BlockPos findTarget(Level level, BlockPos base) {
        for (int[] offset : SEARCH_OFFSETS) {
            BlockPos pos = base.offset(offset[0], offset[1], offset[2]);
            BlockState state = level.getBlockState(pos);
            // MovingLightBlock.canBeReplaced() is true, so our own light counts as a valid
            // target cell. Otherwise the light would climb one block every tick.
            if (state.canBeReplaced()) {
                return pos;
            }
        }
        return null;
    }

    /** Safe spot behind the player, used while the cube is following or being rescued. */
    public static Vec3 findTeleportPos(ServerLevel level, ServerPlayer player) {
        return findPos(level, player, false);
    }

    /** Safe spot in front of the player, used when the summon item is placed. */
    public static Vec3 findSpawnPos(ServerLevel level, ServerPlayer player) {
        return findPos(level, player, true);
    }

    /**
     * Finds a safe spot near the player, kept away from the player's feet so the hidden
     * light block cannot get in the way. Never returns a solid block position.
     */
    private static Vec3 findPos(ServerLevel level, ServerPlayer player, boolean inFront) {
        double distance = Config.FOLLOW_DISTANCE.get();
        Vec3 look = player.getLookAngle();
        double fx = look.x;
        double fz = look.z;
        double[][] directions = inFront
                ? new double[][] {
                        {fx, fz},
                        {fz, -fx},
                        {-fz, fx},
                        {-fx, -fz}
                }
                : new double[][] {
                        {-fx, -fz},
                        {fz, -fx},
                        {-fz, fx},
                        {fx, fz}
                };
        int baseY = player.blockPosition().getY() + Config.Y_OFFSET.get();

        for (double[] direction : directions) {
            double length = Math.hypot(direction[0], direction[1]);
            if (length < 1.0E-4D) {
                continue;
            }
            double dx = direction[0] / length;
            double dz = direction[1] / length;
            int offsetX = (int) Math.round(dx * distance);
            int offsetZ = (int) Math.round(dz * distance);

            for (int dy : new int[] {0, 1, -1, 2, -2}) {
                BlockPos pos = player.blockPosition().offset(offsetX, dy, offsetZ);
                int y = baseY + dy;
                pos = new BlockPos(pos.getX(), y, pos.getZ());
                BlockState state = level.getBlockState(pos);
                if (state.canBeReplaced() && !state.is(MovingLightBlockMod.MOVING_LIGHT.get())) {
                    return new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                }
            }
        }

        return new Vec3(player.getX(), player.getY() + 1.0D, player.getZ());
    }

    public static void discardOwned(UUID playerId) {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, RollingLightEntity> entry : ENTITIES.entrySet()) {
            if (playerId.equals(entry.getValue().getOwner())) {
                toRemove.add(entry.getKey());
            }
        }
        for (UUID id : toRemove) {
            RollingLightEntity entity = ENTITIES.remove(id);
            clearBlock(id);
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
        }
    }

    public static void relocate(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        List<RollingLightEntity> owned = new ArrayList<>();
        for (RollingLightEntity entity : ENTITIES.values()) {
            if (player.getUUID().equals(entity.getOwner())) {
                owned.add(entity);
            }
        }

        for (RollingLightEntity entity : owned) {
            UUID id = entity.getUUID();
            clearBlock(id);
            ENTITIES.remove(id);
            if (entity.isRemoved()) {
                continue;
            }

            // Move the same cube across dimensions instead of throwing it away and
            // summoning a fresh one: its UUID and saved state are carried over.
            Entity moved = entity.changeDimension(new DimensionTransition(
                    level, findTeleportPos(level, player), Vec3.ZERO,
                    entity.getYRot(), entity.getXRot(), DimensionTransition.DO_NOTHING));
            if (moved instanceof RollingLightEntity light && !light.isRemoved()) {
                light.stopFollowing();
                register(light);
            }
        }
    }

    public static void clearBlocks() {
        Iterator<Map.Entry<UUID, Placement>> iterator = PLACEMENTS.entrySet().iterator();
        while (iterator.hasNext()) {
            restore(iterator.next().getValue());
            iterator.remove();
        }
    }

    public static void clearAll() {
        clearBlocks();
        ENTITIES.clear();
    }

    private static void clearBlock(UUID entityId) {
        Placement placement = PLACEMENTS.remove(entityId);
        if (placement != null) {
            restore(placement);
        }
    }

    private static void restore(Placement placement) {
        Level level = placement.level();
        // getBlockState() force-loads the chunk. During shutdown that chunk work can no
        // longer finish, so the server thread waits forever. Never restore a placement
        // whose chunk is already unloaded.
        if (!level.isLoaded(placement.pos())) {
            return;
        }
        // Only restore if the block is still ours; another mod/player may have changed it.
        if (level.getBlockState(placement.pos()).is(MovingLightBlockMod.MOVING_LIGHT.get())) {
            level.setBlock(placement.pos(), placement.previous(), Block.UPDATE_ALL);
        }
    }
}
