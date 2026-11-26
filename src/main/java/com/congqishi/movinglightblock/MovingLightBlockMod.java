package com.congqishi.movinglightblock;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(MovingLightBlockMod.MODID)
public class MovingLightBlockMod {
    public static final String MODID = "movinglightblock";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);

    /**
     * The block placed at the player's position. It is non-colliding, unbreakable and
     * emits a configurable light level (vanilla block light, not a shader effect).
     *
     * <p>The level is a block-state property, not a config lookup at registration time:
     * configs are loaded after blocks are registered.</p>
     */
    public static final DeferredBlock<MovingLightBlock> MOVING_LIGHT = BLOCKS.register("moving_light",
            MovingLightBlock::new);

    /**
     * Render-only block state used by the rolling visual entity. It is never placed in the world.
     */
    public static final DeferredBlock<Block> VISUAL_LIGHT = BLOCKS.register("moving_light_visual",
            () -> new Block(BlockBehaviour.Properties.of()
                    .noCollission()
                    .strength(Block.INDESTRUCTIBLE, 3600000.0F)));

    public MovingLightBlockMod(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(MOVING_LIGHT.get());
            }
        });

        modEventBus.addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == Config.SPEC) {
                LightFollower.clearAll();
            }
        });

        NeoForge.EVENT_BUS.addListener(LightFollower::onServerTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) ->
                LightFollower.clear(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) ->
                LightFollower.clear(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> LightFollower.clearAll());
    }
}
