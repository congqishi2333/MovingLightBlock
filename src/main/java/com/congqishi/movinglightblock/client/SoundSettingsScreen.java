package com.congqishi.movinglightblock.client;

import com.congqishi.movinglightblock.client.sound.SoundController;
import com.congqishi.movinglightblock.client.sound.SoundSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Edits the four JSON-backed sound phases. Opening this screen creates the sound folder. */
public class SoundSettingsScreen extends Screen {
    private static final int ROW_HEIGHT = 34;
    private static final int FIELD_WIDTH = 118;
    private static final int NUMBER_WIDTH = 44;
    private static final int BUTTON_HEIGHT = 20;
    private static final int START_Y = 64;

    private final Screen parent;
    private final SoundSettings working;
    private final Map<SoundSettings.Phase, EditBox> files = new EnumMap<>(SoundSettings.Phase.class);
    private final Map<SoundSettings.Phase, EditBox> volumes = new EnumMap<>(SoundSettings.Phase.class);
    private final Map<SoundSettings.Phase, EditBox> speeds = new EnumMap<>(SoundSettings.Phase.class);
    private final Map<AbstractWidget, List<Component>> tooltips = new HashMap<>();

    public SoundSettingsScreen(Screen parent) {
        super(Component.translatable("gui.movinglightblock.sound_title"));
        this.parent = parent;
        SoundSettings.ensureCreated();
        SoundSettings current = SoundSettings.get();
        this.working = new SoundSettings();
        this.working.summon = current.summon.copy();
        this.working.land = current.land.copy();
        this.working.move = current.move.copy();
        this.working.idle = current.idle.copy();
    }

    @Override
    protected void init() {
        super.init();
        this.tooltips.clear();
        int center = this.width / 2;
        int left = center - 125;

        for (int i = 0; i < SoundSettings.Phase.values().length; i++) {
            SoundSettings.Phase phase = SoundSettings.Phase.values()[i];
            int y = START_Y + i * ROW_HEIGHT;
            SoundSettings.Entry entry = working.entry(phase);

            EditBox file = new EditBox(this.font, left + 62, y, FIELD_WIDTH, BUTTON_HEIGHT,
                    Component.translatable("gui.movinglightblock.sound_file"));
            file.setValue(entry.file);
            file.setMaxLength(96);
            addRenderableWidget(file);
            files.put(phase, file);
            tip(file, "gui.movinglightblock.tooltip.sound_file.desc");

            EditBox volume = new EditBox(this.font, left + 186, y, NUMBER_WIDTH, BUTTON_HEIGHT,
                    Component.translatable("gui.movinglightblock.sound_volume"));
            volume.setValue(Float.toString(entry.volume));
            volume.setMaxLength(8);
            addRenderableWidget(volume);
            volumes.put(phase, volume);
            tip(volume, "gui.movinglightblock.tooltip.sound_volume.desc");

            EditBox speed = new EditBox(this.font, left + 236, y, NUMBER_WIDTH, BUTTON_HEIGHT,
                    Component.translatable("gui.movinglightblock.sound_speed"));
            speed.setValue(Float.toString(entry.speed));
            speed.setMaxLength(8);
            addRenderableWidget(speed);
            speeds.put(phase, speed);
            tip(speed, "gui.movinglightblock.tooltip.sound_speed.desc");
        }

        int footerY = Math.min(this.height - 26,
                START_Y + SoundSettings.Phase.values().length * ROW_HEIGHT + 20);
        int small = 76;
        Button save = Button.builder(
                        Component.translatable("gui.movinglightblock.sound_save"), button -> save())
                .pos(left, footerY).size(small, BUTTON_HEIGHT).build();
        this.addRenderableWidget(save);
        tip(save, "gui.movinglightblock.tooltip.sound_save.desc");

        Button list = Button.builder(
                        Component.translatable("gui.movinglightblock.sound_list"), button -> listFiles())
                .pos(left + small + 6, footerY).size(small, BUTTON_HEIGHT).build();
        this.addRenderableWidget(list);
        tip(list, "gui.movinglightblock.tooltip.sound_list.desc");

        Button done = Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .pos(left + 2 * (small + 6), footerY).size(small, BUTTON_HEIGHT).build();
        this.addRenderableWidget(done);
        tip(done, "gui.movinglightblock.tooltip.done");
    }

    private void tip(AbstractWidget widget, String... keys) {
        List<Component> lines = new java.util.ArrayList<>();
        for (String key : keys) {
            lines.add(Component.translatable(key));
        }
        this.tooltips.put(widget, lines);
    }

    private void showTooltip(List<Component> lines) {
        this.setTooltipForNextRenderPass(lines.stream().map(Component::getVisualOrderText).toList());
    }

    private void save() {
        for (SoundSettings.Phase phase : SoundSettings.Phase.values()) {
            SoundSettings.Entry entry = working.entry(phase);
            entry.file = files.get(phase).getValue().trim();
            entry.volume = parseNumber(volumes.get(phase).getValue(), entry.volume,
                    SoundSettings.MIN_VOLUME, SoundSettings.MAX_VOLUME);
            entry.speed = parseNumber(speeds.get(phase).getValue(), entry.speed,
                    SoundSettings.MIN_SPEED, SoundSettings.MAX_SPEED);
        }
        SoundSettings.save(working);
        SoundController.onSettingsChanged();
        message("message.movinglightblock.sound_saved");
    }

    private void listFiles() {
        var names = SoundSettings.listFiles();
        if (names.isEmpty()) {
            message("message.movinglightblock.sound_no_files");
            return;
        }
        for (String name : names) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal(name));
            }
        }
    }

    private static float parseNumber(String text, float fallback, float min, float max) {
        try {
            float value = Float.parseFloat(text.trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void message(String key) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.translatable(key), true);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        int center = this.width / 2;
        int left = center - 125;

        guiGraphics.drawString(this.font,
                Component.translatable("gui.movinglightblock.sound_group.phases"),
                left, 34, 0xFFD8A24A, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.movinglightblock.sound_file"), left + 62, 51, 0xA0A0A8, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.movinglightblock.sound_volume"), left + 186, 51, 0xA0A0A8, false);
        guiGraphics.drawString(this.font,
                Component.translatable("gui.movinglightblock.sound_speed"), left + 236, 51, 0xA0A0A8, false);

        for (int i = 0; i < SoundSettings.Phase.values().length; i++) {
            SoundSettings.Phase phase = SoundSettings.Phase.values()[i];
            int y = START_Y + i * ROW_HEIGHT;
            String key = "gui.movinglightblock.sound_phase." + phase.key();
            guiGraphics.drawString(this.font, Component.translatable(key),
                    left, y + 2, 0xFFFFFF, false);
            guiGraphics.drawString(this.font, Component.translatable(key + ".desc"),
                    left, y + 15, 0xA0A0A8, false);
        }
        guiGraphics.drawString(this.font,
                Component.translatable("gui.movinglightblock.sound_hint"),
                left, Math.min(this.height - 42, START_Y
                        + SoundSettings.Phase.values().length * ROW_HEIGHT + 6), 0xA0A0A8, false);

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
