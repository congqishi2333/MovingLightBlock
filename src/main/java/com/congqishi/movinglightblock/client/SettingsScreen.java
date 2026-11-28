package com.congqishi.movinglightblock.client;

import com.congqishi.movinglightblock.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Direct, grouped edit screen for the mod's common config.
 *
 * <p>Opening it lands on the actual toggles and number fields; there is no intermediate
 * "select a config file" screen. Options are split into titled sections, and every control
 * has a short description plus a hover tooltip.</p>
 */
public class SettingsScreen extends Screen {
    private static final int CONTENT_WIDTH = 430;
    private static final int CONTROL_OFFSET = 252;
    private static final int CONTROL_WIDTH = 168;
    private static final int CONTROL_HEIGHT = 20;
    private static final int DESCRIPTION_WIDTH = 242;
    private static final int HEADING_HEIGHT = 22;
    private static final int ROW_HEIGHT = 42;
    private static final int TOP = 34;
    private static final int BOTTOM = 36;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Object> working = new HashMap<>();
    private final Map<String, Object> originals = new HashMap<>();
    private final Map<AbstractWidget, List<Component>> tooltips = new HashMap<>();
    private int scroll;
    private int maxScroll;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("gui.movinglightblock.settings_title"));
        this.parent = parent;

        remember("enabled", Config.ENABLED.get());
        remember("animation", Config.ANIMATION.get());
        remember("passThroughWalls", Config.PASS_THROUGH_WALLS.get());
        remember("retractKey", Config.RETRACT_KEY.get());
        remember("initialLightLevel", Config.LIGHT_LEVEL.get());
        remember("yOffset", Config.Y_OFFSET.get());
        remember("followDistance", Config.FOLLOW_DISTANCE.get());
        remember("stopDistance", Config.STOP_DISTANCE.get());
        remember("chaseSpeed", Config.CHASE_SPEED.get());
        remember("teleportDistance", Config.TELEPORT_DISTANCE.get());

        heading("gui.movinglightblock.settings.section.basic");
        bool("enabled");
        bool("animation");
        bool("passThroughWalls");

        heading("gui.movinglightblock.settings.section.control");
        text("retractKey");
        integer("initialLightLevel");

        heading("gui.movinglightblock.settings.section.follow");
        integer("yOffset");
        decimal("followDistance");
        decimal("stopDistance");
        decimal("chaseSpeed");
        decimal("teleportDistance");
    }

    private void remember(String key, Object value) {
        this.working.put(key, value);
        this.originals.put(key, value);
    }

    private void heading(String key) {
        this.rows.add(new Row(Kind.HEADING, key, Component.translatable(key), Component.empty()));
    }

    private void bool(String key) {
        add(Kind.BOOLEAN, key);
    }

    private void integer(String key) {
        add(Kind.INTEGER, key);
    }

    private void decimal(String key) {
        add(Kind.DECIMAL, key);
    }

    private void text(String key) {
        add(Kind.TEXT, key);
    }

    private void add(Kind kind, String key) {
        String translation = "movinglightblock.configuration." + key;
        this.rows.add(new Row(kind, key,
                Component.translatable(translation),
                Component.translatable(translation + ".tooltip")));
    }

    @Override
    protected void init() {
        super.init();
        this.tooltips.clear();

        int contentHeight = 6;
        for (Row row : rows) {
            contentHeight += row.kind == Kind.HEADING ? HEADING_HEIGHT : ROW_HEIGHT;
        }
        this.maxScroll = Math.max(0, contentHeight - (height - TOP - BOTTOM));
        this.scroll = Math.max(0, Math.min(this.maxScroll, this.scroll));

        int left = (width - CONTENT_WIDTH) / 2;
        int y = TOP - scroll;
        for (Row row : rows) {
            row.baseY = y;
            y += row.kind == Kind.HEADING ? HEADING_HEIGHT : ROW_HEIGHT;
            if (row.kind == Kind.HEADING) {
                continue;
            }
            AbstractWidget widget;
            if (row.kind == Kind.BOOLEAN) {
                widget = Button.builder(toggleLabel(row.key), button -> {
                    boolean next = !Boolean.TRUE.equals(working.get(row.key));
                    working.put(row.key, next);
                    button.setMessage(toggleLabel(row.key));
                }).pos(left + CONTROL_OFFSET, row.baseY + 4).size(CONTROL_WIDTH, CONTROL_HEIGHT).build();
            } else {
                EditBox box = new EditBox(this.font, left + CONTROL_OFFSET, row.baseY + 4,
                        CONTROL_WIDTH, CONTROL_HEIGHT, row.label);
                box.setValue(String.valueOf(working.get(row.key)));
                box.setMaxLength(48);
                widget = box;
            }
            row.widget = widget;
            addRenderableWidget(widget);
            tip(widget, row.label, row.description);
        }

        int footerY = height - 26;
        Button save = Button.builder(Component.translatable("gui.movinglightblock.settings_save"),
                button -> save()).pos(left, footerY).size(120, 20).build();
        addRenderableWidget(save);
        tip(save, Component.translatable("gui.movinglightblock.tooltip.settings_save.desc"));

        Button done = Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .pos(left + CONTENT_WIDTH - 100, footerY).size(100, 20).build();
        addRenderableWidget(done);
        tip(done, Component.translatable("gui.movinglightblock.tooltip.done"));
    }

    private Component toggleLabel(String key) {
        return Boolean.TRUE.equals(working.get(key))
                ? Component.translatable("gui.movinglightblock.settings_on").withStyle(ChatFormatting.GREEN)
                : Component.translatable("gui.movinglightblock.settings_off").withStyle(ChatFormatting.RED);
    }

    private void tip(AbstractWidget widget, Component... lines) {
        this.tooltips.put(widget, List.of(lines));
    }

    private void showTooltip(List<Component> lines) {
        this.setTooltipForNextRenderPass(lines.stream().map(Component::getVisualOrderText).toList());
    }

    private void save() {
        boolean invalid = false;
        for (Row row : rows) {
            if (row.widget instanceof EditBox box) {
                String text = box.getValue().trim();
                try {
                    switch (row.kind) {
                        case INTEGER -> working.put(row.key, clampInt(text, row.key));
                        case DECIMAL -> working.put(row.key, clampDouble(text, row.key));
                        case TEXT -> {
                            if (!text.isEmpty()) {
                                working.put(row.key, text);
                            }
                        }
                        default -> {
                        }
                    }
                } catch (NumberFormatException ignored) {
                    working.put(row.key, originals.get(row.key));
                    invalid = true;
                }
            }
        }

        Config.ENABLED.set((Boolean) working.get("enabled"));
        Config.ANIMATION.set((Boolean) working.get("animation"));
        Config.PASS_THROUGH_WALLS.set((Boolean) working.get("passThroughWalls"));
        Config.RETRACT_KEY.set((String) working.get("retractKey"));
        Config.LIGHT_LEVEL.set((Integer) working.get("initialLightLevel"));
        Config.Y_OFFSET.set((Integer) working.get("yOffset"));
        Config.FOLLOW_DISTANCE.set((Double) working.get("followDistance"));
        Config.STOP_DISTANCE.set((Double) working.get("stopDistance"));
        Config.CHASE_SPEED.set((Double) working.get("chaseSpeed"));
        Config.TELEPORT_DISTANCE.set((Double) working.get("teleportDistance"));
        Config.SPEC.save();

        message(invalid ? "message.movinglightblock.settings_invalid"
                : "message.movinglightblock.settings_saved");
        onClose();
    }

    private static int clampInt(String text, String key) {
        int value = Integer.parseInt(text);
        return switch (key) {
            case "initialLightLevel" -> Math.max(0, Math.min(15, value));
            case "yOffset" -> Math.max(-2, Math.min(3, value));
            default -> value;
        };
    }

    private static double clampDouble(String text, String key) {
        double value = Double.parseDouble(text);
        return switch (key) {
            case "followDistance" -> Math.max(1.0D, Math.min(16.0D, value));
            case "stopDistance" -> Math.max(0.5D, Math.min(16.0D, value));
            case "chaseSpeed" -> Math.max(0.1D, Math.min(20.0D, value));
            case "teleportDistance" -> Math.max(4.0D, Math.min(128.0D, value));
            default -> value;
        };
    }

    private static void message(String key) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.translatable(key), true);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll <= 0 || verticalAmount == 0.0D) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int previous = scroll;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(verticalAmount * 24.0D)));
        if (scroll == previous) {
            return true;
        }
        int delta = previous - scroll;
        for (Row row : rows) {
            row.baseY += delta;
            if (row.widget != null) {
                row.widget.setY(row.widget.getY() + delta);
            }
        }
        return true;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        int left = (width - CONTENT_WIDTH) / 2;
        for (Row row : rows) {
            if (row.baseY < TOP - HEADING_HEIGHT || row.baseY > height - BOTTOM) {
                continue;
            }
            if (row.kind == Kind.HEADING) {
                guiGraphics.drawString(this.font, row.label, left, row.baseY + 3, 0xFFD8A24A, false);
                continue;
            }
            guiGraphics.drawString(this.font, row.label, left, row.baseY + 2, 0xFFFFFF, false);
            List<FormattedCharSequence> lines = this.font.split(row.description, DESCRIPTION_WIDTH);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                guiGraphics.drawString(this.font, lines.get(i), left, row.baseY + 14 + i * 10,
                        0xA0A0A8, false);
            }
        }

        if (maxScroll > 0) {
            int barX = left + CONTENT_WIDTH - 4;
            int barHeight = height - TOP - BOTTOM;
            int thumb = Math.max(18, barHeight * barHeight / (maxScroll + barHeight));
            int thumbY = TOP + (barHeight - thumb) * scroll / maxScroll;
            guiGraphics.fill(barX, TOP, barX + 2, TOP + barHeight, 0x33000000);
            guiGraphics.fill(barX, thumbY, barX + 2, thumbY + thumb, 0xFFA0A0A8);
        }

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

    private enum Kind {
        HEADING, BOOLEAN, INTEGER, DECIMAL, TEXT
    }

    private static final class Row {
        final Kind kind;
        final String key;
        final Component label;
        final Component description;
        int baseY;
        AbstractWidget widget;

        Row(Kind kind, String key, Component label, Component description) {
            this.kind = kind;
            this.key = key;
            this.label = label;
            this.description = description;
        }
    }
}
