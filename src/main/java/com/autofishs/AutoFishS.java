package com.autofishs;

import com.autofishs.client.AutoFishSConfigScreen;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * AutoFishS — a purely client-side starcatcher addon.
 * <p>
 * This mod is client-only: it registers nothing server-side, sends no custom
 * packets, and exposes no content on the server. All logic hooks into the
 * client-side starcatcher fishing minigame screen.
 */
@Mod(AutoFishS.MOD_ID)
public class AutoFishS
{
    public static final String MOD_ID = "autofishs";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AutoFishS()
    {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, AutoFishSConfig.SPEC);

        // Guarded by an explicit dist check (instead of DistExecutor.safeRunWhenOn) so the
        // client-only Client class is never loaded on the dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            Client.init();
        }
    }

    public static class Client
    {
        public static void init()
        {
            // Registers a config screen so ESC -> Mods -> AutoFishS -> Config opens AutoFishSConfigScreen.
            ModLoadingContext.get().registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (modsScreen) -> new AutoFishSConfigScreen(modsScreen)));

            LOGGER.info("[AutoFishS] client init complete");
        }
    }
}
