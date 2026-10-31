package dev.streamchat;

import dev.streamchat.chat.ChatManager;
import dev.streamchat.chat.ChatOverlay;
import dev.streamchat.chat.LegacyChatCommands;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

final class ClientBootstrap {
    private ClientBootstrap() {
    }

    static void init() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ChatConfig.SPEC);

        FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLClientSetupEvent event) ->
                ClientRegistry.registerKeyBinding(StreamChatKeys.OPEN_CONFIG));
        FMLJavaModLoadingContext.get().getModEventBus().addListener((ModConfig.ModConfigEvent event) -> {
            if (event.getConfig().getSpec() == ChatConfig.SPEC) {
                ChatConfig.load();
            }
        });

        MinecraftForge.EVENT_BUS.addListener((RenderGameOverlayEvent.Post event) -> {
            if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
                ChatOverlay.render(event.getMatrixStack());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ClientChatEvent event) -> LegacyChatCommands.handle(event));
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent event) -> {
            if (event.getPlayer() != null) {
                ChatManager.onJoin();
            } else {
                ChatManager.onLeave();
            }
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                ChatManager.tick();
                StreamChatKeys.tick();
            }
        });
    }
}
