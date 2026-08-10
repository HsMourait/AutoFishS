package com.autofishs.client;

import com.autofishs.AutoFishS;
import com.autofishs.AutoFishSConfig;
import com.autofishs.AutoFishSConfig.AutoHitMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;

/**
 * Auto-hits the Fishing Overhaul minigame (optional compat).
 * <p>
 * Fully reflection-based so it has NO compile/runtime dependency on Fishing Overhaul:
 * the class, fields and catch-area logic are resolved reflectively. If Fishing Overhaul
 * is absent or changes its internals, this simply disables gracefully. The click itself
 * goes through {@link Screen#mouseClicked} (vanilla, public), producing the same result
 * packet a real click would.
 */
public final class FishingOverhaulAutoHitHandler
{
    private static Class<?> minigameClass;
    private static Field fishDegField;
    private static Field catchChanceField;
    private static Field critChanceField;
    private static boolean reflectionInit = false;
    private static boolean reflectionAvailable = false;

    private FishingOverhaulAutoHitHandler() {}

    public static void tick()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null)
            return;

        if (!AutoFishSConfig.ENABLE_MINIGAME_AUTO_HIT.get())
            return;

        if (!initReflection())
            return;

        if (!minigameClass.isInstance(mc.screen))
            return;

        float fishDeg;
        float catchChance;
        float critChance;
        try
        {
            fishDeg = fishDegField.getFloat(mc.screen);
            catchChance = catchChanceField.getFloat(mc.screen);
            critChance = critChanceField.getFloat(mc.screen);
        }
        catch (IllegalAccessException e)
        {
            return;
        }

        float fish = normalizeDegrees(fishDeg);
        boolean inCrit = isFishCaught(fish, critChance, 270) || isFishCaught(fish, critChance, 90);
        boolean inCatch = isFishCaught(fish, catchChance, 270) || isFishCaught(fish, catchChance, 90);

        boolean shouldClick = AutoFishSConfig.FO_MODE.get() == AutoHitMode.PERFECT ? inCrit : (inCrit || inCatch);
        if (shouldClick)
        {
            ((Screen) mc.screen).mouseClicked(0, 0, 0);
        }
    }

    private static boolean initReflection()
    {
        if (reflectionInit) return reflectionAvailable;
        reflectionInit = true;
        try
        {
            minigameClass = Class.forName("github.pitbox46.fishingoverhaul.MinigameScreen");
            fishDegField = minigameClass.getDeclaredField("fishDeg");
            catchChanceField = minigameClass.getDeclaredField("catchChance");
            critChanceField = minigameClass.getDeclaredField("critChance");
            fishDegField.setAccessible(true);
            catchChanceField.setAccessible(true);
            critChanceField.setAccessible(true);
            reflectionAvailable = true;
        }
        catch (ClassNotFoundException | NoSuchFieldException e)
        {
            // Graceful: absent mod or changed internals -> compat just stays off.
            AutoFishS.LOGGER.warn("[AutoFishS] Fishing Overhaul minigame not found, compat disabled", e);
            reflectionAvailable = false;
        }
        return reflectionAvailable;
    }

    private static float normalizeDegrees(float degreesIn)
    {
        return degreesIn % 360 >= 0 ? degreesIn % 360 : (degreesIn % 360) + 360;
    }

    private static boolean isInRange(float degreesIn, float lower, float upper)
    {
        return ((lower <= upper && degreesIn >= lower && degreesIn <= upper)
                || (lower > upper && !(degreesIn <= lower && degreesIn >= upper)));
    }

    private static boolean isFishCaught(float cappedFishDeg, float catchChance, float offset)
    {
        return isInRange(cappedFishDeg,
                normalizeDegrees(offset - 180 * catchChance),
                normalizeDegrees(offset + 180 * catchChance));
    }
}
