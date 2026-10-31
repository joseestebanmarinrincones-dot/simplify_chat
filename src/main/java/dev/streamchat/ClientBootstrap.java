package dev.streamchat;

import dev.streamchat.chat.ChatManager;
import dev.streamchat.chat.ChatOverlay;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

/**
 * Registra todo lo que solo existe en el cliente. Esta clase se carga
 * unicamente cuando FMLEnvironment.dist == CLIENT.
 */
final class ClientBootstrap {
    private ClientBootstrap() {
    }

    static void init(FMLJavaModLoadingContext context) {
        BusGroup modBus = context.getModBusGroup();

        // El archivo se llama config/streamchat-client.toml
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

        RegisterClientCommandsEvent.BUS.addListener(StreamChatCommands::register);
        AddGuiOverlayLayersEvent.BUS.addListener(event -> ChatOverlay.register(event.getLayeredDraw()));
        StreamChatKeys.register();
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(event -> ChatManager.onJoin());
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> ChatManager.onLeave());
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> {
            ChatManager.tick();
            StreamChatKeys.tick();
        });
    }
}
