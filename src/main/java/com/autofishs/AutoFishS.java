package com.autofishs;

import com.autofishs.client.AutoFishSConfigScreen;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
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

    public AutoFishS(ModContainer modContainer)
    {
        // ConfigTracker.registerConfig is used (instead of ModContainer#registerConfig) because it
        // returns the ModConfig instance, which AutoFishSConfig needs in order to persist changes.
        AutoFishSConfig.attach(ConfigTracker.INSTANCE.registerConfig(
                ModConfig.Type.CLIENT, AutoFishSConfig.SPEC, modContainer));

        // Guarded by an explicit dist check so the client-only Client class is never
        // loaded on the dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            Client.init(modContainer);
        }
    }

    public static class Client
    {
        public static void init(ModContainer modContainer)
        {
            // Registers a config screen so ESC -> Mods -> AutoFishS -> Config opens AutoFishSConfigScreen.
            modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                    (IConfigScreenFactory) (minecraft, parent) -> new AutoFishSConfigScreen(parent));

            LOGGER.info("[AutoFishS] client init complete");
        }
    }
}
