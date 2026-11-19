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

    /**
     * Brute-force mode: skip the Starcatcher minigame entirely instead of playing it.
     * <p>
     * Deliberately source-only. There is no config key, no row in the config screen and no
     * changelog entry for it -- flip it here and rebuild if you want it.
     * <p>
     * Implementation note: rather than forging a {@code FishingCompletedPayload} by hand, this
     * pushes {@code progress} past {@code hp}. Starcatcher's own success branch then runs
     * ({@code FishingMinigameScreen:631}) and sends exactly the packet a legitimate clear would,
     * including the follow-up {@code (-1, false, false, hits)} that {@code onClose()} always
     * emits. The server has nothing to notice.
     * <p>
     * On a multiplayer server this is indistinguishable from cheating, which is why it is not
     * exposed anywhere a player can reach.
     */
    public static final boolean BRUTE_FORCE_MODE = false;

    /**
     * Why the assist is (or is not) acting right now. Read by {@link MinigameStatusOverlay} so the
     * player can tell a deliberate decision apart from a broken mod; nothing here feeds back into
     * the hit logic.
     */
    public enum AssistState
    {
        DISABLED("disabled"),
        TOURNAMENT("tournament"),
        UNCAUGHT("uncaught"),
        BRUTE_FORCE("brute"),
        COOLDOWN("cooldown"),
        READY("ready");

        private final String key;

        AssistState(String key)
        {
            this.key = key;
        }

        public String langKey()
        {
            return "autofishs.status." + key;
        }
    }

    private static AssistState assistState = AssistState.DISABLED;

    /** Last decision taken in {@link #tick()}; purely for the on-screen read-out. */
    public static AssistState assistState()
    {
        return assistState;
    }

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
            assistState = AssistState.DISABLED;
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
            // No AssistState for this one: closing the screen ends the read-out immediately, so
            // there would be nothing left to draw it on.
            screen.onClose();
            return;
        }

        // Eligibility gates. The tournament one stays a source constant on purpose: it must not
        // be flippable from the config file or the config screen.
        if (!ALLOW_IN_TOURNAMENT && TournamentOverlay.tournament != null)
        {
            assistState = AssistState.TOURNAMENT;
            return;
        }
        if (!isUncaughtFishAllowed(mc.player, screen))
        {
            assistState = AssistState.UNCAUGHT;
            return;
        }

        // Skip the minigame itself when the hidden brute-force constant is on. Done before the
        // auto-hit flag so it does not depend on the assist being enabled.
        if (BRUTE_FORCE_MODE)
        {
            assistState = AssistState.BRUTE_FORCE;
            screen.progress = screen.hp + 1;
            screen.progressSmooth = screen.hp + 1;
            return;
        }

        if (!AutoFishSConfig.ENABLE_MINIGAME_AUTO_HIT.get())
        {
            assistState = AssistState.DISABLED;
            return;
        }

        if (hitCooldown > 0)
        {
            hitCooldown--;
            assistState = AssistState.COOLDOWN;
            return;
        }

        if (!shouldHit(screen))
        {
            assistState = AssistState.READY;
            return;
        }

        // Reset the cooldown (in ticks) so hits are at least `hitIntervalSeconds` apart.
        assistState = AssistState.COOLDOWN;
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
            return isNearCenter(pointerPos, best, AutoFishSConfig.PERFECT_LEEWAY.get());
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

    /**
     * Decides whether the assist may run on a fish the player has not caught yet.
     * <p>
     * Already-caught fish are always allowed. For uncaught ones the {@code allowOnUncaughtFish}
     * toggle gates the whole thing and {@code uncaughtFishRarities} narrows it down per
     * Starcatcher rarity, so low-tier fish can go on autopilot while epic/legendary still have to
     * be played by hand the first time. Both settings live in the generated .toml only.
     */
    private static boolean isUncaughtFishAllowed(Player player, FishingMinigameScreen screen)
    {
        if (isFishInCatalogue(player, screen.itemBeingFished))
            return true;
        if (!AutoFishSConfig.ALLOW_ON_UNCAUGHT_FISH.get())
            return false;

        String rarity = screen.rarity == null ? "none" : screen.rarity.getSerializedName();
        for (String allowed : AutoFishSConfig.UNCAUGHT_FISH_RARITIES.get())
        {
            if (allowed != null && allowed.equalsIgnoreCase(rarity))
                return true;
        }
        return false;
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

    /**
     * PERFECT gate: the pointer must sit near the spot's center, but never outside the window the
     * host actually scores.
     * <p>
     * Starcatcher only registers a hit while {@code |Δ| < spot.thickness / 2} — strict comparison,
     * integer division (see {@code FishingMinigameScreen.doDegreesOverlapWithLeeway}, which
     * {@code inputPressed()} calls with {@code ass.thickness / 2}). The configured leeway is
     * therefore clamped into that window. Without the clamp a leeway wider than the spot's half
     * width makes every press fall outside the spot: {@code inputPressed()} finds nothing to hit
     * and applies the miss penalty ({@code progress -= penalty}). Thin spots are the worst case —
     * {@code THIN} has thickness 15, i.e. a real window of |Δ| &lt; 7.
     */
    private static boolean isNearCenter(float pointerPos, ActiveSweetSpot spot, int configuredLeeway)
    {
        int halfWidth = spot.thickness / 2;
        int effective = Math.min(configuredLeeway, Math.max(0, halfWidth - 1));
        return angularDistance(pointerPos, spot.pos) <= effective;
    }

    /** Shortest unsigned angular distance between two degree values, in [0, 180]. */
    private static int angularDistance(float a, float b)
    {
        float diff = Math.abs((a % 360f + 360f) % 360f - (b % 360f + 360f) % 360f);
        if (diff > 180f) diff = 360f - diff;
        return Math.round(diff);
    }
}
