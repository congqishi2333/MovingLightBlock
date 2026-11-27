package com.congqishi.movinglightblock.client;

import com.congqishi.movinglightblock.Config;
import com.congqishi.movinglightblock.ModEntities;
import com.congqishi.movinglightblock.ModMenus;
import com.congqishi.movinglightblock.MovingLightBlockMod;
import com.congqishi.movinglightblock.entity.RollingLightEntity;
import com.congqishi.movinglightblock.network.RetractPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = MovingLightBlockMod.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ROLLING_LIGHT.get(), RollingLightRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ROLLING_LIGHT.get(), RollingLightScreen::new);
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != InputConstants.PRESS || Minecraft.getInstance().screen != null) {
            return;
        }
        RetractBinding binding = RetractBinding.parse(Config.RETRACT_KEY.get());
        if (!binding.matchesMouse(event.getButton(), event.getModifiers()) || !tryRetract()) {
            return;
        }
        event.setCanceled(true);
        Minecraft.getInstance().player.swing(InteractionHand.MAIN_HAND);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != InputConstants.PRESS || Minecraft.getInstance().screen != null) {
            return;
        }
        RetractBinding binding = RetractBinding.parse(Config.RETRACT_KEY.get());
        if (binding.matchesKey(event.getKey(), event.getModifiers())) {
            tryRetract();
        }
    }

    private static boolean tryRetract() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        HitResult hit = minecraft.hitResult;
        if (!(hit instanceof EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof RollingLightEntity light)
                || minecraft.player.distanceToSqr(light) > 64.0D) {
            return false;
        }
        PacketDistributor.sendToServer(new RetractPayload(light.getId()));
        return true;
    }
}
