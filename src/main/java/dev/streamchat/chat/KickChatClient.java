package dev.streamchat.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.streamchat.ChatConfig;
import dev.streamchat.ChatMessage;
import dev.streamchat.ChatPlatform;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cliente de Kick. Desde 2026 Kick sirve el chat por Centrifugo a traves de
 * su API interna: se pide una URL de conexion, un token de invitado y se
 * subscribe al canal chatrooms.&lt;id&gt;.v2.
 */
public final class KickChatClient implements ChatClient {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";

    private final String slug;
    private final ChatListener listener;
    private final String clientId = UUID.randomUUID().toString();
    private final HttpClient http;
    private final ScheduledExecutorService scheduler;
    private final StringBuilder incoming = new StringBuilder();

    private volatile WebSocket socket;
    private volatile boolean running;
    private volatile boolean retryScheduled;
    private volatile int attempts;
    private volatile long channelId;
    private volatile long chatroomId;

    public KickChatClient(String slug, ChatListener listener) {
        this.slug = slug;
        this.listener = listener;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "StreamChat-Kick");
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
            if (channelId <= 0L) {
                resolveChannel();
            }

            listener.onStatus("Kick: abriendo conexion de chat...");
            JsonObject connection = postJson(
                    apiBase() + "/api/v1/realtime/channels/" + channelId + "/chat/connection",
                    connectionBody()
            );
            String websocketUrl = readConnectionUrl(connection);
            String provider = readConnectionProvider(connection);
            if (!"centrifugo".equalsIgnoreCase(provider)) {
                throw new IOException("Kick devolvio un proveedor no soportado: " + provider);
            }

            JsonObject auth = postJson(
                    apiBase() + "/api/v1/realtime/auth/connection",
                    authBody()
            );
            String token = auth.getAsJsonObject("data").get("token").getAsString();
            if (token == null || token.isBlank()) {
                throw new IOException("Kick no devolvio un token de chat");
            }

