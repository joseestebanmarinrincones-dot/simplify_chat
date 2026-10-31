package dev.streamchat.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import dev.streamchat.ChatConfig;
import dev.streamchat.ChatPlatform;
import dev.streamchat.ChatSource;


import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Carga los emotes globales y de canal de BTTV, FFZ y 7TV, y resuelve los
 * codigos escritos en el chat a una imagen.
 */
public final class EmoteManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "SimplifyChat-EmoteAPI");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<String, String> TWITCH_IDS = new ConcurrentHashMap<>();
    private static final Map<String, String> KICK_IDS = new ConcurrentHashMap<>();

    private static volatile Map<String, Emote> emotes = Map.of();
    private static volatile String signature = "";

    private EmoteManager() {
    }

    public static void configure(List<ChatSource> sources) {
        if (!ChatConfig.emotesEnabled) {
            emotes = Map.of();
            signature = "";
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (ChatSource source : sources) {
            builder.append(source.key()).append(';');
        }
        builder.append("providers=").append(ChatConfig.emoteProviders);
        builder.append(";channel=").append(ChatConfig.emotesChannelSpecific);
        String nextSignature = builder.toString();
        if (nextSignature.equals(signature)) {
            return;
        }
        signature = nextSignature;

        List<ChatSource> snapshot = List.copyOf(sources);
        EXECUTOR.execute(() -> load(snapshot));
    }

    public static void reload() {
        signature = "";
        configure(ChatConfig.effectiveSources());
    }

    public static Emote resolve(String code) {
        if (code == null || code.isEmpty() || !ChatConfig.emotesEnabled) {
            return null;
        }
        return emotes.get(code);
    }

    public static void tick() {
        EmoteTextures.drainUploads();
    }

    private static void load(List<ChatSource> sources) {
        long started = System.currentTimeMillis();
        Map<String, Emote> fresh = new HashMap<>();
        Set<String> providers = ChatConfig.emoteProviders;

        try {
            if (providers.contains("bttv")) {
                loadBttvGlobal(fresh);
            }
            if (providers.contains("ffz")) {
                loadFfzGlobal(fresh);
            }
            if (providers.contains("7tv")) {
                load7tvGlobal(fresh);
            }
        } catch (Exception exception) {
            LOGGER.warn("Error cargando emotes globales", exception);
        }

        if (ChatConfig.emotesChannelSpecific) {
            Set<String> seen = new HashSet<>();
            for (ChatSource source : sources) {
                String key = source.platform().id() + ":" + source.channel().toLowerCase(Locale.ROOT);
                if (!seen.add(key)) {
                    continue;
                }
                try {
                    loadChannel(source, fresh, providers);
                } catch (Exception exception) {
                    LOGGER.debug("Sin emotes de canal para {}: {}", source.display(), exception.getMessage());
                }
            }
        }

        if (!fresh.isEmpty()) {
            emotes = Map.copyOf(fresh);
        }
        LOGGER.info("Emotes cargados: {} en {} ms", fresh.size(), System.currentTimeMillis() - started);
    }

    private static void loadChannel(ChatSource source, Map<String, Emote> target, Set<String> providers) {
        if (source.platform() == ChatPlatform.TWITCH) {
            String twitchId = twitchId(source.channel());
            if (twitchId == null) {
                return;
            }
            if (providers.contains("bttv")) {
                loadBttvChannel(twitchId, target);
            }
            if (providers.contains("ffz")) {
                loadFfzRoom(twitchId, target);
            }
            if (providers.contains("7tv")) {
                load7tvUser("twitch", twitchId, target);
            }
        } else if (source.platform() == ChatPlatform.KICK) {
            String kickId = kickId(source.channel());
            if (kickId != null && providers.contains("7tv")) {
                load7tvUser("kick", kickId, target);
            }
        }
    }

    private static String twitchId(String channel) {
        String cached = TWITCH_IDS.get(channel);
        if (cached != null) {
            return cached;
        }
        try {
            JsonElement root = getJson("https://api.ivr.fi/v2/twitch/user?login="
                    + URLEncoder.encode(channel, StandardCharsets.UTF_8));
            if (root != null && root.isJsonArray() && root.getAsJsonArray().size() > 0) {
                JsonObject user = root.getAsJsonArray().get(0).getAsJsonObject();
                if (user.has("id")) {
                    String id = user.get("id").getAsString();
                    TWITCH_IDS.put(channel, id);
                    return id;
                }
            }
        } catch (Exception exception) {
            LOGGER.debug("No se pudo resolver el ID de Twitch de {}", channel);
        }
        return null;
    }

    private static String kickId(String channel) {
        String cached = KICK_IDS.get(channel);
        if (cached != null) {
            return cached;
        }
        try {
            JsonElement root = getJson("https://kick.com/api/v2/channels/"
                    + URLEncoder.encode(channel, StandardCharsets.UTF_8));
            if (root != null && root.isJsonObject()) {
                JsonObject channelJson = root.getAsJsonObject();
                if (channelJson.has("user_id")) {
                    String id = channelJson.get("user_id").getAsString();
                    KICK_IDS.put(channel, id);
                    return id;
                }
            }
        } catch (Exception exception) {
            LOGGER.debug("No se pudo resolver el ID de Kick de {}", channel);
        }
        return null;
    }

    private static void loadBttvGlobal(Map<String, Emote> target) {
        JsonElement root = getJson("https://api.betterttv.net/3/cached/emotes/global");
        if (root == null || !root.isJsonArray()) {
            return;
        }
        for (JsonElement element : root.getAsJsonArray()) {
            addBttv(element, target);
        }
    }

    private static void loadBttvChannel(String twitchId, Map<String, Emote> target) {
        JsonElement root = getJson("https://api.betterttv.net/3/cached/users/twitch/" + twitchId);
        if (root == null || !root.isJsonObject()) {
            return;
        }
        JsonObject object = root.getAsJsonObject();
        addBttvArray(object.get("channelEmotes"), target);
        addBttvArray(object.get("sharedEmotes"), target);
    }

    private static void addBttvArray(JsonElement element, Map<String, Emote> target) {
        if (element == null || !element.isJsonArray()) {
            return;
        }
        for (JsonElement emote : element.getAsJsonArray()) {
            addBttv(emote, target);
        }
    }

    private static void addBttv(JsonElement element, Map<String, Emote> target) {
        if (element == null || !element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (!object.has("code") || !object.has("id")) {
            return;
        }
        String code = object.get("code").getAsString();
        String id = object.get("id").getAsString();
        if (code == null || code.isBlank()) {
            return;
        }
        String url = "https://cdn.betterttv.net/emote/" + id + "/1x";
        target.put(code, new Emote(code, url, 28, 28, "bttv"));
    }

    private static void loadFfzGlobal(Map<String, Emote> target) {
        JsonElement root = getJson("https://api.frankerfacez.com/v1/set/global");
        addFfzSets(root, target);
    }

    private static void loadFfzRoom(String twitchId, Map<String, Emote> target) {
        JsonElement root = getJson("https://api.frankerfacez.com/v1/room/id/" + twitchId);
        addFfzSets(root, target);
    }

    private static void addFfzSets(JsonElement root, Map<String, Emote> target) {
        if (root == null || !root.isJsonObject()) {
            return;
        }
        JsonElement setsElement = root.getAsJsonObject().get("sets");
        if (setsElement == null || !setsElement.isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> entry : setsElement.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonElement emoticons = entry.getValue().getAsJsonObject().get("emoticons");
            if (emoticons == null || !emoticons.isJsonArray()) {
                continue;
            }
            for (JsonElement element : emoticons.getAsJsonArray()) {
                addFfz(element, target);
            }
        }
    }

    private static void addFfz(JsonElement element, Map<String, Emote> target) {
        if (element == null || !element.isJsonObject()) {
            return;
        }
        JsonObject object = element.getAsJsonObject();
        if (!object.has("name")) {
            return;
        }
        String code = object.get("name").getAsString();
        if (code == null || code.isBlank()) {
            return;
        }
        String url = null;
        JsonElement animated = object.get("animated");
        if (animated != null && animated.isJsonObject()) {
            JsonElement one = animated.getAsJsonObject().get("1");
            if (one != null && !one.isJsonNull()) {
                url = one.getAsString();
            }
        }
        if (url == null && object.has("urls") && object.get("urls").isJsonObject()) {
            JsonElement one = object.getAsJsonObject("urls").get("1");
            if (one != null && !one.isJsonNull()) {
                url = one.getAsString();
            }
        }
        if (url == null || url.isBlank()) {
            return;
        }
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        int width = object.has("width") ? object.get("width").getAsInt() : 28;
        int height = object.has("height") ? object.get("height").getAsInt() : 28;
        target.put(code, new Emote(code, url, width, height, "ffz"));
    }

    private static void load7tvGlobal(Map<String, Emote> target) {
        JsonElement root = getJson("https://7tv.io/v3/emote-sets/global");
        if (root == null || !root.isJsonObject()) {
            return;
        }
        JsonElement list = root.getAsJsonObject().get("emotes");
        if (list == null || !list.isJsonArray()) {
            return;
        }
        for (JsonElement element : list.getAsJsonArray()) {
            add7tv(element, target);
        }
    }

    private static void load7tvUser(String platform, String id, Map<String, Emote> target) {
        JsonElement root = getJson("https://7tv.io/v3/users/" + platform + "/" + id);
        if (root == null || !root.isJsonObject()) {
            return;
        }
        JsonElement set = root.getAsJsonObject().get("emote_set");
        if (set == null || !set.isJsonObject()) {
            return;
        }
        JsonElement list = set.getAsJsonObject().get("emotes");
        if (list == null || !list.isJsonArray()) {
            return;
        }
        for (JsonElement element : list.getAsJsonArray()) {
            add7tv(element, target);
        }
    }

    private static void add7tv(JsonElement element, Map<String, Emote> target) {
        if (element == null || !element.isJsonObject()) {
            return;
        }
        JsonObject wrapper = element.getAsJsonObject();
        JsonObject data = wrapper.has("data") && wrapper.get("data").isJsonObject()
                ? wrapper.getAsJsonObject("data") : wrapper;
        String code = data.has("name") ? data.get("name").getAsString()
                : wrapper.has("name") ? wrapper.get("name").getAsString() : null;
        if (code == null || code.isBlank()) {
            return;
        }
        JsonElement hostElement = data.get("host");
        if (hostElement == null || !hostElement.isJsonObject()) {
            return;
        }
        JsonObject host = hostElement.getAsJsonObject();
        if (!host.has("url") || !host.has("files") || !host.get("files").isJsonArray()) {
            return;
        }
        String base = host.get("url").getAsString();
        if (base.startsWith("//")) {
            base = "https:" + base;
        }

        boolean animated = data.has("animated") && data.get("animated").getAsBoolean();
        JsonObject chosen = null;
        JsonObject fallback = null;
        for (JsonElement fileElement : host.getAsJsonArray("files")) {
            if (!fileElement.isJsonObject()) {
                continue;
            }
            JsonObject file = fileElement.getAsJsonObject();
            String format = file.has("format") ? file.get("format").getAsString().toUpperCase(Locale.ROOT) : "";
            String name = file.has("name") ? file.get("name").getAsString() : "";
            if (!name.startsWith("1x")) {
                continue;
            }
            if (fallback == null) {
                fallback = file;
            }
            if (animated && "GIF".equals(format)) {
                chosen = file;
                break;
            }
            if (!animated && "PNG".equals(format)) {
                chosen = file;
                break;
            }
        }
        if (chosen == null) {
            chosen = fallback;
        }
        if (chosen == null) {
            return;
        }
        String name = chosen.get("name").getAsString();
        int width = chosen.has("width") ? chosen.get("width").getAsInt() : 32;
        int height = chosen.has("height") ? chosen.get("height").getAsInt() : 32;
        target.put(code, new Emote(code, base + "/" + name, width, height, "7tv"));
    }

    private static JsonElement getJson(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(10000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            try {
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) {
                    return null;
                }
                try (InputStream input = connection.getInputStream();
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int read;
                    int total = 0;
                    while ((read = input.read(buffer)) >= 0) {
                        total += read;
                        if (total > 8 * 1024 * 1024) {
                            return null;
                        }
                        output.write(buffer, 0, read);
                    }
                    return new JsonParser().parse(output.toString(StandardCharsets.UTF_8));
                }
            } finally {
                connection.disconnect();
            }
        } catch (Exception exception) {
            LOGGER.debug("Error consultando {}: {}", url, exception.getMessage());
            return null;
        }
    }
}
