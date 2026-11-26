package com.congqishi.movinglightblock;

import com.congqishi.movinglightblock.entity.RollingLightEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places one moving light block per player on the logical server and drives the
 * client-side rolling visual entity.
 *
 * <p>Block lighting is grid based, so the light snaps between block positions as the player
 * moves. The previous block is restored to its original state before placing a new one, so
 * ordinary terrain is never permanently replaced.</p>
 */
public final class LightFollower {
    private static final Map<UUID, Placement> PLACEMENTS = new ConcurrentHashMap<>();
    private static final Map<UUID, RollingLightEntity> VISUALS = new ConcurrentHashMap<>();
    private static final int[] SEARCH_OFFSETS = {0, 1, -1, 2, -2, 3};

    private record Placement(Level level, BlockPos pos, BlockState previous) {
    }

    private LightFollower() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            UUID id = player.getUUID();

            // Remove last tick's light block, but keep the visual entity so it can animate.
            clearBlock(id);

            if (!Config.ENABLED.get()) {
                clearVisual(id);
                continue;
            }

            Level level = player.level();
            BlockPos target = findTarget(level, player);
            if (target == null) {
                clearVisual(id);
                continue;
            }

            BlockState oldState = level.getBlockState(target);
            if (!oldState.is(MovingLightBlockMod.MOVING_LIGHT.get())) {
                level.setBlock(target, MovingLightBlockMod.MOVING_LIGHT.get().defaultBlockState(),
                        Block.UPDATE_ALL);
                PLACEMENTS.put(id, new Placement(level, target, oldState));
            }

            updateVisual(player);
        }
    }

    private static BlockPos findTarget(Level level, ServerPlayer player) {
        BlockPos base = player.blockPosition().above(Config.Y_OFFSET.get());
        for (int dy : SEARCH_OFFSETS) {
            BlockPos pos = base.above(dy);
            BlockState state = level.getBlockState(pos);
            if (state.canBeReplaced() && !state.is(MovingLightBlockMod.MOVING_LIGHT.get())) {
                return pos;
            }
        }
        return null;
    }

    private static void updateVisual(ServerPlayer player) {
        UUID id = player.getUUID();
        RollingLightEntity visual = VISUALS.get(id);

        if (visual == null || visual.isRemoved() || visual.level() != player.level()) {
            clearVisual(id);
            visual = new RollingLightEntity(ModEntities.ROLLING_LIGHT.get(), player.level());
            visual.setPos(player.getX(), player.getY() + Config.Y_OFFSET.get(), player.getZ());
            player.level().addFreshEntity(visual);
            VISUALS.put(id, visual);
        }

        visual.follow(player, Config.Y_OFFSET.get(), Config.ANIMATION.get());
    }

    public static void clear(UUID playerId) {
        clearBlock(playerId);
        clearVisual(playerId);
    }

    private static void clearBlock(UUID playerId) {
        Placement placement = PLACEMENTS.remove(playerId);
        if (placement != null) {
            restore(placement);
        }
    }

    private static void clearVisual(UUID playerId) {
        RollingLightEntity visual = VISUALS.remove(playerId);
        if (visual != null && !visual.isRemoved()) {
            visual.discard();
        }
    }

    public static void clearAll() {
        Iterator<Map.Entry<UUID, Placement>> iterator = PLACEMENTS.entrySet().iterator();
        while (iterator.hasNext()) {
            restore(iterator.next().getValue());
            iterator.remove();
        }
        Iterator<Map.Entry<UUID, RollingLightEntity>> visuals = VISUALS.entrySet().iterator();
        while (visuals.hasNext()) {
            RollingLightEntity visual = visuals.next().getValue();
            if (!visual.isRemoved()) {
                visual.discard();
            }
            visuals.remove();
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
