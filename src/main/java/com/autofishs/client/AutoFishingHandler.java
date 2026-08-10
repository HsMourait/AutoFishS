package com.autofishs.client;

import com.autofishs.AutoFishS;
import com.autofishs.AutoFishSConfig;
import com.wdiscute.starcatcher.SCTags;
import com.wdiscute.starcatcher.bobberentity.FishingBobEntity;
import com.wdiscute.starcatcher.minigame.FishingMinigameScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Client-side auto-reel / auto-cast for both the Starcatcher rod and the vanilla rod.
 * <p>
 * Pure client: it only simulates the vanilla use-key click (a normal right-click) at
 * the right moment, so the server handles it exactly as if the player did it.
 * No custom packets, no server-side logic.
 */
public final class AutoFishingHandler
{
    private static final int REEL_COOLDOWN = 12;
    private static final int RECAST_DELAY = 20;

    private static int reelCooldown = 0;
    private static int recastDelay = 0;
    private static boolean pendingRecast = false;
    private static boolean hadSessionActive = false;
    private static Level lastLevel = null;

    // Vanilla FishingHook.DATA_BITING accessor, resolved reflectively (field name differs in prod).
    private static EntityDataAccessor<Boolean> dataBitingAccessor;
    private static boolean vanillaInit = false;

    private AutoFishingHandler() {}

    public static void tick()
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
        {
            hadSessionActive = false;
            lastLevel = null;
            return;
        }

        // Reset the state machine when the world changes (dimension switch, world change, server join),
        // so it never acts on a stale session from another world.
        if (mc.level != lastLevel)
        {
            lastLevel = mc.level;
            reelCooldown = 0;
            recastDelay = 0;
            pendingRecast = false;
            hadSessionActive = false;
        }

        // Screen guard: only act when no screen is open, or the Starcatcher fishing minigame is open.
        if (mc.screen != null && !(mc.screen instanceof FishingMinigameScreen))
            return;

        boolean inMinigame = mc.screen instanceof FishingMinigameScreen;
        FishingBobEntity starcatcherBobber = findLocalBobber(mc);
        FishingHook vanillaHook = findVanillaHook(mc);

        if (reelCooldown > 0) reelCooldown--;

        // 1. Auto-reel on bite: Starcatcher bobber STATE == 3, or vanilla hook DATA_BITING.
        boolean biting = (starcatcherBobber != null && isStarcatcherBiting(starcatcherBobber))
                || (vanillaHook != null && isVanillaBiting(vanillaHook));
        if (AutoFishSConfig.ENABLE_AUTO_REEL.get() && biting && reelCooldown <= 0)
        {
            KeyMapping.click(mc.options.keyUse.getKey());
            reelCooldown = REEL_COOLDOWN;
        }

        // 2. Detect when a fishing session (bobber / hook / minigame) fully ends.
        boolean sessionActive = inMinigame || starcatcherBobber != null || vanillaHook != null;
        if (hadSessionActive && !sessionActive)
        {
            pendingRecast = true;
            recastDelay = RECAST_DELAY;
        }
        hadSessionActive = sessionActive;

        // 3. Auto-cast after the previous session ends.
        if (AutoFishSConfig.ENABLE_AUTO_CAST.get()
                && holdsRod(mc)
                && pendingRecast
                && !inMinigame
                && starcatcherBobber == null
                && vanillaHook == null
                && reelCooldown <= 0)
        {
            if (recastDelay > 0)
            {
                recastDelay--;
            }
            else
            {
                KeyMapping.click(mc.options.keyUse.getKey());
                pendingRecast = false;
                reelCooldown = REEL_COOLDOWN;
            }
        }
    }

    private static FishingBobEntity findLocalBobber(Minecraft mc)
    {
        for (Entity e : mc.level.getEntitiesOfClass(FishingBobEntity.class, mc.player.getBoundingBox().inflate(64.0)))
        {
            if (e instanceof FishingBobEntity fbe && fbe.getOwner() == mc.player)
                return fbe;
        }
        return null;
    }

    private static boolean isStarcatcherBiting(FishingBobEntity bobber)
    {
        // FishHookState.BITING is synced as STATE == 3.
        return bobber.getEntityData().get(FishingBobEntity.STATE) == 3;
    }

    private static FishingHook findVanillaHook(Minecraft mc)
    {
        for (Entity e : mc.level.getEntitiesOfClass(FishingHook.class, mc.player.getBoundingBox().inflate(64.0)))
        {
            if (e instanceof FishingHook hook && hook.getOwner() == mc.player)
                return hook;
        }
        return null;
    }

    private static boolean isVanillaBiting(FishingHook hook)
    {
        EntityDataAccessor<Boolean> accessor = getDataBitingAccessor();
        return accessor != null && Boolean.TRUE.equals(hook.getEntityData().get(accessor));
    }

    /**
     * Locates the private static FishingHook.DATA_BITING accessor by finding the
     * static EntityDataAccessor whose serializer is BOOLEAN. Avoids hardcoding the
     * field name, which differs between dev (named) and production (SRG) mappings.
     */
    private static EntityDataAccessor<Boolean> getDataBitingAccessor()
    {
        if (vanillaInit) return dataBitingAccessor;
        vanillaInit = true;
        try
        {
            for (Field f : FishingHook.class.getDeclaredFields())
            {
                if (Modifier.isStatic(f.getModifiers()) && EntityDataAccessor.class.isAssignableFrom(f.getType()))
                {
                    f.setAccessible(true);
                    Object value = f.get(null);
                    if (value instanceof EntityDataAccessor<?> accessor
                            && accessor.getSerializer() == EntityDataSerializers.BOOLEAN)
                    {
                        dataBitingAccessor = (EntityDataAccessor<Boolean>) accessor;
                        break;
                    }
                }
            }
            if (dataBitingAccessor == null)
            {
                AutoFishS.LOGGER.warn("[AutoFishS] Vanilla FishingHook.DATA_BITING not found, vanilla auto-reel disabled");
            }
        }
        catch (ReflectiveOperationException e)
        {
            AutoFishS.LOGGER.warn("[AutoFishS] Could not resolve FishingHook.DATA_BITING, vanilla auto-reel disabled", e);
        }
        return dataBitingAccessor;
    }

    private static boolean holdsRod(Minecraft mc)
    {
        return mc.player.getMainHandItem().is(SCTags.RODS)
                || mc.player.getOffhandItem().is(SCTags.RODS)
                || mc.player.getMainHandItem().is(Items.FISHING_ROD)
                || mc.player.getOffhandItem().is(Items.FISHING_ROD);
    }
}
