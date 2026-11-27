package com.congqishi.movinglightblock;

import com.congqishi.movinglightblock.entity.RollingLightEntity;
import com.congqishi.movinglightblock.menu.RollingLightMenu;
import com.congqishi.movinglightblock.network.RetractPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
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
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(MovingLightBlockMod.MODID)
public class MovingLightBlockMod {
    public static final String MODID = "movinglightblock";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

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

    /**
     * Right-click item that summons the entity. It is not a block item: the real light block
     * is hidden and placed directly by {@link LightFollower}.
     */
    public static final DeferredItem<FollowLightItem> MOVING_LIGHT_ITEM =
            ITEMS.register("moving_light", FollowLightItem::new);

    public MovingLightBlockMod(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        modEventBus.addListener((RegisterPayloadHandlersEvent event) -> {
            event.registrar("1").playToServer(RetractPayload.TYPE, RetractPayload.CODEC,
                    MovingLightBlockMod::handleRetract);
        });

        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(MOVING_LIGHT_ITEM.get());
            }
        });

        modEventBus.addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == Config.SPEC) {
                LightFollower.clearBlocks();
            }
        });

        NeoForge.EVENT_BUS.addListener(LightFollower::onServerTick);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                if (player.containerMenu instanceof RollingLightMenu menu) {
                    menu.serverTick();
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) ->
                LightFollower.discardOwned(event.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) ->
                LightFollower.relocate((ServerPlayer) event.getEntity()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> LightFollower.clearAll());
    }

    private static void handleRetract(RetractPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Entity entity = player.level().getEntity(payload.entityId());
        if (!(entity instanceof RollingLightEntity light)) {
            return;
        }
        if (light.getOwner() != null && !light.getOwner().equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.movinglightblock.not_owner"), true);
            return;
        }
        if (player.distanceToSqr(light) > 64.0D) {
            return;
        }

        LightFollower.discardOwned(player.getUUID());
        ItemStack item = new ItemStack(MOVING_LIGHT_ITEM.get());
        if (!player.getInventory().add(item)) {
            player.drop(item, false);
        }
        player.displayClientMessage(Component.translatable("message.movinglightblock.retracted"), true);
    }
}