            listener.onStatus("Kick: conectando...");
            WebSocket webSocket = http.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .header("User-Agent", USER_AGENT)
                    .buildAsync(URI.create(websocketUrl), new SocketListener(token))
                    .get(20, TimeUnit.SECONDS);
            socket = webSocket;
        } catch (Exception exception) {
            fail("Kick: " + rootMessage(exception));
        }
    }

    private void resolveChannel() throws IOException, InterruptedException {
        if (ChatConfig.kickChannelId > 0) {
            channelId = ChatConfig.kickChannelId;
            chatroomId = ChatConfig.kickChatroomId > 0 ? ChatConfig.kickChatroomId : channelId;
            return;
        }

        listener.onStatus("Kick: buscando el canal " + slug + "...");
        String encoded = URLEncoder.encode(slug, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://kick.com/api/v2/channels/" + encoded))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "https://kick.com/" + slug)
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Kick respondio HTTP " + response.statusCode()
                    + ". Si se repite, pon kickChannelId y kickChatroomId en el TOML.");
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        if (!root.has("id") || !root.has("chatroom")) {
            throw new IOException("La respuesta de Kick no contiene id/chatroom");
        }
        channelId = root.get("id").getAsLong();
        chatroomId = root.getAsJsonObject("chatroom").get("id").getAsLong();
        if (channelId <= 0L || chatroomId <= 0L) {
            throw new IOException("IDs de Kick invalidos");
        }
    }

    private JsonObject connectionBody() {
        JsonObject client = new JsonObject();
        client.addProperty("id", clientId);
        client.addProperty("type", "web");

        JsonObject pusher = new JsonObject();
        pusher.addProperty("provider", "pusher");
        JsonObject centrifugo = new JsonObject();
        centrifugo.addProperty("provider", "centrifugo");
        JsonArray providers = new JsonArray();
        providers.add(pusher);
        providers.add(centrifugo);

        JsonObject capabilities = new JsonObject();
        capabilities.add("accepted_providers", providers);

        JsonObject body = new JsonObject();
        body.add("client", client);
        body.add("capabilities", capabilities);
        return body;
    }

    private JsonObject authBody() {
        JsonObject body = new JsonObject();
        body.addProperty("client_id", clientId);
        return body;
    }

    private JsonObject postJson(String url, JsonObject body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Origin", "https://kick.com")
                .header("Referer", "https://kick.com/" + slug)
                .header("x-app-platform", "web")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Kick respondio HTTP " + response.statusCode() + " en " + url);
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static String readConnectionUrl(JsonObject root) throws IOException {
        JsonArray connections = root.getAsJsonObject("data").getAsJsonArray("connections");
        if (connections.size() == 0) {
            throw new IOException("Kick no devolvio ninguna conexion");
        }
        JsonObject credentials = connections.get(0).getAsJsonObject().getAsJsonObject("credentials");
        if (credentials == null || !credentials.has("url")) {
            throw new IOException("Kick no devolvio la URL del WebSocket");
        }
        return credentials.get("url").getAsString();
    }

    private static String readConnectionProvider(JsonObject root) {
        JsonArray connections = root.getAsJsonObject("data").getAsJsonArray("connections");
        if (connections.size() == 0) {
            return "desconocido";
        }
        JsonElement provider = connections.get(0).getAsJsonObject().get("provider");
        return provider == null ? "desconocido" : provider.getAsString();
    }

    private final class SocketListener implements WebSocket.Listener {
        private final String token;

        private SocketListener(String token) {
            this.token = token;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            webSocket.request(1);
            sendJson(webSocket, connectFrame(token));
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            incoming.append(data);
            if (last) {
                String chunk = incoming.toString();
                incoming.setLength(0);
                handleFrame(webSocket, chunk);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            socket = null;
            if (running) {
                listener.onStatus("Kick: conexion cerrada; reconectando...");
                scheduleReconnect();
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            fail("Kick: " + rootMessage(error));
        }
    }

    private void handleFrame(WebSocket webSocket, String frame) {
        String trimmed = frame.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        // Centrifugo manda un objeto vacio como ping y espera otro como pong.
        if (trimmed.equals("{}")) {
            sendRaw(webSocket, "{}");
            return;
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(trimmed).getAsJsonObject();
        } catch (Exception ignored) {
            return;
        }

        if (root.has("connect")) {
            attempts = 0;
            listener.onStatus("Kick: conectado a #" + slug);
            sendJson(webSocket, subscribeFrame());
            return;
        }

        if (root.has("push")) {
            handlePush(root);
            return;
        }

        if (root.has("error")) {
            listener.onError("Kick: " + root.get("error"));
        }
    }

    private JsonObject connectFrame(String token) {
        JsonObject connect = new JsonObject();
        connect.addProperty("token", token);
        connect.addProperty("name", "streamchat");

        JsonObject root = new JsonObject();
        root.add("connect", connect);
        root.addProperty("id", 1);
        return root;
    }

    private JsonObject subscribeFrame() {
        JsonObject subscribe = new JsonObject();
        subscribe.addProperty("channel", "chatrooms." + chatroomId + ".v2");
        subscribe.addProperty("flag", 1);

        JsonObject root = new JsonObject();
        root.add("subscribe", subscribe);
        root.addProperty("id", 2);
        return root;
    }

    private void handlePush(JsonObject root) {
        JsonObject push = root.getAsJsonObject("push");
        if (push == null || !push.has("pub")) {
            return;
        }
        JsonElement dataElement = push.getAsJsonObject("pub").get("data");
        if (dataElement == null || !dataElement.isJsonObject()) {
            return;
        }
        JsonObject data = dataElement.getAsJsonObject();
        if (!data.has("event") || !data.has("data")) {
            return;
        }
        String event = data.get("event").getAsString();
        if (!event.endsWith("ChatMessageEvent")) {
            return;
        }

        String payload = data.get("data").getAsString();
        JsonObject message = JsonParser.parseString(payload).getAsJsonObject();
        String content = message.has("content") ? message.get("content").getAsString() : "";
        if (content.isBlank()) {
            return;
        }

        String user = "kick";
        String color = null;
        if (message.has("sender")) {
            JsonObject sender = message.getAsJsonObject("sender");
            if (sender.has("username")) {
                user = sender.get("username").getAsString();
            }
            if (sender.has("identity")) {
                JsonObject identity = sender.getAsJsonObject("identity");
                if (identity.has("color")) {
                    color = identity.get("color").getAsString();
                }
            }
        }

        listener.onMessage(new ChatMessage(ChatPlatform.KICK, user, content, color));
    }

    private void sendJson(WebSocket webSocket, JsonObject json) {
        sendRaw(webSocket, json.toString());
    }

    private void sendRaw(WebSocket webSocket, String text) {
        try {
            synchronized (webSocket) {
                webSocket.sendText(text, true);
            }
        } catch (Exception exception) {
            listener.onError("Kick: no se pudo enviar un mensaje al WebSocket.");
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
                channelId = 0L;
                chatroomId = 0L;
                connectFlow();
            }, delay, TimeUnit.SECONDS);
        } catch (RejectedExecutionException ignored) {
            retryScheduled = false;
        }
    }

    private static String apiBase() {
        String base = ChatConfig.kickApiBase;
        if (base == null || base.isBlank()) {
            return "https://web.kick.com";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
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
