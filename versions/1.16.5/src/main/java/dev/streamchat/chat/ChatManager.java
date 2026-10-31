package dev.streamchat.chat;

import org.apache.logging.log4j.LogManager;
import dev.streamchat.ChatConfig;
import dev.streamchat.ChatMessage;
import dev.streamchat.ChatPlatform;
import dev.streamchat.ChannelNames;
import dev.streamchat.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.Color;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import org.apache.logging.log4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

public final class ChatManager {
    private static final Logger LOGGER = LogManager.getLogger();
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
        if (ChatConfig.enabled && !ChatConfig.channel.trim().isEmpty()) {
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
            if (selected == ChatPlatform.KICK) {
                client = new KickChatClient(channel, LISTENER);
            } else if (selected == ChatPlatform.TIKTOK) {
                client = new TikTokBridgeClient(ChatConfig.tiktokBridgeUrl, channel, LISTENER);
            } else {
                client = new TwitchChatClient(channel, LISTENER);
            }
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
        if (body.trim().isEmpty()) {
            return;
        }

        deliver(MessageFormatter.format(message), false, false);
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
        if (status == null || status.trim().isEmpty() || status.equals(lastStatus)) {
            return;
        }
        lastStatus = status;
        state = status;
        if (ChatConfig.announceStatus) {
            announceRaw(status, false);
        }
    }

    private static void handleError(String error) {
        if (error == null || error.trim().isEmpty()) {
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

    private static void addChat(ITextComponent component) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            return;
        }
        minecraft.gui.getChat().addMessage(component);
    }

    private static void deliver(ITextComponent component, boolean system, boolean error) {
        if (ChatConfig.overlayEnabled) {
            OverlayChat.add(component, system, error);
        }
        if (!ChatConfig.overlayEnabled || ChatConfig.overlayMirrorVanilla) {
            addChat(component);
        }
    }

    private static void onClient(Runnable task) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.execute(task);
        }
    }

    public static void announceRaw(String text, boolean error) {
        int color = error ? ChatConfig.overlayErrorColor : ChatConfig.overlaySystemColor;
        String prefix = ChatConfig.overlaySystemPrefix == null ? "" : ChatConfig.overlaySystemPrefix;
        IFormattableTextComponent line = new StringTextComponent(prefix)
                .withStyle(style -> style.withColor(Color.fromRgb(color)));
        line.append(new StringTextComponent(text)
                .withStyle(style -> style.withColor(Color.fromRgb(color))));
        onClient(() -> deliver(line, true, error));
    }

    public static void test() {
        display(new ChatMessage(platform, "Simplify Chat", "Mensaje de prueba: el overlay se ve correctamente.", "#55FF55"));
    }

    public static String statusText() {
        String shownChannel = channel.trim().isEmpty() ? "(sin configurar)" : displayChannel();
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
