package dev.streamchat.chat;

import dev.streamchat.ChatMessage;
import dev.streamchat.ChatPlatform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Cliente de solo lectura para el IRC de Twitch. Se conecta de forma anonima
 * con un nick justinfan, igual que la mayoria de overlays de chat.
 */
public final class TwitchChatClient implements ChatClient {
    private static final URI TWITCH_URI = URI.create("wss://irc-ws.chat.twitch.tv:443");
    private static final String USER_AGENT = "StreamChat Minecraft Mod/1.0";

    private final String channel;
    private final ChatListener listener;
    private final String nickname;
    private final ScheduledExecutorService scheduler;
    private final HttpClient http;
    private final StringBuilder incoming = new StringBuilder();

    private volatile WebSocket socket;
    private volatile boolean running;
    private volatile boolean retryScheduled;
    private volatile int attempts;

    public TwitchChatClient(String channel, ChatListener listener) {
        this.channel = channel;
        this.listener = listener;
        this.nickname = "justinfan" + ThreadLocalRandom.current().nextInt(10000, 99999);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "StreamChat-Twitch");
            thread.setDaemon(true);
            return thread;
        });
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public void start() {
        running = true;
        scheduler.execute(this::open);
    }

    private void open() {
        if (!running) {
            return;
        }
        listener.onStatus("Twitch: conectando al IRC...");
        http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .header("User-Agent", USER_AGENT)
                .buildAsync(TWITCH_URI, new SocketListener())
                .whenComplete((webSocket, throwable) -> {
                    if (throwable != null) {
                        fail("No se pudo conectar a Twitch: " + rootMessage(throwable));
                    }
                });
    }

    private final class SocketListener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            webSocket.request(1);
            send(webSocket, "CAP REQ :twitch.tv/tags twitch.tv/commands");
            send(webSocket, "PASS SCHMOOPIIE");
            send(webSocket, "NICK " + nickname);
            // Twitch procesa mejor el JOIN si llega justo despues del NICK.
            try {
                scheduler.schedule(() -> send(webSocket, "JOIN #" + channel), 250L, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException ignored) {
                send(webSocket, "JOIN #" + channel);
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            incoming.append(data);
            if (last) {
                String chunk = incoming.toString();
                incoming.setLength(0);
                for (String line : chunk.split("\\r?\\n")) {
                    handleLine(webSocket, line.trim());
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            socket = null;
            if (running) {
                listener.onStatus("Twitch: conexion cerrada; reconectando...");
                scheduleReconnect();
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            fail("Twitch: " + rootMessage(error));
        }
    }

    private void handleLine(WebSocket webSocket, String line) {
        if (line.isEmpty()) {
            return;
        }

        if (line.startsWith("PING")) {
            send(webSocket, "PONG :tmi.twitch.tv");
            return;
        }

        if (line.equals("RECONNECT")) {
            listener.onStatus("Twitch: el servidor pidio reconectar.");
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "reconnect");
            } catch (Exception ignored) {
            }
            scheduleReconnect();
            return;
        }

        if (line.contains(" PRIVMSG ")) {
            handlePrivmsg(line);
            return;
        }

        if (line.contains(" JOIN #")) {
            attempts = 0;
            listener.onStatus("Twitch: conectado a #" + channel);
            return;
        }

        if (line.contains(" NOTICE ")) {
            int index = line.indexOf(" :");
            String notice = index >= 0 ? line.substring(index + 2) : line;
            if (notice.toLowerCase().contains("error") || notice.toLowerCase().contains("failed")) {
                listener.onError("Twitch: " + notice);
            } else {
                listener.onStatus("Twitch: " + notice);
            }
        }
    }

    private void handlePrivmsg(String line) {
        String tags = null;
        String rest = line;

        if (line.startsWith("@")) {
            int space = line.indexOf(' ');
            if (space > 0) {
                tags = line.substring(1, space);
                rest = line.substring(space + 1);
            }
        }

        int privmsg = rest.indexOf(" PRIVMSG ");
        if (privmsg < 0) {
            return;
        }
        int colon = rest.indexOf(" :", privmsg);
        if (colon < 0) {
            return;
        }

        String content = rest.substring(colon + 2);
        String displayName = tag(tags, "display-name");
        String color = tag(tags, "color");

        if (displayName.isEmpty()) {
            String prefix = rest.substring(0, privmsg);
            int at = prefix.indexOf(':');
            int bang = prefix.indexOf('!', Math.max(at, 0));
            displayName = at >= 0 && bang > at ? prefix.substring(at + 1, bang) : "twitch";
        }

        displayName = displayName.replace("\\s", " ");
        listener.onMessage(new ChatMessage(ChatPlatform.TWITCH, displayName, content, color));
    }

    private static String tag(String tags, String key) {
        if (tags == null) {
            return "";
        }
        for (String entry : tags.split(";")) {
            int equals = entry.indexOf('=');
            if (equals > 0 && entry.substring(0, equals).equals(key)) {
                return entry.substring(equals + 1);
            }
        }
        return "";
    }

    private void send(WebSocket webSocket, String text) {
        try {
            synchronized (webSocket) {
                webSocket.sendText(text, true);
            }
        } catch (Exception exception) {
            listener.onError("Twitch: no se pudo enviar un comando al IRC.");
        }
    }

    private void fail(String message) {
        listener.onError(message);
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        if (!running || retryScheduled) {
            return;
        }
        retryScheduled = true;
        int delay = Math.min(60, 5 * (1 << Math.min(attempts, 3)));
        attempts++;
        try {
            scheduler.schedule(() -> {
                retryScheduled = false;
                open();
            }, delay, TimeUnit.SECONDS);
        } catch (RejectedExecutionException ignored) {
            retryScheduled = false;
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    @Override
    public void stop() {
        running = false;
        WebSocket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
            } catch (Exception ignored) {
            }
        }
        scheduler.shutdownNow();
    }
}
