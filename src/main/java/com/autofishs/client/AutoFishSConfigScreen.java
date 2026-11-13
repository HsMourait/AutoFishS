package com.autofishs.client;

import com.autofishs.AutoFishSConfig;
import com.autofishs.AutoFishSConfig.AutoHitMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Client-only config screen opened from ESC -> Mods -> AutoFishS -> Config.
 * <p>
 * Edits the same values as the config file and persists them immediately. Options are grouped
 * under section headings, every row carries a visible label (including the numeric ones, which
 * use -/+ steppers), and the list scrolls with the mouse wheel when it is taller than the screen.
 */
public class AutoFishSConfigScreen extends Screen
{
    private static final int ROW_H = 26;
    private static final int ROW_W = 310;
    private static final int HEADER_H = 24;
    private static final int COLOR_HEADER = 0xFF9AA0A6;

    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Header> headers = new ArrayList<>();

    private Button autoHitButton;
    private Button autoReelButton;
    private Button autoCastButton;
    private Button treasureFocusButton;
    private Button clickFrozenButton;
    private Button clickExplosiveButton;
    private Button modeButton;
    private Button foModeButton;
    private Button leewayValueButton;
    private Button intervalValueButton;

    private int scrollOffset = 0;
    private int maxScroll = 0;

    private record Entry(AbstractWidget widget, int baseY) {}

    private record Header(String key, int baseY) {}

    public AutoFishSConfigScreen(Screen parent)
    {
        super(Component.translatable("autofishs.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init()
    {
        // init() runs again whenever the window is resized; rebuild from scratch so widgets from
        // the previous pass are not accumulated.
        entries.clear();
        headers.clear();

        int cx = this.width / 2;
        int y = Math.max(20, this.height / 6);

        // ---- Starcatcher hits ----
        y = addHeader("autofishs.config.section.hit", y);

        modeButton = Button.builder(
                Component.translatable("autofishs.config.mode", AutoFishSConfig.MODE.get().name()),
                b -> {
                    AutoFishSConfig.setMode(AutoFishSConfig.MODE.get() == AutoHitMode.AUTO
                            ? AutoHitMode.PERFECT : AutoHitMode.AUTO);
                    refresh();
                }
        ).bounds(cx - ROW_W / 2, y, ROW_W, 20).build();
        addEntry(modeButton, y);
        y += ROW_H;

        intervalValueButton = addValueRow(cx, y,
                Component.translatable("autofishs.config.interval",
                        formatSeconds(AutoFishSConfig.HIT_INTERVAL_SECONDS.get())),
                () -> stepInterval(-0.1),
                () -> stepInterval(0.1));
        y += ROW_H;

        autoHitButton = addToggle(cx, y, "autofishs.config.autohit",
                AutoFishSConfig.ENABLE_MINIGAME_AUTO_HIT::get,
                () -> AutoFishSConfig.setAutoHit(!AutoFishSConfig.ENABLE_MINIGAME_AUTO_HIT.get()));
        y += ROW_H;

        // ---- Sweet spot strategy ----
        y = addHeader("autofishs.config.section.spots", y);

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

        leewayValueButton = addValueRow(cx, y,
                Component.translatable("autofishs.config.leeway", AutoFishSConfig.PERFECT_LEEWAY.get()),
                () -> stepLeeway(-1),
                () -> stepLeeway(1));
        y += ROW_H;

        // ---- Reel / cast ----
        y = addHeader("autofishs.config.section.rod", y);

        autoReelButton = addToggle(cx, y, "autofishs.config.autoreel",
                AutoFishSConfig.ENABLE_AUTO_REEL::get,
                () -> AutoFishSConfig.setAutoReel(!AutoFishSConfig.ENABLE_AUTO_REEL.get()));
        y += ROW_H;

        autoCastButton = addToggle(cx, y, "autofishs.config.autocast",
                AutoFishSConfig.ENABLE_AUTO_CAST::get,
                () -> AutoFishSConfig.setAutoCast(!AutoFishSConfig.ENABLE_AUTO_CAST.get()));
        y += ROW_H;

        // ---- Fishing Overhaul compatibility ----
        y = addHeader("autofishs.config.section.compat", y);

        foModeButton = Button.builder(
                Component.translatable("autofishs.config.fo_mode", AutoFishSConfig.FO_MODE.get().name()),
                b -> {
                    AutoFishSConfig.setFoMode(AutoFishSConfig.FO_MODE.get() == AutoHitMode.AUTO
                            ? AutoHitMode.PERFECT : AutoHitMode.AUTO);
                    refresh();
                }
        ).bounds(cx - ROW_W / 2, y, ROW_W, 20).build();
        addEntry(foModeButton, y);
        y += ROW_H;

        int backY = this.height - 30;
        maxScroll = Math.max(0, y - (backY - ROW_H));
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
        layout();

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> this.onClose())
                .bounds(cx - 100, backY, 200, 20).build());

        // Populate every label so values are shown immediately on open.
        refresh();
    }

    private int addHeader(String key, int y)
    {
        headers.add(new Header(key, y + 6));
        return y + HEADER_H;
    }

    private Button addToggle(int cx, int y, String key, BooleanSupplier active, Runnable action)
    {
        Button b = Button.builder(
                label(key, active.getAsBoolean()),
                btn -> {
                    action.run();
                    refresh();
                }
        ).bounds(cx - ROW_W / 2, y, ROW_W, 20).build();
        addEntry(b, y);
        return b;
    }

    /** A labelled numeric row: [-] value [+] — the middle button shows the full label. */
    private Button addValueRow(int cx, int y, Component initialValue, Runnable minus, Runnable plus)
    {
        Button minusBtn = Button.builder(Component.literal("-"), btn -> {
            minus.run();
            refresh();
        }).bounds(cx - ROW_W / 2, y, 40, 20).build();

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
            e.widget().setY(e.baseY() - scrollOffset);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks)
    {
        renderBackground(guiGraphics, mouseX, mouseY, partialTicks);

        for (Header header : headers)
        {
            int headerY = header.baseY() - scrollOffset;
            if (headerY < -HEADER_H || headerY > this.height)
                continue;

            guiGraphics.drawString(this.font, Component.translatable(header.key()),
                    this.width / 2 - ROW_W / 2, headerY, COLOR_HEADER, false);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if (maxScroll <= 0) return false;

        int before = scrollOffset;
        scrollOffset = Mth.clamp(scrollOffset - (int) Math.round(scrollY) * 15, 0, maxScroll);
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
        intervalValueButton.setMessage(Component.translatable("autofishs.config.interval",
                formatSeconds(AutoFishSConfig.HIT_INTERVAL_SECONDS.get())));
        leewayValueButton.setMessage(Component.translatable("autofishs.config.leeway", AutoFishSConfig.PERFECT_LEEWAY.get()));
    }

    /** Steps the hit interval by {@code delta} seconds, snapped to 0.1 s (clamped by the config). */
    private static void stepInterval(double delta)
    {
        double next = Math.round((AutoFishSConfig.HIT_INTERVAL_SECONDS.get() + delta) * 10.0) / 10.0;
        AutoFishSConfig.setHitInterval(next);
    }

    private static void stepLeeway(int delta)
    {
        AutoFishSConfig.setPerfectLeeway(AutoFishSConfig.PERFECT_LEEWAY.get() + delta);
    }

    private static String formatSeconds(double seconds)
    {
        return String.format(Locale.ROOT, "%.1f", seconds);
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
