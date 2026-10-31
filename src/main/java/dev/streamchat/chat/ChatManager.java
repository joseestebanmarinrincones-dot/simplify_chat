package dev.streamchat.chat;

import com.mojang.logging.LogUtils;
import dev.streamchat.ChatConfig;
import dev.streamchat.ChatMessage;
import dev.streamchat.ChatPlatform;
import dev.streamchat.ChannelNames;
import dev.streamchat.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Orquesta la conexion activa y pinta los mensajes en el chat del cliente.
 * Todos los metodos que tocan Minecraft se ejecutan en el hilo del juego.
 */
public final class ChatManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Deque<Long> MESSAGE_TIMES = new ArrayDeque<>();

    private static volatile ChatClient client;
    private static ChatPlatform platform = ChatPlatform.TWITCH;
    private static String channel = "";
    private static String state = "desconectado";
    private static String lastStatus = "";
    private static String lastError = "";
    private static long lastErrorAt = 0L;
    private static int autoConnectDelay = -1;

    private static final ChatListener LISTENER = new ChatListener() {
        @Override
        public void onMessage(ChatMessage message) {
            display(message);
        }

        @Override
        public void onStatus(String status) {
            onClient(() -> handleStatus(status));
        }

        @Override
        public void onError(String error) {
            onClient(() -> handleError(error));
        }
    };

    private ChatManager() {
    }

    public static void onJoin() {
        if (ChatConfig.enabled && !ChatConfig.channel.isBlank()) {
            // Dar un margen para que el chat del cliente termine de inicializarse.
            autoConnectDelay = 60;
        }
    }

    public static void onLeave() {
        autoConnectDelay = -1;
        disconnect(false);
    }

    public static void tick() {
        OverlayChat.prune();
        if (autoConnectDelay > 0) {
            autoConnectDelay--;
            if (autoConnectDelay == 0) {
                autoConnectDelay = -1;
                if (ChatConfig.enabled) {
                    connectConfigured(false);
                }
            }
        }
    }

    public static void connectConfigured(boolean announceErrors) {
        ChatPlatform selected = ChatPlatform.byIdOrDefault(ChatConfig.platform);
        String normalized = ChannelNames.normalize(selected, ChatConfig.channel);
        if (normalized.isEmpty()) {
            if (announceErrors) {
                announceRaw("No hay ningun canal configurado. Usa /streamchat connect <twitch|kick|tiktok> <canal>.", true);
            }
            return;
        }
        connect(selected, normalized, false);
    }

    public static void connect(ChatPlatform selected, String rawChannel, boolean save) {
        String normalized = ChannelNames.normalize(selected, rawChannel);
        if (normalized.isEmpty()) {
            announceRaw("El canal esta vacio.", true);
            return;
        }

        disconnect(false);

        platform = selected;
        channel = normalized;
        state = "conectando";
        lastStatus = "";
        lastError = "";
        lastErrorAt = 0L;

        if (save) {
            ChatConfig.setPlatform(selected.id());
            ChatConfig.setChannel(channel);
        }

        announceRaw("Conectando a " + selected.displayName() + ": " + displayChannel(), false);

        try {
            client = switch (selected) {
                case TWITCH -> new TwitchChatClient(channel, LISTENER);
                case KICK -> new KickChatClient(channel, LISTENER);
                case TIKTOK -> new TikTokBridgeClient(ChatConfig.tiktokBridgeUrl, channel, LISTENER);
            };
            client.start();
        } catch (Exception exception) {
            client = null;
            state = "error";
            announceRaw("No se pudo iniciar la conexion: " + exception.getMessage(), true);
        }
    }

    public static void disconnect(boolean announce) {
        ChatClient old = client;
        client = null;
        if (old != null) {
            try {
                old.stop();
            } catch (Exception exception) {
                LOGGER.warn("Error cerrando el cliente de chat", exception);
            }
        }
        state = "desconectado";
        lastStatus = "";
        if (announce) {
            announceRaw("Desconectado.", false);
        }
    }

    public static void display(ChatMessage message) {
        if (message == null) {
            return;
        }
        onClient(() -> displayNow(message));
    }

    private static void displayNow(ChatMessage message) {
        if (message == null) {
            return;
        }
        String rawMessage = message.message() == null ? "" : message.message();
        String rawUser = message.user() == null ? "" : message.user();

        if (ChatConfig.ignoreCommands && rawMessage.startsWith("!")) {
            return;
        }
        if (ChatConfig.blockedUsers.contains(rawUser.toLowerCase(Locale.ROOT))) {
            return;
        }
        if (!allowMessage()) {
            return;
        }

        String body = TextUtil.cleanMessage(rawMessage, ChatConfig.maxMessageLength);
        if (body.isBlank()) {
            return;
        }

        MutableComponent line = MessageFormatter.format(message);
        deliver(line, false, false);
    }

    private static boolean allowMessage() {
        int limit = ChatConfig.maxMessagesPerSecond;
        if (limit <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        while (!MESSAGE_TIMES.isEmpty() && now - MESSAGE_TIMES.peekFirst() >= 1000L) {
            MESSAGE_TIMES.pollFirst();
        }
        if (MESSAGE_TIMES.size() >= limit) {
            return false;
        }
        MESSAGE_TIMES.addLast(now);
        return true;
    }

    private static void handleStatus(String status) {
        if (status == null || status.isBlank() || status.equals(lastStatus)) {
            return;
        }
        lastStatus = status;
        state = status;
        if (ChatConfig.announceStatus) {
            announceRaw(status, false);
        }
    }

    private static void handleError(String error) {
        if (error == null || error.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (error.equals(lastError) && now - lastErrorAt < 10_000L) {
            return;
        }
        lastError = error;
        lastErrorAt = now;
        state = "error";
        if (ChatConfig.announceStatus) {
            announceRaw(error, true);
        }
    }

    private static void addChat(Component component) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null || minecraft.gui.hud == null) {
            return;
        }
        minecraft.gui.hud.getChat().addClientSystemMessage(component);
    }

    private static void deliver(Component component, boolean system, boolean error) {
        if (ChatConfig.overlayEnabled) {
            OverlayChat.add(component, system, error);
        }
        if (!ChatConfig.overlayEnabled || ChatConfig.overlayMirrorVanilla) {
            addChat(component);
        }
    }

    private static void onClient(Runnable task) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(task);
    }

    public static void announceRaw(String text, boolean error) {
        int color = error ? ChatConfig.overlayErrorColor : ChatConfig.overlaySystemColor;
        String prefix = ChatConfig.overlaySystemPrefix == null ? "" : ChatConfig.overlaySystemPrefix;
        MutableComponent line = Component.literal(prefix)
                .withStyle(style -> style.withColor(TextColor.fromRgb(color)));
        line.append(Component.literal(text)
                .withStyle(style -> style.withColor(TextColor.fromRgb(color))));
        onClient(() -> deliver(line, true, error));
    }

    public static void test() {
        display(new ChatMessage(platform, "StreamChat", "Mensaje de prueba: el chat se ve correctamente.", "#55FF55"));
    }

    public static String statusText() {
        String shownChannel = channel.isBlank() ? "(sin configurar)" : displayChannel();
        String bridge = platform == ChatPlatform.TIKTOK ? " | Puente: " + ChatConfig.tiktokBridgeUrl : "";
        return "Plataforma: " + platform.displayName() + " | Canal: " + shownChannel
                + " | Estado: " + state + bridge;
    }

    public static ChatPlatform platform() {
        return platform;
    }

    public static String channel() {
        return channel;
    }

    private static String displayChannel() {
        return platform == ChatPlatform.TIKTOK ? "@" + channel : channel;
    }
}
