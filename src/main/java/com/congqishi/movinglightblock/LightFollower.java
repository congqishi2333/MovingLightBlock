package com.congqishi.movinglightblock;

import com.congqishi.movinglightblock.entity.RollingLightEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
    private static final int[] SEARCH_OFFSETS = {0, 1, -1, 2, -2, 3};

    private record Placement(Level level, BlockPos pos, BlockState previous) {
    }

    private LightFollower() {
    }

    public static void register(RollingLightEntity entity) {
        ENTITIES.put(entity.getUUID(), entity);
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

            clearBlock(id);

            if (!Config.ENABLED.get()) {
                entity.stopFollowing();
                continue;
            }

            ServerPlayer target = findFollowTarget(level, entity);
            if (target == null) {
                entity.stopFollowing();
                continue;
            }

            // Hard leash: never leave the cube far away, and rescue it if it spawns in a wall.
            if (entity.distanceTo(target) > Config.TELEPORT_DISTANCE.get() || entity.isInsideSolid()) {
                entity.teleportTo(findTeleportPos(level, target));
            }

            double startDistance = Config.FOLLOW_DISTANCE.get();
            double stopDistance = Math.min(Config.STOP_DISTANCE.get(), startDistance - 0.1D);
            entity.follow(target, Config.Y_OFFSET.get(), Config.ANIMATION.get(),
                    startDistance, stopDistance, Config.CHASE_SPEED.get());

            BlockPos targetPos = findTarget(level, entity.blockPosition());
            if (targetPos == null) {
                continue;
            }

            BlockState oldState = level.getBlockState(targetPos);
            if (!oldState.is(MovingLightBlockMod.MOVING_LIGHT.get())) {
                level.setBlock(targetPos, MovingLightBlock.stateForLevel(Config.LIGHT_LEVEL.get()),
                        Block.UPDATE_ALL);
                PLACEMENTS.put(id, new Placement(level, targetPos, oldState));
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
        for (int dy : SEARCH_OFFSETS) {
            BlockPos pos = base.above(dy);
            BlockState state = level.getBlockState(pos);
            if (state.canBeReplaced() && !state.is(MovingLightBlockMod.MOVING_LIGHT.get())) {
                return pos;
            }
        }
        return null;
    }

    /**
     * Finds a safe spot beside the player, kept away from the player's feet so the hidden
     * light block cannot get in the way. Never returns a solid block position.
     */
    public static Vec3 findTeleportPos(ServerLevel level, ServerPlayer player) {
        double distance = Config.FOLLOW_DISTANCE.get();
        Vec3 look = player.getLookAngle();
        double[][] directions = {
                {-look.x, -look.z},
                {look.z, -look.x},
                {-look.z, look.x},
                {look.x, look.z}
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
            if (!entity.isRemoved()) {
                entity.discard();
            }

            RollingLightEntity replacement = ModEntities.ROLLING_LIGHT.get().create(player.level());
            if (replacement != null) {
                replacement.setPos(findTeleportPos((ServerLevel) player.level(), player));
                replacement.setOwner(player.getUUID());
                player.level().addFreshEntity(replacement);
                register(replacement);
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
        // Only restore if the block is still ours; another mod/player may have changed it.
        if (placement.level().getBlockState(placement.pos())
                .is(MovingLightBlockMod.MOVING_LIGHT.get())) {
            placement.level().setBlock(placement.pos(), placement.previous(), Block.UPDATE_ALL);
        }
    }
}
