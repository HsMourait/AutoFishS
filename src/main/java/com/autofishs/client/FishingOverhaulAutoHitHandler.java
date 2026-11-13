package com.autofishs.client;

import com.autofishs.AutoFishS;
import com.autofishs.AutoFishSConfig;
import com.autofishs.AutoFishSConfig.AutoHitMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.ModList;

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

    /** Fishing Overhaul's mod id, as declared in its META-INF/mods.toml. */
    private static final String FO_MOD_ID = "fishingoverhaul";

    /** Tick cooldown shared with the Starcatcher side so both play by the same interval rule. */
    private static int hitCooldown = 0;
    private static Screen lastScreen = null;

    private FishingOverhaulAutoHitHandler() {}

    public static void tick()
    {
        Minecraft mc = Minecraft.getInstance();

        boolean foMinigameOpen = mc.screen != null
                && initReflection()
                && minigameClass.isInstance(mc.screen);

        if (!foMinigameOpen)
        {
            hitCooldown = 0;
            lastScreen = null;
            return;
        }

        if (!AutoFishSConfig.ENABLE_MINIGAME_AUTO_HIT.get())
            return;

        // Fresh cooldown for each new minigame instance.
        if (mc.screen != lastScreen)
        {
            lastScreen = mc.screen;
            hitCooldown = 0;
        }

        if (hitCooldown > 0)
        {
            hitCooldown--;
            return;
        }

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
            // Rate-limit to the shared hit interval. Fishing Overhaul's fishDeg only advances
            // during render() (with partialTicks), so the value read here can lag the drawn fish
            // by up to a frame; a click that lands outside the zone costs a miss penalty, so we
            // do not want to re-fire every tick.
            hitCooldown = Math.max(0, (int) Math.ceil(AutoFishSConfig.HIT_INTERVAL_SECONDS.get() * 20) - 1);
            ((Screen) mc.screen).mouseClicked(0, 0, 0);
        }
    }

    private static boolean initReflection()
    {
        if (reflectionInit) return reflectionAvailable;
        reflectionInit = true;

        // Fishing Overhaul simply not being installed is the normal case for most players:
        // skip the class probe entirely and stay silent. ModList lookups are a set contains().
        if (!ModList.get().isLoaded(FO_MOD_ID))
        {
            reflectionAvailable = false;
            return false;
        }

        // From here on the mod IS present, so any failure is a real incompatibility worth logging.
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
            AutoFishS.LOGGER.warn("[AutoFishS] Fishing Overhaul is installed but its minigame class/fields"
                    + " could not be resolved; FO auto-hit disabled", e);
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
