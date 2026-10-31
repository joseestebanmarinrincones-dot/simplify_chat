package dev.streamchat.chat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.streamchat.ChatMessage;
import dev.streamchat.ChatPlatform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cliente del puente local de TikTok. El puente (Node.js) es quien habla con
 * TikTok; este cliente solo recibe los mensajes ya limpios por WebSocket.
 */
public final class TikTokBridgeClient implements ChatClient {
    private final String bridgeUrl;
    private final String channel;
    private final ChatListener listener;
    private final HttpClient http;
    private final ScheduledExecutorService scheduler;
    private final StringBuilder incoming = new StringBuilder();

    private volatile WebSocket socket;
    private volatile boolean running;
    private volatile boolean retryScheduled;
    private volatile boolean warnedOffline;

    public TikTokBridgeClient(String bridgeUrl, String channel, ChatListener listener) {
        this.bridgeUrl = bridgeUrl;
        this.channel = channel;
        this.listener = listener;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "StreamChat-TikTok");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void start() {
        running = true;
        scheduler.execute(this::connectFlow);
    }

    private void connectFlow() {
        if (!running) {
            return;
        }
        try {
            listener.onStatus("TikTok: conectando al puente local...");
            WebSocket webSocket = http.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .buildAsync(URI.create(bridgeUrl), new SocketListener())
                    .get(10, TimeUnit.SECONDS);
            socket = webSocket;
        } catch (Exception exception) {
            if (!warnedOffline) {
                warnedOffline = true;
                listener.onError("TikTok: no se pudo conectar al puente en " + bridgeUrl
                        + ". Abre tiktok-bridge/iniciar-puente.bat y vuelve a intentarlo.");
            } else {
                listener.onStatus("TikTok: esperando al puente local...");
            }
            scheduleReconnect();
        }
    }

    private final class SocketListener implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            warnedOffline = false;
            webSocket.request(1);

            JsonObject join = new JsonObject();
            join.addProperty("type", "join");
            join.addProperty("channel", channel);
            send(webSocket, join.toString());

            listener.onStatus("TikTok: puente conectado; esperando el directo...");
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            incoming.append(data);
            if (last) {
                String chunk = incoming.toString();
                incoming.setLength(0);
                handleFrame(chunk);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            socket = null;
            if (running) {
                listener.onStatus("TikTok: puente desconectado; reintentando...");
                scheduleReconnect();
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (!warnedOffline) {
                warnedOffline = true;
                listener.onError("TikTok: " + rootMessage(error));
            }
            scheduleReconnect();
        }
    }

    private void handleFrame(String frame) {
        JsonObject root;
        try {
            root = JsonParser.parseString(frame).getAsJsonObject();
        } catch (Exception ignored) {
            return;
        }
        if (!root.has("type")) {
            return;
        }

        String type = root.get("type").getAsString();
        switch (type) {
            case "chat" -> handleChat(root);
            case "status" -> handleStatus(root);
            case "error" -> listener.onError("TikTok: " + string(root, "message", "error desconocido"));
            case "hello" -> listener.onStatus("TikTok: " + string(root, "message", "puente listo"));
            default -> {
                // Ignorar mensajes de protocolo desconocidos.
            }
        }
    }

    private void handleChat(JsonObject root) {
        String user = string(root, "displayName", "");
        if (user.isBlank()) {
            user = string(root, "user", "tiktok");
        }
        String message = string(root, "message", "");
        if (message.isBlank()) {
            return;
        }
        String color = string(root, "color", null);
        listener.onMessage(new ChatMessage(ChatPlatform.TIKTOK, user, message, color));
    }

    private void handleStatus(JsonObject root) {
        String status = string(root, "status", "");
        String message = string(root, "message", status);
        switch (status) {
            case "connected" -> {
                warnedOffline = false;
                listener.onStatus("TikTok: conectado a @" + channel);
            }
            case "connecting" -> listener.onStatus("TikTok: conectando a @" + channel + "...");
            case "offline" -> listener.onError("TikTok: @" + channel + " no esta en directo.");
            case "error" -> listener.onError("TikTok: " + message);
            case "disconnected" -> listener.onStatus("TikTok: conexion perdida; esperando...");
            default -> listener.onStatus("TikTok: " + message);
        }
    }

    private static String string(JsonObject root, String key, String fallback) {
        if (root.has(key) && !root.get(key).isJsonNull()) {
            return root.get(key).getAsString();
        }
        return fallback;
    }

    private void send(WebSocket webSocket, String text) {
        try {
            synchronized (webSocket) {
                webSocket.sendText(text, true);
            }
        } catch (Exception ignored) {
        }
    }

    private void scheduleReconnect() {
        if (!running || retryScheduled) {
            return;
        }
        retryScheduled = true;
        try {
            scheduler.schedule(() -> {
                retryScheduled = false;
                connectFlow();
            }, 10L, TimeUnit.SECONDS);
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
