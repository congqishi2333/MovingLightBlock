package com.congqishi.movinglightblock;

import com.congqishi.movinglightblock.entity.RollingLightEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

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

        return spawn(serverLevel, player, context.getItemInHand());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(stack);
        }

        InteractionResult result = spawn(serverLevel, player, stack);
        return result == InteractionResult.SUCCESS
                ? InteractionResultHolder.success(stack)
                : InteractionResultHolder.pass(stack);
    }

    private InteractionResult spawn(ServerLevel level, Player player, ItemStack stack) {
        // One summoned light per player: replace the old one instead of stacking entities.
        LightFollower.discardOwned(player.getUUID());

        RollingLightEntity entity = ModEntities.ROLLING_LIGHT.get().create(level);
        if (entity == null) {
            return InteractionResult.FAIL;
        }

        entity.setPos(LightFollower.findTeleportPos(level, (ServerPlayer) player));
        entity.setOwner(player.getUUID());
        level.addFreshEntity(entity);
        LightFollower.register(entity);

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
