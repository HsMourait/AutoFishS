package com.autofishs;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import java.util.ArrayList;
import java.util.List;

public class AutoFishSConfig
{
    public enum AutoHitMode
    {
        /** Hit as soon as the pointer overlaps any sweet spot. */
        AUTO,
        /** Only hit when the pointer is near the center of a sweet spot, for perfect-catch scoring. */
        PERFECT
    }

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLE_MINIGAME_AUTO_HIT =
            BUILDER.comment("Automatically press the hit key when the pointer aligns with a sweet spot in the Starcatcher fishing minigame.",
                            "Default off; enable manually in the config screen (F8) or this file.")
                    .define("enableMinigameAutoHit", false);

    public static final ForgeConfigSpec.BooleanValue ENABLE_AUTO_REEL =
            BUILDER.comment("Automatically reel in when the Starcatcher bobber bites.")
                    .define("enableAutoReel", true);

    public static final ForgeConfigSpec.BooleanValue ENABLE_AUTO_CAST =
            BUILDER.comment("Automatically cast the rod again after the previous fishing session ends.")
                    .define("enableAutoCast", true);

    public static final ForgeConfigSpec.EnumValue<AutoHitMode> MODE =
            BUILDER.comment("How aggressively the mod hits (Starcatcher minigame).",
                            "AUTO    = hit whenever the pointer is anywhere inside a sweet spot.",
                            "PERFECT = only hit when the pointer is near the center of a sweet spot (better scoring).")
                    .defineEnum("mode", AutoHitMode.AUTO);

    public static final ForgeConfigSpec.EnumValue<AutoHitMode> FO_MODE =
            BUILDER.comment("Hit target for the Fishing Overhaul minigame.",
                            "AUTO    = click when the fish is in the normal catch area.",
                            "PERFECT = only click when the fish is in the crit area.")
                    .defineEnum("foMode", AutoHitMode.PERFECT);

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FISHING_BLACKLIST =
            BUILDER.comment("Fishing blacklist (Starcatcher). When the fish being caught in the Starcatcher",
                            "minigame is one of these items, AutoFishS presses ESC to abort the catch.",
                            "Use item registry IDs, e.g. 'minecraft:cod' or 'starcatcher:example_fish'.")
                    .defineList("fishingBlacklist", List.of(), o -> o instanceof String);

    public static final ForgeConfigSpec.DoubleValue HIT_INTERVAL_SECONDS =
            BUILDER.comment("Minimum seconds to wait between automatic hits. Higher = fewer presses, less spam.")
                    .defineInRange("hitIntervalSeconds", 1.0, 0.1, 5.0);

    public static final ForgeConfigSpec.BooleanValue TREASURE_FOCUS =
            BUILDER.comment("When a treasure sweet spot is present, only aim at treasure spots and ignore others until it is gone.")
                    .define("treasureFocus", true);

    public static final ForgeConfigSpec.BooleanValue CLICK_FROZEN_SPOTS =
            BUILDER.comment("Also click frozen (Freeze) sweet spots. Off by default to avoid the freeze effect.")
                    .define("clickFrozenSpots", false);

    public static final ForgeConfigSpec.BooleanValue CLICK_EXPLOSIVE_SPOTS =
            BUILDER.comment("Also click explosive (TNT) sweet spots. Off by default because hitting them reduces progress.")
                    .define("clickExplosiveSpots", false);

    public static final ForgeConfigSpec.IntValue PERFECT_LEEWAY =
            BUILDER.comment("Angular leeway (in degrees) from a sweet spot's center within which PERFECT mode will hit.")
                    .defineInRange("perfectLeeway", 6, 1, 45);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static ModConfig clientConfig;

    /** Writes the current values back to the client config file so they survive restarts. */
    public static void save()
    {
        if (clientConfig == null)
        {
            clientConfig = ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.CLIENT).stream()
                    .filter(c -> c.getSpec() == SPEC)
                    .findFirst()
                    .orElse(null);
        }
        if (clientConfig != null) clientConfig.save();
    }

    public static void setAutoHit(boolean enabled)
    {
        ENABLE_MINIGAME_AUTO_HIT.set(enabled);
        save();
    }

    public static void setAutoReel(boolean enabled)
    {
        ENABLE_AUTO_REEL.set(enabled);
        save();
    }

    public static void setAutoCast(boolean enabled)
    {
        ENABLE_AUTO_CAST.set(enabled);
        save();
    }

    public static void setMode(AutoHitMode mode)
    {
        MODE.set(mode);
        save();
    }

    public static void setFoMode(AutoHitMode mode)
    {
        FO_MODE.set(mode);
        save();
    }

    public static void setHitInterval(double seconds)
    {
        HIT_INTERVAL_SECONDS.set(Math.max(0.1, Math.min(5.0, seconds)));
        save();
    }

    public static void setTreasureFocus(boolean enabled)
    {
        TREASURE_FOCUS.set(enabled);
        save();
    }

    public static void setClickFrozen(boolean enabled)
    {
        CLICK_FROZEN_SPOTS.set(enabled);
        save();
    }

    public static void setClickExplosive(boolean enabled)
    {
        CLICK_EXPLOSIVE_SPOTS.set(enabled);
        save();
    }

    public static void setPerfectLeeway(int leeway)
    {
        PERFECT_LEEWAY.set(Math.max(1, Math.min(45, leeway)));
        save();
    }

    /** Adds or removes an item registry ID from the fishing blacklist, then persists. */
    public static void toggleBlacklist(String itemId)
    {
        List<String> list = new ArrayList<>();
        for (Object o : FISHING_BLACKLIST.get()) list.add(String.valueOf(o));
        if (list.contains(itemId)) list.remove(itemId);
        else list.add(itemId);
        FISHING_BLACKLIST.set(list);
        save();
    }
}
