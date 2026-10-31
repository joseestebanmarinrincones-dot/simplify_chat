package dev.streamchat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.streamchat.gui.OverlayConfigScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;

public final class StreamChatKeys {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(StreamChatMod.MODID, "keys"));

    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.streamchat.config",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_F7,
            CATEGORY
    );

    private StreamChatKeys() {
    }

    public static void register() {
        RegisterKeyMappingsEvent.BUS.addListener(event -> event.register(OPEN_CONFIG));
    }

    public static void tick() {
        if (OPEN_CONFIG.consumeClick()) {
            openPanel();
        }
    }

    public static void openPanel() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null && minecraft.gui != null
                && !(minecraft.gui.screen() instanceof OverlayConfigScreen)) {
            minecraft.gui.setScreen(new OverlayConfigScreen());
        }
    }
}
