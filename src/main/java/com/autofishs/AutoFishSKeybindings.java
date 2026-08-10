package com.autofishs;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class AutoFishSKeybindings
{
    public static final String CATEGORY = "key.category.autofishs.autofishs";

    public static final KeyMapping OPEN_CONFIG =
            new KeyMapping("key.autofishs.open_config", GLFW.GLFW_KEY_F8, CATEGORY);

    public static final KeyMapping OPEN_BLACKLIST =
            new KeyMapping("key.autofishs.open_blacklist", GLFW.GLFW_KEY_F7, CATEGORY);

    public static KeyMapping[] all()
    {
        return new KeyMapping[] { OPEN_CONFIG, OPEN_BLACKLIST };
    }
}
