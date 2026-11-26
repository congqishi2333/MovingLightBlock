package com.congqishi.movinglightblock;

import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

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
}
