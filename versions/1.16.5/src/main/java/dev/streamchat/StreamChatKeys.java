package dev.streamchat;

import dev.streamchat.gui.OverlayConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import org.lwjgl.glfw.GLFW;

public final class StreamChatKeys {
    public static final KeyBinding OPEN_CONFIG = new KeyBinding(
            "key.streamchat.config",
            InputMappings.Type.KEYSYM,
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
