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

/** Placeholder UI; replace {@code textures/gui/rolling_light_ui.png} with your own artwork. */
public class RollingLightScreen extends AbstractContainerScreen<RollingLightMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MovingLightBlockMod.MODID, "textures/gui/rolling_light_ui.png");

    public RollingLightScreen(RollingLightMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 420;
        this.imageHeight = 240;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 204;
        this.inventoryLabelY = 137;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(this.leftPos, this.topPos, 0.0F);

        guiGraphics.blit(TEXTURE, 0, 0, 0, 0, this.imageWidth, this.imageHeight,
                this.imageWidth, this.imageHeight);

        drawModuleLabel(guiGraphics);
        drawDynamicArea(guiGraphics);
        renderModel(guiGraphics, partialTick);

        pose.popPose();
    }

    private void drawModuleLabel(GuiGraphics guiGraphics) {
        guiGraphics.drawString(this.font, Component.translatable("gui.movinglightblock.module"),
                158, 24, 0xB0B0B8, false);
    }

    private void drawDynamicArea(GuiGraphics guiGraphics) {
        switch (menu.getMode()) {
            case RollingLightMenu.MODE_CRAFTING ->
                    guiGraphics.drawString(this.font, Component.translatable("gui.movinglightblock.crafting"),
                            196, 16, 0xE0E0E8, false);
            case RollingLightMenu.MODE_SMELTING -> {
                guiGraphics.drawString(this.font, Component.translatable("gui.movinglightblock.smelting"),
                        196, 16, 0xE0E0E8, false);
                int progress = menu.getSmeltProgress() * 30 / 200;
                guiGraphics.fill(238, 54, 238 + progress, 64, 0xFFD8A24A);
                int burn = menu.getBurnProgress() * 22 / 200;
                if (burn > 0) {
                    guiGraphics.fill(210, 74, 210 + 16, 74 + burn, 0xFFE06A3A);
                }
            }
            case RollingLightMenu.MODE_STORAGE ->
                    guiGraphics.drawString(this.font, Component.translatable("gui.movinglightblock.storage"),
                            196, 16, 0xE0E0E8, false);
            default -> guiGraphics.drawString(this.font, Component.translatable("gui.movinglightblock.empty"),
                    196, 60, 0x909098, false);
        }
    }

    /** Draws the tracked cube itself, like the player model in the vanilla inventory. */
    private void renderModel(GuiGraphics guiGraphics, float partialTick) {
        RollingLightEntity entity = menu.getEntity();
        if (entity == null || entity.isRemoved()) {
            return;
        }

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(79.0F, 92.0F, 50.0F);
        pose.scale(52.0F, 52.0F, -52.0F);
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
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, 0xA0A0A8, false);
    }
}
