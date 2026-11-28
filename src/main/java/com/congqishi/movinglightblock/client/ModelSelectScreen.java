package com.congqishi.movinglightblock.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lets the player pick one of the JSON presets in config/movinglightblock/models/. */
public class ModelSelectScreen extends Screen {
    private static final int PER_PAGE = 8;
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private final List<String> models;
    private final Map<AbstractWidget, List<Component>> tooltips = new HashMap<>();
    private int page;

    public ModelSelectScreen(Screen parent) {
        super(Component.translatable("gui.movinglightblock.model_title"));
        this.parent = parent;
        this.models = ModelManager.list();
        int activeIndex = this.models.indexOf(ModelManager.active());
        if (activeIndex > 0) {
            this.page = activeIndex / PER_PAGE;
        }
    }

    @Override
    protected void init() {
        super.init();
        this.tooltips.clear();
        int from = page * PER_PAGE;
        int count = Math.min(PER_PAGE, models.size() - from);
        int x = (this.width - BUTTON_WIDTH) / 2;
        int y = 42;

        for (int i = 0; i < count; i++) {
            String name = models.get(from + i);
            Component label = ModelManager.DEFAULT.equals(name)
                    ? Component.translatable("gui.movinglightblock.model_default")
                    : Component.literal(name);
            if (name.equals(ModelManager.active())) {
                label = Component.literal("[ ").append(label).append(" ]");
            }
            final String chosen = name;
            Button entry = Button.builder(label, button -> {
                ModelManager.select(chosen);
                Minecraft.getInstance().setScreen(parent);
            }).pos(x, y + i * (BUTTON_HEIGHT + 4)).size(BUTTON_WIDTH, BUTTON_HEIGHT).build();
            this.addRenderableWidget(entry);
            tip(entry,
                    Component.translatable("gui.movinglightblock.tooltip.model_entry", name),
                    Component.translatable("gui.movinglightblock.tooltip.model_entry.desc"));
        }

        int footerY = Math.min(this.height - 30, y + Math.max(count, 1) * (BUTTON_HEIGHT + 4) + 6);
        int pages = Math.max(1, (models.size() + PER_PAGE - 1) / PER_PAGE);
        int smallWidth = 60;
        int gap = 8;
        int total = smallWidth * 3 + gap * 2;
        int startX = (this.width - total) / 2;

        Button prev = Button.builder(Component.literal("<"), button -> {
            if (page > 0) {
                page--;
                rebuildWidgets();
            }
        }).pos(startX, footerY).size(smallWidth, BUTTON_HEIGHT).build();
        this.addRenderableWidget(prev);
        tip(prev, Component.translatable("gui.movinglightblock.tooltip.prev"));

        Button current = Button.builder(Component.literal((page + 1) + "/" + pages), button -> {
        }).pos(startX + smallWidth + gap, footerY).size(smallWidth, BUTTON_HEIGHT).build();
        this.addRenderableWidget(current);
        tip(current, Component.translatable("gui.movinglightblock.tooltip.page"));

        Button next = Button.builder(Component.literal(">"), button -> {
            if (page + 1 < pages) {
                page++;
                rebuildWidgets();
            }
        }).pos(startX + (smallWidth + gap) * 2, footerY).size(smallWidth, BUTTON_HEIGHT).build();
        this.addRenderableWidget(next);
        tip(next, Component.translatable("gui.movinglightblock.tooltip.next"));

        Button done = Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .pos((this.width - 100) / 2, footerY + BUTTON_HEIGHT + 6)
                .size(100, BUTTON_HEIGHT).build();
        this.addRenderableWidget(done);
        tip(done, Component.translatable("gui.movinglightblock.tooltip.done"));
    }

    private void tip(AbstractWidget widget, Component... lines) {
        this.tooltips.put(widget, List.of(lines));
    }

    private void showTooltip(List<Component> lines) {
        this.setTooltipForNextRenderPass(lines.stream().map(Component::getVisualOrderText).toList());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("gui.movinglightblock.model_hint"),
                this.width / 2, 30, 0xA0A0A8);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        for (GuiEventListener listener : this.children()) {
            if (listener instanceof AbstractWidget widget && widget.active && widget.isHovered()) {
                List<Component> lines = this.tooltips.get(widget);
                if (lines != null) {
                    showTooltip(lines);
                }
            }
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
