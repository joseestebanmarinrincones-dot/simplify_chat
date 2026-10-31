package dev.streamchat.chat;

import dev.streamchat.ChatConfig;
import dev.streamchat.ChatPlatform;
import dev.streamchat.ChatSource;
import dev.streamchat.ChannelNames;
import dev.streamchat.StreamChatKeys;
import dev.streamchat.emote.EmoteManager;
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
        } else if ("add".equals(command)) {
            if (parts.length < 3) {
                ChatManager.announceRaw("Uso: /streamchat add <twitch|kick|tiktok> <canal>", true);
                return;
            }
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
            ChatManager.announceRaw("Chat agregado: " + platform.displayName() + " " + channel, false);
        } else if ("remove".equals(command)) {
            if (parts.length < 2) {
                ChatManager.announceRaw("Uso: /streamchat remove <plataforma> [canal]", true);
                return;
            }
            ChatPlatform platform = ChatPlatform.byId(parts[1]);
            if (platform == null) {
                ChatManager.announceRaw("Plataforma desconocida. Usa twitch, kick o tiktok.", true);
                return;
            }
            String channel = parts.length >= 3 ? join(parts, 2) : "";
            if (!ChatConfig.removeSource(platform, channel)) {
                ChatManager.announceRaw("No habia ningun chat configurado para " + platform.displayName() + ".", true);
                return;
            }
            ChatManager.connectConfigured(false);
            ChatManager.announceRaw("Chat eliminado. Quedan " + ChatConfig.effectiveSources().size() + ".", false);
        } else if ("disconnect".equals(command)) {
            ChatManager.disconnect(true);
        } else if ("status".equals(command) || "sources".equals(command)) {
            ChatManager.announceRaw(ChatManager.statusText(), false);
        } else if ("panel".equals(command)) {
            StreamChatKeys.openPanel();
        } else if ("test".equals(command)) {
            ChatManager.test();
        } else if ("reload".equals(command)) {
            ChatConfig.load();
            EmoteManager.reload();
            ChatManager.announceRaw("Configuracion recargada.", false);
        } else if ("emotes".equals(command)) {
            String action = parts.length >= 2 ? parts[1].toLowerCase() : "";
            if ("on".equals(action)) {
                ChatConfig.setEmotesEnabled(true);
                EmoteManager.reload();
                ChatManager.announceRaw("Emotes activados.", false);
            } else if ("off".equals(action)) {
                ChatConfig.setEmotesEnabled(false);
                EmoteManager.reload();
                ChatManager.announceRaw("Emotes desactivados.", false);
            } else if ("animated".equals(action) && parts.length >= 3) {
                boolean enabled = "on".equals(parts[2].toLowerCase());
                ChatConfig.setEmotesAnimated(enabled);
                EmoteManager.reload();
                ChatManager.announceRaw(enabled ? "Emotes animados activados." : "Emotes animados desactivados.", false);
            } else if ("size".equals(action) && parts.length >= 3) {
                try {
                    ChatConfig.setEmotesSize(Integer.parseInt(parts[2]));
                    ChatManager.announceRaw("Tamano de emote guardado: " + ChatConfig.emotesSize, false);
                } catch (NumberFormatException exception) {
                    ChatManager.announceRaw("Tamano invalido. Usa 8-28.", true);
                }
            } else if ("reload".equals(action)) {
                EmoteManager.reload();
                ChatManager.announceRaw("Recargando emotes...", false);
            } else {
                ChatManager.announceRaw("Emotes: " + (ChatConfig.emotesEnabled ? "activados" : "desactivados")
                        + " | Animados: " + (ChatConfig.emotesAnimated ? "si" : "no")
                        + " | Tamano: " + ChatConfig.emotesSize, false);
            }
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
        ChatManager.announceRaw("Simplify Chat 1.1.0 - comandos: add, remove, connect, sources, status, emotes, disconnect, overlay, test, panel, reload, bridge.", false);
    }
}
