package com.autofishs.event;

import com.autofishs.AutoFishS;
import com.autofishs.AutoFishSKeybindings;
import com.autofishs.client.AutoFishSConfigScreen;
import com.autofishs.client.AutoFishingHandler;
import com.autofishs.client.BlacklistScreen;
import com.autofishs.client.FishingOverhaulAutoHitHandler;
import com.autofishs.client.MinigameAutoHitHandler;
import com.autofishs.client.MinigameStatusOverlay;
import com.wdiscute.starcatcher.minigame.FishingMinigameScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
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

    /**
     * Draws the assist read-out on top of the Starcatcher minigame. Render.Post fires after the
     * screen's own render pass has finished, so this lands above the minigame's own drawing
     * without any mixin, pose or depth handling.
     */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event)
    {
        if (event.getScreen() instanceof FishingMinigameScreen screen)
            MinigameStatusOverlay.render(event.getGuiGraphics(), screen);
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
