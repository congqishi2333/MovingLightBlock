package com.congqishi.movinglightblock;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The invisible, real-light source block.
 *
 * <p>Minecraft bakes a block's light value into every {@link BlockState} when the block is
 * registered, so reading a config value in the block constructor would fail (configs are not
 * loaded yet). Instead the light level is a block-state property. The config is read only when
 * the block is placed, after startup.</p>
 */
public final class MovingLightBlock extends Block {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 15);

    public MovingLightBlock() {
        super(BlockBehaviour.Properties.of()
                .noCollission()
                // LightFollower.findTarget() uses BlockState.canBeReplaced(), which reads this
                // cached flag - not the canBeReplaced(BlockPlaceContext) override below. Without
                // it our own light is not a valid target cell, so the block alternates between
                // two cells every tick and the light visibly flickers even when standing still.
                .replaceable()
                .strength(Block.INDESTRUCTIBLE, 3600000.0F)
                .sound(SoundType.GLASS)
                .lightLevel(state -> state.getValue(LEVEL)));
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 15));
    }

    public static BlockState stateForLevel(int lightLevel) {
        return MovingLightBlockMod.MOVING_LIGHT.get().defaultBlockState()
                .setValue(LEVEL, Mth.clamp(lightLevel, 0, 15));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    // Empty shapes mean there is no hitbox to target, collide with or highlight:
    // the block cannot be selected or broken, but its light value still works normally.
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return 0.0F;
    }

    /** Allow building over the hidden light source instead of leaving an unbreakable invisible block. */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return true;
    }
}
