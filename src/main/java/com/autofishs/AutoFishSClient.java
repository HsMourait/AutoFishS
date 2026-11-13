package com.autofishs;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * Client-side MOD-bus setup for AutoFishS.
 */
@EventBusSubscriber(modid = AutoFishS.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class AutoFishSClient
{
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event)
    {
        for (KeyMapping key : AutoFishSKeybindings.all())
        {
            event.register(key);
        }
    }
}
