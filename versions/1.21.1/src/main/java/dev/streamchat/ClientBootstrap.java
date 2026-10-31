package dev.streamchat;

import dev.streamchat.chat.ChatManager;
import dev.streamchat.chat.ChatOverlay;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

final class ClientBootstrap {
    private ClientBootstrap() {
    }

    static void init(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        context.registerConfig(ModConfig.Type.CLIENT, ChatConfig.SPEC);

        modBus.addListener((ModConfigEvent.Loading event) -> {
            if (event.getConfig().getSpec() == ChatConfig.SPEC) {
                ChatConfig.load();
            }
        });
        modBus.addListener((ModConfigEvent.Reloading event) -> {
            if (event.getConfig().getSpec() == ChatConfig.SPEC) {
                ChatConfig.load();
            }
        });
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(StreamChatKeys.OPEN_CONFIG));

        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> StreamChatCommands.register(event));
        // 1.21.1 usa el sistema de capas del HUD\n        modBus.addListener((AddGuiOverlayLayersEvent event) -> ChatOverlay.register(event.getLayeredDraw()));
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> ChatManager.onJoin());
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ChatManager.onLeave());
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                ChatManager.tick();
                StreamChatKeys.tick();
            }
        });
    }
}
