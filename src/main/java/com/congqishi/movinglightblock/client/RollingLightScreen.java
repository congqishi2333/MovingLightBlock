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
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import org.joml.Quaternionf;

/** GUI backed by the supplied mode textures; storage uses a larger double-chest layout. */
public class RollingLightScreen extends AbstractContainerScreen<RollingLightMenu> {
    private static final ResourceLocation TEXTURE_CRAFTING = gui("rolling_light_crafting.png");
    private static final ResourceLocation TEXTURE_SMELTING = gui("rolling_light_smelting.png");
    private static final ResourceLocation TEXTURE_STORAGE = gui("rolling_light_storage.png");
    private static final ResourceLocation TEXTURE_EMPTY = gui("rolling_light_ui.png");
    private static final ResourceLocation TEXTURE_CHEST =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    private static ResourceLocation gui(String name) {
        return ResourceLocation.fromNamespaceAndPath(MovingLightBlockMod.MODID, "textures/gui/" + name);
    }

    /** Settings / change-model / sound icon positions painted into each mode's texture. */
    private static final int[][] BUTTON_POS = {
            {9, 64, 26, 65, 44, 65}, // no module
            {8, 64, 26, 63, 44, 63}, // crafting
            {9, 66, 26, 65, 44, 65}, // smelting
            {228, 18, 228, 40, 228, 62} // large storage side panel
    };
    private static final int BUTTON_SIZE = 16;

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

    /** Resizes and recenters the screen when the module slot changes mode. */
    private void applySize() {
        boolean large = menu.getMode() == RollingLightMenu.MODE_STORAGE;
        int width = large ? 256 : 176;
        int height = large ? 222 : 166;
        if (this.imageWidth != width || this.imageHeight != height) {
            this.imageWidth = width;
            this.imageHeight = height;
            this.leftPos = (this.width - width) / 2;
            this.topPos = (this.height - height) / 2;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        applySize();
        if (button == 0) {
            int localX = (int) (mouseX - this.leftPos);
            int localY = (int) (mouseY - this.topPos);
            int mode = menu.getMode();
            if (mode < 0 || mode >= BUTTON_POS.length) {
                mode = RollingLightMenu.MODE_NONE;
            }
            int[] pos = BUTTON_POS[mode];
            if (hit(localX, localY, pos[0], pos[1])) {
                Minecraft.getInstance().setScreen(new ConfigurationScreen(
                        ModList.get().getModContainerById(MovingLightBlockMod.MODID).orElseThrow(),
                        this));
                return true;
            }
            if (hit(localX, localY, pos[2], pos[3])) {
                Minecraft.getInstance().setScreen(new ModelSelectScreen(this));
                return true;
            }
            if (hit(localX, localY, pos[4], pos[5])) {
                Minecraft.getInstance().setScreen(new SoundSettingsScreen(this));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean hit(int x, int y, int buttonX, int buttonY) {
        return x >= buttonX && x < buttonX + BUTTON_SIZE
                && y >= buttonY && y < buttonY + BUTTON_SIZE;
    }

    private void drawButtonHover(GuiGraphics guiGraphics, int x, int y) {
        int mode = menu.getMode();
        if (mode < 0 || mode >= BUTTON_POS.length) {
            mode = RollingLightMenu.MODE_NONE;
        }
        int[] pos = BUTTON_POS[mode];
        for (int i = 0; i < pos.length; i += 2) {
            if (hit(x, y, pos[i], pos[i + 1])) {
                guiGraphics.fill(pos[i], pos[i + 1], pos[i] + BUTTON_SIZE,
                        pos[i + 1] + BUTTON_SIZE, 0x22FFFFFF);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        applySize();
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(this.leftPos, this.topPos, 0.0F);

        if (menu.getMode() == RollingLightMenu.MODE_STORAGE) {
            // Vanilla 54-slot chest background on the left, with the cube controls on the right.
            guiGraphics.blit(TEXTURE_CHEST, 0, 0, 0, 0, 176, 222, 256, 256);
            drawStoragePanel(guiGraphics);
        } else {
            guiGraphics.blit(backgroundTexture(), 0, 0, 0, 0, this.imageWidth, this.imageHeight,
                    this.imageWidth, this.imageHeight);
        }

        drawButtonHover(guiGraphics, mouseX - this.leftPos, mouseY - this.topPos);
        menu.updateSpecialSlots();
        drawProgress(guiGraphics);
        renderModel(guiGraphics, partialTick);

        pose.popPose();
    }

    /** Draws the 80-pixel side panel used only by the enlarged storage GUI. */
    private void drawStoragePanel(GuiGraphics guiGraphics) {
        guiGraphics.fill(176, 0, 256, 222, 0xFFC6C6C6);
        guiGraphics.fill(176, 0, 177, 222, 0xFF373737);
        guiGraphics.fill(255, 0, 256, 222, 0xFF373737);
        guiGraphics.fill(176, 0, 256, 1, 0xFF373737);
        guiGraphics.fill(176, 221, 256, 222, 0xFF373737);
        guiGraphics.fill(178, 2, 254, 3, 0xFFFFFFFF);
        guiGraphics.fill(178, 219, 254, 220, 0xFFFFFFFF);

        // Cube preview well.
        guiGraphics.fill(183, 55, 241, 113, 0xFF373737);
        guiGraphics.fill(184, 56, 240, 112, 0xFF000000);

        // Reuse the module, torch and button artwork from the supplied storage texture.
        guiGraphics.blit(TEXTURE_STORAGE, 136, 63, 184, 18, 18, 18, 256, 256);
        guiGraphics.blit(TEXTURE_STORAGE, 154, 63, 204, 18, 18, 18, 256, 256);
        guiGraphics.blit(TEXTURE_STORAGE, 9, 64, 228, 18, 16, 16, 256, 256);
        guiGraphics.blit(TEXTURE_STORAGE, 25, 63, 228, 40, 16, 16, 256, 256);
        guiGraphics.blit(TEXTURE_STORAGE, 43, 64, 228, 62, 16, 16, 256, 256);
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

        int mode = menu.getMode();
        boolean crafting = mode == RollingLightMenu.MODE_CRAFTING;
        boolean largeStorage = mode == RollingLightMenu.MODE_STORAGE;
        float centerX = largeStorage ? 212.0F : (crafting ? 32.0F : 34.0F);
        float centerY = largeStorage ? 84.0F : (crafting ? 32.0F : 33.0F);
        float scale = largeStorage ? 44.0F : 40.0F;

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 50.0F);
        pose.scale(scale, scale, -scale);
        pose.translate(0.0F, -0.5F, 0.0F);
        pose.mulPose(new Quaternionf().rotationY(0.7F));
        pose.mulPose(new Quaternionf().rotationX(-0.35F));

        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        // Preview follows the installed torches; keep a little ambient light so the model
        // stays visible at zero instead of turning into a black square.
        int previewLight = Math.max(4, menu.getLightLevel()) << 4;
        RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0D, 0.0D, 0.0D,
                0.0F, partialTick, pose, guiGraphics.bufferSource(), previewLight));
        guiGraphics.flush();
        dispatcher.setRenderShadow(true);
        Lighting.setupFor3DItems();
        pose.popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (menu.isTorchSlot(this.hoveredSlot)) {
            guiGraphics.renderTooltip(this.font, Component.translatable(
                    "gui.movinglightblock.torch", menu.getLightLevel(), 15), mouseX, mouseY);
            return;
        }
        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
