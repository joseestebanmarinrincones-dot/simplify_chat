package dev.streamchat.chat;

import com.mojang.logging.LogUtils;
import dev.streamchat.ChatConfig;
import dev.streamchat.ChatMessage;
import dev.streamchat.ChatPlatform;
import dev.streamchat.ChatSource;
import dev.streamchat.ChannelNames;
import dev.streamchat.TextUtil;
import dev.streamchat.emote.EmoteManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orquesta todas las conexiones activas (Twitch, Kick y TikTok a la vez) y
 * pinta los mensajes en el overlay. Todos los metodos que tocan Minecraft se
 * ejecutan en el hilo del juego.
 */
public final class ChatManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Deque<Long> MESSAGE_TIMES = new ArrayDeque<>();
    private static final Map<String, Connection> CONNECTIONS = new LinkedHashMap<>();

    private static ChatPlatform platform = ChatPlatform.TWITCH;
    private static String channel = "";
    private static int autoConnectDelay = -1;

    private ChatManager() {
    }

    public static void onJoin() {
        if (ChatConfig.enabled && !ChatConfig.effectiveSources().isEmpty()) {
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
        EmoteManager.tick();
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
        List<ChatSource> sources = ChatConfig.effectiveSources();
        if (sources.isEmpty()) {
            if (announceErrors) {
                announceRaw("No hay ningun chat configurado. Usa /streamchat add <twitch|kick|tiktok> <canal>.", true);
            }
            return;
        }

        closeConnections();
        for (ChatSource source : sources) {
            start(source);
        }
        EmoteManager.configure(sources);

        String summary = String.join(", ", sources.stream().map(ChatSource::display).toList());
        announceRaw("Conectando " + sources.size() + " chat" + (sources.size() == 1 ? "" : "s") + ": " + summary, false);
    }

    public static void connect(ChatPlatform selected, String rawChannel, boolean save) {
        String normalized = ChannelNames.normalize(selected, rawChannel);
        if (normalized.isEmpty()) {
            announceRaw("El canal esta vacio.", true);
            return;
        }

        ChatSource source = new ChatSource(selected, normalized);
        if (save) {
            ChatConfig.addSource(source);
            platform = selected;
            channel = normalized;
            connectConfigured(false);
        } else {
            start(source);
            EmoteManager.configure(ChatConfig.effectiveSources());
            announceRaw("Conectando a " + source.display(), false);
        }
    }

    private static void start(ChatSource source) {
        String key = source.key();
        Connection connection = new Connection(source);
        CONNECTIONS.put(key, connection);

        try {
            ChatListener listener = new ChatListener() {
                @Override
                public void onMessage(ChatMessage message) {
                    display(message);
                }

                @Override
                public void onStatus(String status) {
                    onClient(() -> handleStatus(connection, status));
                }

                @Override
                public void onError(String error) {
                    onClient(() -> handleError(connection, error));
                }
            };

            connection.client = switch (source.platform()) {
                case TWITCH -> new TwitchChatClient(source.channel(), listener);
                case KICK -> new KickChatClient(source.channel(), listener);
                case TIKTOK -> new TikTokBridgeClient(ChatConfig.tiktokBridgeUrl, source.channel(), listener);
            };
            connection.client.start();
        } catch (Exception exception) {
            connection.client = null;
            connection.state = "error";
            announceRaw("No se pudo iniciar " + source.display() + ": " + exception.getMessage(), true);
        }
    }

    public static void disconnect(boolean announce) {
        closeConnections();
        if (announce) {
            announceRaw("Desconectado.", false);
        }
    }

    private static void closeConnections() {
        for (Connection connection : CONNECTIONS.values()) {
            if (connection.client != null) {
                try {
                    connection.client.stop();
                } catch (Exception exception) {
                    LOGGER.warn("Error cerrando el cliente de chat de {}", connection.source.display(), exception);
                }
            }
        }
        CONNECTIONS.clear();
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

        ChatMessage cleaned = new ChatMessage(message.platform(), message.user(), body, message.colorHex());
        if (ChatConfig.overlayEnabled) {
            OverlayChat.add(cleaned, false, false);
        }
        if (!ChatConfig.overlayEnabled || ChatConfig.overlayMirrorVanilla) {
            addChat(MessageFormatter.format(cleaned));
        }
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

    private static void handleStatus(Connection connection, String status) {
        if (status == null || status.isBlank() || status.equals(connection.lastStatus)) {
            return;
        }
        connection.lastStatus = status;
        connection.state = status;
        if (ChatConfig.announceStatus) {
            announceRaw(connection.source.platform().displayName() + " (" + connection.source.displayChannel() + "): " + status, false);
        }
    }

    private static void handleError(Connection connection, String error) {
        if (error == null || error.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (error.equals(connection.lastError) && now - connection.lastErrorAt < 10_000L) {
            return;
        }
        connection.lastError = error;
        connection.lastErrorAt = now;
        connection.state = "error";
        if (ChatConfig.announceStatus) {
            announceRaw(connection.source.platform().displayName() + " (" + connection.source.displayChannel() + "): " + error, true);
        }
    }

    private static void addChat(Component component) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gui == null) {
            return;
        }
        minecraft.gui.getChat().addMessage(component);
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
        String line = prefix + text;
        onClient(() -> {
            if (ChatConfig.overlayEnabled) {
                OverlayChat.addSystem(line, color, error);
            }
            if (!ChatConfig.overlayEnabled || ChatConfig.overlayMirrorVanilla) {
                MutableComponent component = Component.literal(line)
                        .withStyle(style -> style.withColor(TextColor.fromRgb(color)));
                addChat(component);
            }
        });
    }

    public static void test() {
        List<ChatSource> sources = ChatConfig.effectiveSources();
        ChatSource source = sources.isEmpty() ? new ChatSource(platform, channel) : sources.get(0);
        display(new ChatMessage(source.platform(), "Simplify", "Mensaje de prueba :tf: el chat y los emotes se ven correctamente.", "#55FF55"));
    }

    public static String statusText() {
        List<ChatSource> configured = ChatConfig.effectiveSources();
        if (configured.isEmpty() && CONNECTIONS.isEmpty()) {
            return "No hay chats configurados. Usa /streamchat add <twitch|kick|tiktok> <canal>.";
        }
        StringBuilder builder = new StringBuilder();
        if (CONNECTIONS.isEmpty()) {
            builder.append("Chats configurados: ").append(configured.size());
        } else {
            builder.append("Chats activos: ").append(CONNECTIONS.size());
        }
        for (Connection connection : CONNECTIONS.values()) {
            builder.append('\n').append("- ").append(connection.source.display())
                    .append(" | ").append(connection.state);
        }
        if (CONNECTIONS.isEmpty()) {
            for (ChatSource source : configured) {
                builder.append('\n').append("- ").append(source.display());
            }
        }
        return builder.toString();
    }

    public static ChatPlatform platform() {
        List<ChatSource> sources = ChatConfig.effectiveSources();
        return sources.isEmpty() ? platform : sources.get(0).platform();
    }

    public static String channel() {
        List<ChatSource> sources = ChatConfig.effectiveSources();
        return sources.isEmpty() ? channel : sources.get(0).channel();
    }

    public static int connectionCount() {
        return CONNECTIONS.size();
    }

    private static final class Connection {
        private final ChatSource source;
        private ChatClient client;
        private String state = "conectando";
        private String lastStatus = "";
        private String lastError = "";
        private long lastErrorAt = 0L;

        private Connection(ChatSource source) {
            this.source = source;
        }
    }
}
