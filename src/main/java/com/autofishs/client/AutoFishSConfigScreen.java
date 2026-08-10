package com.autofishs.client;

import com.autofishs.AutoFishSConfig;
import com.autofishs.AutoFishSConfig.AutoHitMode;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Client-only config screen opened from ESC -> Mods -> AutoFishS -> Config.
 * Edits the same values as the config file and persists them immediately.
 * Supports mouse-wheel scrolling when the option list is taller than the screen.
 */
public class AutoFishSConfigScreen extends Screen
{
    private static final int ROW_H = 26;

    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();

    private Button autoHitButton;
    private Button autoReelButton;
    private Button autoCastButton;
    private Button treasureFocusButton;
    private Button clickFrozenButton;
    private Button clickExplosiveButton;
    private Button modeButton;
    private Button foModeButton;
    private Button leewayValueButton;

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int contentBottomY = 0;

    private record Entry(AbstractWidget widget, int baseY) {}

    public AutoFishSConfigScreen(Screen parent)
    {
        super(Component.translatable("autofishs.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        int cx = this.width / 2;
        int y = Math.max(20, this.height / 6);

        autoHitButton = addToggle(cx, y, "autofishs.config.autohit",
                AutoFishSConfig.ENABLE_MINIGAME_AUTO_HIT::get,
                () -> AutoFishSConfig.setAutoHit(!AutoFishSConfig.ENABLE_MINIGAME_AUTO_HIT.get()));
        y += ROW_H;

        autoReelButton = addToggle(cx, y, "autofishs.config.autoreel",
                AutoFishSConfig.ENABLE_AUTO_REEL::get,
                () -> AutoFishSConfig.setAutoReel(!AutoFishSConfig.ENABLE_AUTO_REEL.get()));
        y += ROW_H;

        autoCastButton = addToggle(cx, y, "autofishs.config.autocast",
                AutoFishSConfig.ENABLE_AUTO_CAST::get,
                () -> AutoFishSConfig.setAutoCast(!AutoFishSConfig.ENABLE_AUTO_CAST.get()));
        y += ROW_H;

        treasureFocusButton = addToggle(cx, y, "autofishs.config.treasure",
                AutoFishSConfig.TREASURE_FOCUS::get,
                () -> AutoFishSConfig.setTreasureFocus(!AutoFishSConfig.TREASURE_FOCUS.get()));
        y += ROW_H;

        clickFrozenButton = addToggle(cx, y, "autofishs.config.frozen",
                AutoFishSConfig.CLICK_FROZEN_SPOTS::get,
                () -> AutoFishSConfig.setClickFrozen(!AutoFishSConfig.CLICK_FROZEN_SPOTS.get()));
        y += ROW_H;

        clickExplosiveButton = addToggle(cx, y, "autofishs.config.explosive",
                AutoFishSConfig.CLICK_EXPLOSIVE_SPOTS::get,
                () -> AutoFishSConfig.setClickExplosive(!AutoFishSConfig.CLICK_EXPLOSIVE_SPOTS.get()));
        y += ROW_H;

        // Mode (AUTO / PERFECT) — created directly so it shows the enum, not ON/OFF.
        modeButton = Button.builder(
                Component.translatable("autofishs.config.mode", AutoFishSConfig.MODE.get().name()),
                b -> {
                    AutoHitMode next = AutoFishSConfig.MODE.get() == AutoHitMode.AUTO ? AutoHitMode.PERFECT : AutoHitMode.AUTO;
                    AutoFishSConfig.setMode(next);
                    refresh();
                }
        ).bounds(cx - 155, y, 310, 20).build();
        addEntry(modeButton, y);
        y += ROW_H;

        // Fishing Overhaul mode (AUTO / PERFECT)
        foModeButton = Button.builder(
                Component.translatable("autofishs.config.fo_mode", AutoFishSConfig.FO_MODE.get().name()),
                b -> {
                    AutoHitMode next = AutoFishSConfig.FO_MODE.get() == AutoHitMode.AUTO ? AutoHitMode.PERFECT : AutoHitMode.AUTO;
                    AutoFishSConfig.setFoMode(next);
                    refresh();
                }
        ).bounds(cx - 155, y, 310, 20).build();
        addEntry(foModeButton, y);
        y += ROW_H;

        // Minimum interval: editable text box (seconds)
        EditBox intervalBox = new EditBox(this.font, cx - 100, y, 200, 20, Component.translatable("autofishs.config.interval"));
        intervalBox.setMaxLength(5);
        intervalBox.setFilter(s -> s.matches("[0-9.]*"));
        intervalBox.setValue(String.format("%.1f", AutoFishSConfig.HIT_INTERVAL_SECONDS.get()));
        intervalBox.setResponder(text -> {
            try
            {
                AutoFishSConfig.setHitInterval(Double.parseDouble(text));
            }
            catch (NumberFormatException ignored) { }
        });
        addEntry(intervalBox, y);
        y += ROW_H;

        leewayValueButton = addValueRow(cx, y,
                Component.translatable("autofishs.config.leeway", AutoFishSConfig.PERFECT_LEEWAY.get()),
                () -> AutoFishSConfig.setPerfectLeeway(AutoFishSConfig.PERFECT_LEEWAY.get() - 1),
                () -> AutoFishSConfig.setPerfectLeeway(AutoFishSConfig.PERFECT_LEEWAY.get() + 1));
        y += ROW_H;

        contentBottomY = y;

        int backY = this.height - 30;
        maxScroll = Math.max(0, contentBottomY - (backY - ROW_H));
        scrollOffset = 0;
        layout();

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> this.onClose())
                .bounds(cx - 100, backY, 200, 20).build());

        // Populate every label so values are shown immediately on open.
        refresh();
    }

