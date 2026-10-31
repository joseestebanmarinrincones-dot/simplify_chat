package dev.streamchat;

import dev.streamchat.chat.ChatManager;
import dev.streamchat.chat.ChatOverlay;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

final class ClientBootstrap {
    private ClientBootstrap() {
    }

    static void init(FMLJavaModLoadingContext context) {
        BusGroup modBus = context.getModBusGroup();
        context.registerConfig(ModConfig.Type.CLIENT, ChatConfig.SPEC);

        ModConfigEvent.Loading.getBus(modBus).addListener(event -> {
            if (event.getConfig().getSpec() == ChatConfig.SPEC) {
                ChatConfig.load();
            }
        });
        ModConfigEvent.Reloading.getBus(modBus).addListener(event -> {
            if (event.getConfig().getSpec() == ChatConfig.SPEC) {
                ChatConfig.load();
            }
        });
        RegisterKeyMappingsEvent.getBus(modBus).addListener(event -> event.register(StreamChatKeys.OPEN_CONFIG));

        RegisterClientCommandsEvent.BUS.addListener(StreamChatCommands::register);
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(event -> ChatManager.onJoin());
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> ChatManager.onLeave());
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> {
            ChatManager.tick();
            StreamChatKeys.tick();
        });
        AddGuiOverlayLayersEvent.getBus(modBus).addListener(event -> ChatOverlay.register(event.getLayeredDraw()));
    }
}
