package com.congqishi.movinglightblock;

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
 * Places one moving light block per player on the logical server.
 *
 * <p>Block lighting is grid based, so the light snaps between block positions as the player
 * moves. The previous block is restored to its original state before placing a new one, so
 * ordinary terrain is never permanently replaced.</p>
 */
public final class LightFollower {
    private static final Map<UUID, Placement> PLACEMENTS = new ConcurrentHashMap<>();
    private static final int[] SEARCH_OFFSETS = {0, 1, -1, 2, -2, 3};

    private record Placement(Level level, BlockPos pos, BlockState previous) {
    }

    private LightFollower() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            UUID id = player.getUUID();

            // Remove the block from the previous tick first.
            clear(id);

            if (!Config.ENABLED.get()) {
                continue;
            }

            Level level = player.level();
            BlockPos target = findTarget(level, player);
            if (target == null) {
                continue;
            }

            BlockState oldState = level.getBlockState(target);
            if (oldState.is(MovingLightBlockMod.MOVING_LIGHT.get())) {
                continue;
            }

            level.setBlock(target, MovingLightBlockMod.MOVING_LIGHT.get().defaultBlockState(),
                    Block.UPDATE_ALL);
            PLACEMENTS.put(id, new Placement(level, target, oldState));
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

    public static void clear(UUID playerId) {
        Placement placement = PLACEMENTS.remove(playerId);
        if (placement != null) {
            restore(placement);
        }
    }

    public static void clearAll() {
        Iterator<Map.Entry<UUID, Placement>> iterator = PLACEMENTS.entrySet().iterator();
        while (iterator.hasNext()) {
            restore(iterator.next().getValue());
            iterator.remove();
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
