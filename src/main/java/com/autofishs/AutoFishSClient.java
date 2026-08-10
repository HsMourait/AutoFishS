package com.autofishs;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side MOD-bus setup for AutoFishS.
 */
@Mod.EventBusSubscriber(modid = AutoFishS.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
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
