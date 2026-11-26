package com.congqishi.movinglightblock;

import com.congqishi.movinglightblock.entity.RollingLightEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Right-click to summon a rolling light entity that follows its owner.
 *
 * <p>The hidden light block used for real block lighting is still placed by
 * {@link LightFollower}; this item only creates the visible, animated entity.</p>
 */
public final class FollowLightItem extends Item {
    public FollowLightItem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        BlockPos spawnPos = context.getClickedPos().relative(context.getClickedFace());
        Vec3 requested = new Vec3(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);
        return spawn(serverLevel, player, requested, context.getItemInHand());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack);
        }

        Vec3 requested = new Vec3(player.getX(), player.getY() + Config.Y_OFFSET.get(), player.getZ());
        InteractionResult result = spawn(serverLevel, player, requested, stack);
        return result == InteractionResult.SUCCESS
                ? InteractionResultHolder.success(stack)
                : InteractionResultHolder.pass(stack);
    }

    private InteractionResult spawn(ServerLevel level, Player player, Vec3 requested, ItemStack stack) {
        RollingLightEntity entity = ModEntities.ROLLING_LIGHT.get().create(level);
        if (entity == null) {
            return InteractionResult.FAIL;
        }

        Vec3 spawnPos = keepAwayFromPlayer(player, requested);
        entity.setPos(spawnPos);
        entity.setOwner(player.getUUID());
        level.addFreshEntity(entity);
        LightFollower.register(entity);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    /** Never summon the cube on top of the player, where its hidden light block would be in the way. */
    private static Vec3 keepAwayFromPlayer(Player player, Vec3 requested) {
        double minimum = Config.FOLLOW_DISTANCE.get();
        double dx = requested.x - player.getX();
        double dz = requested.z - player.getZ();
        if (Math.hypot(dx, dz) >= minimum) {
            return requested;
        }

        Vec3 look = player.getLookAngle();
        double backX = -look.x;
        double backZ = -look.z;
        double length = Math.hypot(backX, backZ);
        if (length < 1.0E-4D) {
            backX = 1.0D;
            backZ = 0.0D;
        } else {
            backX /= length;
            backZ /= length;
        }

        return new Vec3(player.getX() + backX * minimum,
                player.getY() + Config.Y_OFFSET.get(),
                player.getZ() + backZ * minimum);
    }
}
