package com.congqishi.movinglightblock;

import com.congqishi.movinglightblock.entity.RollingLightEntity;
import com.congqishi.movinglightblock.menu.RollingLightMenu;
import com.congqishi.movinglightblock.network.RetractPayload;
import net.minecraft.nbt.CompoundTag;
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
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
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
        ModDataComponents.register(modEventBus);

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
        // Logout no longer discards the cube: it stays in the world with its light and
        // simply resumes following when this player comes back online.
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) ->
                LightFollower.relocate((ServerPlayer) event.getEntity()));
        // Restore hidden light blocks before Minecraft saves/unloads worlds. Doing this on
        // ServerStoppedEvent was after saving, and getBlockState() there forced chunk loads
        // that could never complete, leaving the game stuck on the saving-world screen.
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> LightFollower.clearAll());
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

        // Keep the GUI contents inside the picked-up cube instead of dumping them out.
        RollingLightMenu openMenu = player.containerMenu instanceof RollingLightMenu menu
                && menu.getEntity() == light ? menu : null;
        if (openMenu != null) {
            openMenu.saveToEntity();
        }
        CompoundTag contents = light.getStoredMenu().copy();
        contents.putInt("LightLevel", light.getLightLevel());
        ItemStack item = new ItemStack(MOVING_LIGHT_ITEM.get());
        if (!contents.isEmpty()) {
            item.set(ModDataComponents.STORED_ITEMS.get(), contents);
        }
        if (openMenu != null) {
            // The contents now live in the item; do not also return them when the GUI closes.
            openMenu.clearContents();
        }
        LightFollower.discardOwned(player.getUUID());
        if (!player.getInventory().add(item)) {
            player.drop(item, false);
        }
        player.displayClientMessage(Component.translatable("message.movinglightblock.retracted"), true);
    }
}
