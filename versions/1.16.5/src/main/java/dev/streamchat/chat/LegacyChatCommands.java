package dev.streamchat.chat;

import dev.streamchat.ChatConfig;
import dev.streamchat.ChatPlatform;
import dev.streamchat.ChannelNames;
import dev.streamchat.StreamChatKeys;
import net.minecraftforge.client.event.ClientChatEvent;

public final class LegacyChatCommands {
    private LegacyChatCommands() {
    }

    public static void handle(ClientChatEvent event) {
        String message = event.getMessage();
        if (message == null || !message.startsWith("/streamchat")) {
            return;
        }
        event.setCanceled(true);

        String rest = message.substring("/streamchat".length()).trim();
        if (rest.isEmpty()) {
            help();
            return;
        }

        String[] parts = rest.split("\\s+");
        String command = parts[0].toLowerCase();

        if ("help".equals(command)) {
            help();
        } else if ("connect".equals(command)) {
            if (parts.length >= 3) {
                ChatPlatform platform = ChatPlatform.byId(parts[1]);
                if (platform == null) {
                    ChatManager.announceRaw("Plataforma desconocida. Usa twitch, kick o tiktok.", true);
                    return;
                }
                String channel = ChannelNames.normalize(platform, join(parts, 2));
                if (channel.isEmpty()) {
                    ChatManager.announceRaw("El canal esta vacio.", true);
                    return;
                }
                ChatManager.connect(platform, channel, true);
            } else {
                ChatManager.connectConfigured(true);
            }
        } else if ("disconnect".equals(command)) {
            ChatManager.disconnect(true);
        } else if ("status".equals(command)) {
            ChatManager.announceRaw(ChatManager.statusText(), false);
        } else if ("panel".equals(command)) {
            StreamChatKeys.openPanel();
        } else if ("test".equals(command)) {
            ChatManager.test();
        } else if ("reload".equals(command)) {
            ChatConfig.load();
            ChatManager.announceRaw("Configuracion recargada.", false);
        } else if ("platform".equals(command) && parts.length >= 2) {
            ChatPlatform platform = ChatPlatform.byId(parts[1]);
            if (platform == null) {
                ChatManager.announceRaw("Plataforma desconocida. Usa twitch, kick o tiktok.", true);
            } else {
                ChatConfig.setPlatform(platform.id());
                ChatManager.announceRaw("Plataforma guardada: " + platform.displayName(), false);
            }
        } else if ("channel".equals(command) && parts.length >= 2) {
            ChatPlatform platform = ChatPlatform.byIdOrDefault(ChatConfig.platform);
            String channel = ChannelNames.normalize(platform, join(parts, 1));
            if (channel.isEmpty()) {
                ChatManager.announceRaw("El canal esta vacio.", true);
            } else {
                ChatConfig.setChannel(channel);
                ChatManager.announceRaw("Canal guardado: " + channel, false);
            }
        } else if ("overlay".equals(command)) {
            String action = parts.length >= 2 ? parts[1].toLowerCase() : "";
            if ("on".equals(action)) {
                ChatConfig.setOverlayEnabled(true);
                ChatManager.announceRaw("Overlay activado.", false);
            } else if ("off".equals(action)) {
                ChatConfig.setOverlayEnabled(false);
                OverlayChat.clear();
                ChatManager.announceRaw("Overlay desactivado.", false);
            } else if ("clear".equals(action)) {
                OverlayChat.clear();
                ChatManager.announceRaw("Overlay limpiado.", false);
            } else {
                ChatConfig.setOverlayEnabled(!ChatConfig.overlayEnabled);
                ChatManager.announceRaw(ChatConfig.overlayEnabled ? "Overlay activado." : "Overlay desactivado.", false);
            }
        } else if ("bridge".equals(command)) {
            ChatManager.announceRaw("TikTok usa el puente local. Ejecuta tiktok-bridge/iniciar-puente.bat y luego /streamchat connect tiktok TU_USUARIO.", false);
        } else {
            ChatManager.announceRaw("Comando desconocido. Usa /streamchat help.", true);
        }
    }

    private static String join(String[] parts, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < parts.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(parts[i]);
        }
        return builder.toString();
    }

    private static void help() {
        ChatManager.announceRaw("Simplify Chat 1.0.0 - comandos: connect, disconnect, status, platform, channel, overlay, test, panel, reload, bridge.", false);
    }
}
