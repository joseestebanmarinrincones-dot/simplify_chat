package dev.streamchat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.streamchat.gui.OverlayConfigScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class StreamChatKeys {
    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.streamchat.config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            "key.categories.streamchat"
    );

    private StreamChatKeys() {
    }

    public static void tick() {
        if (OPEN_CONFIG.consumeClick()) {
            openPanel();
        }
    }

    public static void openPanel() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null
                && !(minecraft.screen instanceof OverlayConfigScreen)) {
            minecraft.setScreen(new OverlayConfigScreen());
        }
    }
}
