package com.autofishs.event;

import com.autofishs.AutoFishS;
import com.autofishs.AutoFishSKeybindings;
import com.autofishs.client.AutoFishSConfigScreen;
import com.autofishs.client.AutoFishingHandler;
import com.autofishs.client.BlacklistScreen;
import com.autofishs.client.FishingOverhaulAutoHitHandler;
import com.autofishs.client.MinigameAutoHitHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AutoFishS.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEvents
{
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
            return;

        AutoFishingHandler.tick();
        MinigameAutoHitHandler.tick();
        FishingOverhaulAutoHitHandler.tick();
        handleKeys();
    }

    private static void handleKeys()
    {
        Minecraft mc = Minecraft.getInstance();

        if (AutoFishSKeybindings.OPEN_CONFIG.consumeClick())
        {
            mc.setScreen(new AutoFishSConfigScreen(null));
        }

        if (AutoFishSKeybindings.OPEN_BLACKLIST.consumeClick())
        {
            mc.setScreen(new BlacklistScreen(null));
        }
    }
}
