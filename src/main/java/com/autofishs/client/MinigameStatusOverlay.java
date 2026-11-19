package com.autofishs.client;

import com.autofishs.AutoFishSConfig;
import com.autofishs.client.MinigameAutoHitHandler.AssistState;
import com.wdiscute.starcatcher.minigame.ActiveSweetSpot;
import com.wdiscute.starcatcher.minigame.FishingMinigameScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * Small read-out drawn on top of the Starcatcher minigame.
 * <p>
 * Registered through {@code ScreenEvent.Render.Post}, which fires after the screen's own render
 * pass has finished, so it lands above everything the minigame drew. No mixin, no pose or depth
 * tricks, and no interference with the background the screen paints for itself.
 * <p>
 * The point is not to duplicate the progress bar the minigame already draws — it is to make the
 * assist's own decisions visible. Every gate this mod applies (blacklist, tournament, uncaught
 * fish outside the allowed rarities, the auto-hit toggle, the hit cooldown) is otherwise silent,
 * so from the player's seat "the mod is deliberately not helping" and "the mod is broken" look
 * exactly the same.
 */
public final class MinigameStatusOverlay
{
    private static final int MARGIN = 6;
    private static final int PAD = 4;
    private static final int LINE = 10;
    private static final int BACKGROUND = 0x90101010;

    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_READY = 0x55FF55;
    private static final int COLOR_WAIT = 0xFFFF55;
    private static final int COLOR_BLOCKED = 0xFF5555;
    private static final int COLOR_IDLE = 0xAAAAAA;
    private static final int COLOR_BRUTE = 0xCC66FF;

    private MinigameStatusOverlay() {}

    public static void render(GuiGraphics guiGraphics, FishingMinigameScreen screen)
    {
        if (!AutoFishSConfig.SHOW_STATUS_OVERLAY.get())
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null)
            return;

        var font = mc.font;

        Component mode = AutoFishSConfig.MODE.get() == AutoFishSConfig.AutoHitMode.PERFECT
                ? Component.translatable("autofishs.status.mode_perfect", AutoFishSConfig.PERFECT_LEEWAY.get())
                : Component.translatable("autofishs.status.mode_auto");
        Component progress = Component.translatable("autofishs.status.progress",
                (int) screen.progressSmooth, screen.hp);
        Component nearest = nearestSpotText(screen);
        Component state = Component.translatable(MinigameAutoHitHandler.assistState().langKey());

        int lines = nearest == null ? 3 : 4;

        int contentWidth = Math.max(font.width(mode), Math.max(font.width(progress), font.width(state)));
        if (nearest != null)
            contentWidth = Math.max(contentWidth, font.width(nearest));

        int boxWidth = contentWidth + PAD * 2;
        int boxHeight = lines * LINE + PAD;
        int x = MARGIN;
        int y = screen.height - MARGIN - boxHeight;

        guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, BACKGROUND);

        int textX = x + PAD;
        int textY = y + PAD;
        guiGraphics.drawString(font, mode, textX, textY, COLOR_TEXT, false);
        textY += LINE;
        guiGraphics.drawString(font, progress, textX, textY, COLOR_TEXT, false);
        textY += LINE;
        if (nearest != null)
        {
            guiGraphics.drawString(font, nearest, textX, textY, COLOR_TEXT, false);
            textY += LINE;
        }
        guiGraphics.drawString(font, state, textX, textY, stateColor(MinigameAutoHitHandler.assistState()), false);
    }

    /** Angular gap from the pointer to the closest sweet spot, or null when there is none. */
    private static Component nearestSpotText(FishingMinigameScreen screen)
    {
        List<ActiveSweetSpot> spots = screen.getActiveSweetSpots();
        if (spots.isEmpty())
            return null;

        float pointerPos = screen.getPointerPosPrecise();
        float closest = Float.MAX_VALUE;
        for (ActiveSweetSpot spot : spots)
        {
            float distance = angleDistance(pointerPos, spot.pos);
            if (distance < closest)
                closest = distance;
        }

        if (closest == Float.MAX_VALUE)
            return null;

        return Component.translatable("autofishs.status.nearest",
                String.format(Locale.ROOT, "%.1f", closest));
    }

    private static float angleDistance(float a, float b)
    {
        float d = Math.abs(a - b) % 360.0F;
        return d > 180.0F ? 360.0F - d : d;
    }

    private static int stateColor(AssistState state)
    {
        return switch (state)
        {
            case READY -> COLOR_READY;
            case COOLDOWN -> COLOR_WAIT;
            case TOURNAMENT, UNCAUGHT -> COLOR_BLOCKED;
            case BRUTE_FORCE -> COLOR_BRUTE;
            case DISABLED -> COLOR_IDLE;
        };
    }
}
