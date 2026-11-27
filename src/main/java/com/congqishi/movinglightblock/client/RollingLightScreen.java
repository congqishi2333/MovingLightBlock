package com.congqishi.movinglightblock.client;

import com.congqishi.movinglightblock.MovingLightBlockMod;
import com.congqishi.movinglightblock.entity.RollingLightEntity;
import com.congqishi.movinglightblock.menu.RollingLightMenu;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.joml.Quaternionf;

/** GUI backed by the three mode-specific 176x166 textures supplied for this mod. */
public class RollingLightScreen extends AbstractContainerScreen<RollingLightMenu> {
    private static final ResourceLocation TEXTURE_CRAFTING = gui("rolling_light_crafting.png");
    private static final ResourceLocation TEXTURE_SMELTING = gui("rolling_light_smelting.png");
    private static final ResourceLocation TEXTURE_STORAGE = gui("rolling_light_storage.png");
    private static final ResourceLocation TEXTURE_EMPTY = gui("rolling_light_ui.png");

    private static ResourceLocation gui(String name) {
        return ResourceLocation.fromNamespaceAndPath(MovingLightBlockMod.MODID, "textures/gui/" + name);
    }

    public RollingLightScreen(RollingLightMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        // The mode name is already painted into the artwork below the cube preview.
        this.inventoryLabelY = -1000;
    }

    private ResourceLocation backgroundTexture() {
        return switch (menu.getMode()) {
            case RollingLightMenu.MODE_CRAFTING -> TEXTURE_CRAFTING;
            case RollingLightMenu.MODE_SMELTING -> TEXTURE_SMELTING;
            case RollingLightMenu.MODE_STORAGE -> TEXTURE_STORAGE;
            default -> TEXTURE_EMPTY;
        };
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(this.leftPos, this.topPos, 0.0F);

        guiGraphics.blit(backgroundTexture(), 0, 0, 0, 0, this.imageWidth, this.imageHeight,
                this.imageWidth, this.imageHeight);

        menu.updateModuleSlotPosition();
        drawProgress(guiGraphics);
        renderModel(guiGraphics, partialTick);

        pose.popPose();
    }

    private void drawProgress(GuiGraphics guiGraphics) {
        if (menu.getMode() != RollingLightMenu.MODE_SMELTING) {
            return;
        }
        int progress = menu.getSmeltProgress() * 22 / 200;
        if (progress > 0) {
            guiGraphics.fill(94, 48, 94 + progress, 52, 0xFFD8A24A);
        }
        int burn = menu.getBurnProgress() * 16 / 200;
        if (burn > 0) {
            guiGraphics.fill(70, 56 - burn, 85, 56, 0xFFE06A3A);
        }
    }

    /** Draws the tracked cube itself inside the black preview square of the artwork. */
    private void renderModel(GuiGraphics guiGraphics, float partialTick) {
        RollingLightEntity entity = menu.getEntity();
        if (entity == null || entity.isRemoved()) {
            return;
        }

        boolean crafting = menu.getMode() == RollingLightMenu.MODE_CRAFTING;
        float centerX = crafting ? 32.0F : 34.0F;
        float centerY = crafting ? 32.0F : 33.0F;

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 50.0F);
        pose.scale(40.0F, 40.0F, -40.0F);
        pose.translate(0.0F, -0.5F, 0.0F);
        pose.mulPose(new Quaternionf().rotationY(0.7F));
        pose.mulPose(new Quaternionf().rotationX(-0.35F));

        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0D, 0.0D, 0.0D,
                0.0F, partialTick, pose, guiGraphics.bufferSource(), 0xF000F0));
        guiGraphics.flush();
        dispatcher.setRenderShadow(true);
        Lighting.setupFor3DItems();
        pose.popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
    }
}
