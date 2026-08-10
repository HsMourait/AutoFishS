package com.autofishs.client;

import com.autofishs.AutoFishSConfig;
import com.autofishs.AutoFishSConfig.AutoHitMode;
import com.wdiscute.starcatcher.minigame.ActiveSweetSpot;
import com.wdiscute.starcatcher.minigame.FishingMinigameScreen;
import com.wdiscute.starcatcher.io.FishCaughtCounter;
import com.wdiscute.starcatcher.io.attachments.FishingGuideAttachment;
import com.wdiscute.starcatcher.registry.sweetspotbehaviour.FreezeSweetSpotBehaviour;
import com.wdiscute.starcatcher.registry.sweetspotbehaviour.TntSweetSpotBehaviour;
import com.wdiscute.starcatcher.registry.sweetspotbehaviour.TreasureSweetSpotBehaviour;
import com.wdiscute.starcatcher.tournament.TournamentOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Automatically performs hits in the Starcatcher fishing minigame.
 * <p>
 * This is purely client-side: it reads the currently open
 * {@link FishingMinigameScreen} and calls its public {@code inputPressed()}
 * when the pointer is aligned with a sweet spot. It never touches the network.
 */
public final class MinigameAutoHitHandler
{
    /** Whether auto-hit works while the player is in an active Starcatcher tournament. */
    public static final boolean ALLOW_IN_TOURNAMENT = false;
    /** Whether auto-hit works on fish the player has not caught yet (not in the catalogue). */
    public static final boolean ALLOW_ON_UNCAUGHT_FISH = false;

    private static int hitCooldown = 0;
    private static FishingMinigameScreen lastScreen = null;
    private static boolean blacklistApplied = false;

    private MinigameAutoHitHandler() {}

    public static void tick()
    {
        Minecraft mc = Minecraft.getInstance();

        if (!(mc.screen instanceof FishingMinigameScreen screen))
        {
            hitCooldown = 0;
            lastScreen = null;
            blacklistApplied = false;
            return;
        }

        // Blacklist: when the fish being caught is blacklisted, press ESC (close) once to abort.
        if (screen != lastScreen)
        {
            lastScreen = screen;
            blacklistApplied = false;
        }
        if (!blacklistApplied && isBlacklisted(screen.itemBeingFished))
        {
            blacklistApplied = true;
            screen.onClose();
            return;
        }

        // Source-level gates (edit the constants above to override; not configurable).
        if (!ALLOW_IN_TOURNAMENT && TournamentOverlay.tournament != null)
            return;
        if (!ALLOW_ON_UNCAUGHT_FISH && !isFishInCatalogue(mc.player, screen.itemBeingFished))
            return;

        if (!AutoFishSConfig.ENABLE_MINIGAME_AUTO_HIT.get())
            return;

        if (hitCooldown > 0)
        {
            hitCooldown--;
            return;
        }

        if (!shouldHit(screen))
            return;

        // Reset the cooldown (in ticks) so hits are at least `hitIntervalSeconds` apart.
        hitCooldown = Math.max(0, (int) Math.ceil(AutoFishSConfig.HIT_INTERVAL_SECONDS.get() * 20) - 1);
        screen.inputPressed();
    }

    private static boolean shouldHit(FishingMinigameScreen screen)
    {
        float pointerPos = screen.getPointerPosPrecise();
        List<ActiveSweetSpot> spots = screen.getActiveSweetSpots();

        // 1. Only consider spots we are allowed to hit (skip negative spots unless enabled).
        List<ActiveSweetSpot> targetable = new ArrayList<>();
        for (ActiveSweetSpot spot : spots)
        {
            if (isTargetable(spot)) targetable.add(spot);
        }

        // 2. Treasure focus: if any treasure spot is present, aim only at treasure spots.
        //    Exception: if the fish is about to escape (progress below 50% of hp), also allow
        //    hitting other targetable spots so we don't lose the catch while chasing treasure.
        if (AutoFishSConfig.TREASURE_FOCUS.get())
        {
            List<ActiveSweetSpot> treasure = new ArrayList<>();
            for (ActiveSweetSpot spot : targetable)
            {
                if (spot.behaviour instanceof TreasureSweetSpotBehaviour) treasure.add(spot);
            }
            if (!treasure.isEmpty() && !isFishEscaping(screen)) targetable = treasure;
        }

        if (targetable.isEmpty()) return false;

        if (AutoFishSConfig.MODE.get() == AutoHitMode.PERFECT)
        {
            // Only hit when the pointer is near the center of the closest targetable sweet spot.
            ActiveSweetSpot best = findClosestSpot(targetable, pointerPos);
            if (best == null) return false;
            return isNearCenter(pointerPos, best.pos, AutoFishSConfig.PERFECT_LEEWAY.get());
        }

        // AUTO: hit when the pointer overlaps any targetable sweet spot (same check starcatcher uses to score a hit).
        for (ActiveSweetSpot spot : targetable)
        {
            if (FishingMinigameScreen.doDegreesOverlapWithLeeway(pointerPos, spot.pos, spot.thickness / 2))
                return true;
        }
        return false;
    }

    /** Returns true if the item being caught is in the configured blacklist. */
    private static boolean isBlacklisted(ItemStack stack)
    {
        for (String id : AutoFishSConfig.FISHING_BLACKLIST.get())
        {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && BuiltInRegistries.ITEM.get(rl) == stack.getItem())
                return true;
        }
        return false;
    }

    /** True if the player has already caught this fish (present in the Starcatcher catalogue). */
    private static boolean isFishInCatalogue(Player player, ItemStack stack)
    {
        Map<ResourceLocation, FishCaughtCounter> caught = FishingGuideAttachment.getFishesCaught(player);
        return caught.containsKey(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** True when the fish is close to escaping (catch progress below half of its max). */
    private static boolean isFishEscaping(FishingMinigameScreen screen)
    {
        return screen.hp > 0 && screen.progress < screen.hp * 0.5f;
    }

    /** Returns true if this spot is allowed to be auto-hit (negative spots gated by config). */
    private static boolean isTargetable(ActiveSweetSpot spot)
    {
        if (spot.behaviour instanceof TntSweetSpotBehaviour && !AutoFishSConfig.CLICK_EXPLOSIVE_SPOTS.get())
            return false;
        if (spot.behaviour instanceof FreezeSweetSpotBehaviour && !AutoFishSConfig.CLICK_FROZEN_SPOTS.get())
            return false;
        return true;
    }

    private static ActiveSweetSpot findClosestSpot(List<ActiveSweetSpot> spots, float pointerPos)
    {
        ActiveSweetSpot closest = null;
        int bestDist = Integer.MAX_VALUE;
        for (ActiveSweetSpot spot : spots)
        {
            int dist = angularDistance(pointerPos, spot.pos);
            if (dist < bestDist)
            {
                bestDist = dist;
                closest = spot;
            }
        }
        return closest;
    }

    private static boolean isNearCenter(float pointerPos, float centerPos, int leeway)
    {
        return angularDistance(pointerPos, centerPos) <= leeway;
    }

    /** Shortest unsigned angular distance between two degree values, in [0, 180]. */
    private static int angularDistance(float a, float b)
    {
        float diff = Math.abs((a % 360f + 360f) % 360f - (b % 360f + 360f) % 360f);
        if (diff > 180f) diff = 360f - diff;
        return Math.round(diff);
    }
}