    private Button addToggle(int cx, int y, String key, BooleanSupplier active, Runnable action)
    {
        Button b = Button.builder(
                label(key, active.getAsBoolean()),
                btn -> {
                    action.run();
                    refresh();
                }
        ).bounds(cx - 155, y, 310, 20).build();
        addEntry(b, y);
        return b;
    }

    private Button addValueRow(int cx, int y, Component initialValue, Runnable minus, Runnable plus)
    {
        Button minusBtn = Button.builder(Component.literal("-"), btn -> {
            minus.run();
            refresh();
        }).bounds(cx - 155, y, 40, 20).build();
        Button valueBtn = Button.builder(initialValue, btn -> {}).bounds(cx - 115, y, 230, 20).build();
        Button plusBtn = Button.builder(Component.literal("+"), btn -> {
            plus.run();
            refresh();
        }).bounds(cx + 115, y, 40, 20).build();
        addEntry(minusBtn, y);
        addEntry(valueBtn, y);
        addEntry(plusBtn, y);
        return valueBtn;
    }

    private void addEntry(AbstractWidget w, int baseY)
    {
        this.addRenderableWidget(w);
        entries.add(new Entry(w, baseY));
    }

    private void layout()
    {
        for (Entry e : entries)
        {
            e.widget.setY(e.baseY - scrollOffset);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta)
    {
        if (maxScroll <= 0) return false;
        int before = scrollOffset;
        scrollOffset = Mth.clamp(scrollOffset - (int) Math.round(delta) * 15, 0, maxScroll);
        if (scrollOffset != before)
        {
            layout();
            return true;
        }
        return false;
    }

    private void refresh()
    {
        autoHitButton.setMessage(label("autofishs.config.autohit", AutoFishSConfig.ENABLE_MINIGAME_AUTO_HIT.get()));
        autoReelButton.setMessage(label("autofishs.config.autoreel", AutoFishSConfig.ENABLE_AUTO_REEL.get()));
        autoCastButton.setMessage(label("autofishs.config.autocast", AutoFishSConfig.ENABLE_AUTO_CAST.get()));
        treasureFocusButton.setMessage(label("autofishs.config.treasure", AutoFishSConfig.TREASURE_FOCUS.get()));
        clickFrozenButton.setMessage(label("autofishs.config.frozen", AutoFishSConfig.CLICK_FROZEN_SPOTS.get()));
        clickExplosiveButton.setMessage(label("autofishs.config.explosive", AutoFishSConfig.CLICK_EXPLOSIVE_SPOTS.get()));
        modeButton.setMessage(Component.translatable("autofishs.config.mode", AutoFishSConfig.MODE.get().name()));
        foModeButton.setMessage(Component.translatable("autofishs.config.fo_mode", AutoFishSConfig.FO_MODE.get().name()));
        leewayValueButton.setMessage(Component.translatable("autofishs.config.leeway", AutoFishSConfig.PERFECT_LEEWAY.get()));
    }

    private static Component label(String key, boolean on)
    {
        return Component.translatable(key, stateText(on));
    }

    private static String stateText(boolean on)
    {
        return on ? "ON" : "OFF";
    }

    @Override
    public void onClose()
    {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen()
    {
        return true;
    }
}
