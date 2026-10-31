package dev.streamchat;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Configuracion del mod. Se guarda en config/streamchat-client.toml y se
 * puede editar con cualquier editor de texto mientras el juego esta cerrado.
 */
public final class ChatConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> PLATFORM;
    private static final ForgeConfigSpec.ConfigValue<String> CHANNEL;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> SOURCES;
    private static final ForgeConfigSpec.BooleanValue ANNOUNCE_STATUS;
    private static final ForgeConfigSpec.BooleanValue SHOW_PLATFORM_TAG;
    private static final ForgeConfigSpec.BooleanValue SHOW_NAME_COLORS;
    private static final ForgeConfigSpec.BooleanValue IGNORE_COMMANDS;
    private static final ForgeConfigSpec.IntValue MAX_MESSAGE_LENGTH;
    private static final ForgeConfigSpec.IntValue MAX_MESSAGES_PER_SECOND;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCKED_USERS;
    private static final ForgeConfigSpec.ConfigValue<String> TIKTOK_BRIDGE_URL;
    private static final ForgeConfigSpec.ConfigValue<String> KICK_API_BASE;
    private static final ForgeConfigSpec.IntValue KICK_CHANNEL_ID;
    private static final ForgeConfigSpec.IntValue KICK_CHATROOM_ID;
    private static final ForgeConfigSpec.BooleanValue OVERLAY_ENABLED;
    private static final ForgeConfigSpec.BooleanValue OVERLAY_MIRROR_VANILLA;
    private static final ForgeConfigSpec.IntValue OVERLAY_WIDTH;
    private static final ForgeConfigSpec.IntValue OVERLAY_MAX_MESSAGES;
    private static final ForgeConfigSpec.IntValue OVERLAY_LIFETIME;
    private static final ForgeConfigSpec.DoubleValue OVERLAY_SCALE;
    private static final ForgeConfigSpec.ConfigValue<String> OVERLAY_ANCHOR;
    private static final ForgeConfigSpec.IntValue OVERLAY_MARGIN_X;
    private static final ForgeConfigSpec.IntValue OVERLAY_MARGIN_Y;
    private static final ForgeConfigSpec.DoubleValue OVERLAY_BACKGROUND;
    private static final ForgeConfigSpec.BooleanValue OVERLAY_TEXT_SHADOW;
    private static final ForgeConfigSpec.DoubleValue OVERLAY_TEXT_OPACITY;
    private static final ForgeConfigSpec.ConfigValue<String> OVERLAY_BACKGROUND_COLOR;
    private static final ForgeConfigSpec.ConfigValue<String> OVERLAY_MESSAGE_COLOR;
    private static final ForgeConfigSpec.ConfigValue<String> OVERLAY_SYSTEM_COLOR;
    private static final ForgeConfigSpec.ConfigValue<String> OVERLAY_ERROR_COLOR;
    private static final ForgeConfigSpec.ConfigValue<String> OVERLAY_SEPARATOR_COLOR;
    private static final ForgeConfigSpec.ConfigValue<String> OVERLAY_TWITCH_COLOR;
    private static final ForgeConfigSpec.ConfigValue<String> OVERLAY_KICK_COLOR;
    private static final ForgeConfigSpec.ConfigValue<String> OVERLAY_TIKTOK_COLOR;
    private static final ForgeConfigSpec.ConfigValue<String> OVERLAY_PLATFORM_TAG;
    private static final ForgeConfigSpec.ConfigValue<String> OVERLAY_MESSAGE_FORMAT;
    private static final ForgeConfigSpec.ConfigValue<String> OVERLAY_SYSTEM_PREFIX;
    private static final ForgeConfigSpec.BooleanValue EMOTES_ENABLED;
    private static final ForgeConfigSpec.BooleanValue EMOTES_ANIMATED;
    private static final ForgeConfigSpec.BooleanValue EMOTES_CHANNEL_SPECIFIC;
    private static final ForgeConfigSpec.IntValue EMOTES_SIZE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> EMOTES_PROVIDERS;

    // Valores cacheados para no tocar el spec desde hilos de red.
    public static volatile boolean enabled = true;
    public static volatile boolean announceStatus = true;
    public static volatile String platform = "twitch";
    public static volatile String channel = "";
    public static volatile List<ChatSource> sources = java.util.Collections.emptyList();
    public static volatile boolean showPlatformTag = true;
    public static volatile boolean showNameColors = true;
    public static volatile boolean ignoreCommands = true;
    public static volatile int maxMessageLength = 220;
    public static volatile int maxMessagesPerSecond = 8;
    public static volatile Set<String> blockedUsers = java.util.Collections.emptySet();
    public static volatile String tiktokBridgeUrl = "ws://127.0.0.1:8787";
    public static volatile String kickApiBase = "https://web.kick.com";
    public static volatile int kickChannelId = 0;
    public static volatile int kickChatroomId = 0;

    public static volatile boolean overlayEnabled = true;
    public static volatile boolean overlayMirrorVanilla = false;
    public static volatile int overlayWidth = 300;
    public static volatile int overlayMaxMessages = 12;
    public static volatile int overlayLifetimeSeconds = 18;
    public static volatile double overlayScale = 1.0;
    public static volatile String overlayAnchor = "top_left";
    public static volatile int overlayMarginX = 8;
    public static volatile int overlayMarginY = 8;
    public static volatile double overlayBackground = 0.0;
    public static volatile boolean overlayTextShadow = true;
    public static volatile double overlayTextOpacity = 1.0;
    public static volatile int overlayBackgroundColor = 0x000000;
    public static volatile int overlayMessageColor = 0xFFFFFF;
    public static volatile int overlaySystemColor = 0xAAAAAA;
    public static volatile int overlayErrorColor = 0xFF5555;
    public static volatile int overlaySeparatorColor = 0x888888;
    public static volatile int overlayTwitchColor = 0x9146FF;
    public static volatile int overlayKickColor = 0x53FC18;
    public static volatile int overlayTikTokColor = 0x25F4EE;
    public static volatile String overlayPlatformTagFormat = "[{platform}] ";
    public static volatile String overlayMessageFormat = "{tag}{name}: {message}";
    public static volatile String overlaySystemPrefix = "[Simplify] ";

    public static volatile boolean emotesEnabled = true;
    public static volatile boolean emotesAnimated = true;
    public static volatile boolean emotesChannelSpecific = true;
    public static volatile int emotesSize = 12;
    public static volatile Set<String> emoteProviders = new HashSet<>(java.util.Arrays.asList("bttv", "ffz", "7tv"));

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment(
                "Simplify Chat - muestra el chat de Twitch, Kick y TikTok dentro de Minecraft.",
                "Los mensajes se leen de forma anonima: el mod nunca envia nada a las plataformas.",
                "Despues de editar este archivo, ejecuta /streamchat reload o reinicia el juego."
        ).push("general");

        ENABLED = builder
                .comment("Conectarse automaticamente al entrar a un mundo o servidor.")
                .define("enabled", true);

        PLATFORM = builder
                .comment("Plataforma por defecto: twitch, kick o tiktok.")
                .define("platform", "twitch");

        CHANNEL = builder
                .comment("Canal antiguo de una sola fuente. Se usa solo si sources esta vacio.",
                        "En Twitch/Kick es el nombre sin @. En TikTok es el usuario sin @.")
                .define("channel", "");

        SOURCES = builder
                .comment("Lista de chats conectados a la vez, con formato plataforma:canal.",
                        "Ejemplo: [\"twitch:ibai\", \"kick:ibai\", \"tiktok:usuario\"].",
                        "Si esta lista tiene entradas, platform/channel se ignoran.",
                        "Tambien puedes usar /streamchat add <plataforma> <canal>.")
                .defineListAllowEmpty(java.util.Arrays.asList("sources"),
                        () -> java.util.Collections.emptyList(), value -> value instanceof String);

        ANNOUNCE_STATUS = builder
                .comment("Mostrar en el chat los avisos de conexion, errores y reconexiones.")
                .define("announceStatus", true);

        SHOW_PLATFORM_TAG = builder
                .comment("Mostrar una etiqueta como [Twitch] delante de cada mensaje.")
                .define("showPlatformTag", true);

        SHOW_NAME_COLORS = builder
                .comment("Usar el color que el usuario tiene en Twitch/Kick cuando este disponible.")
                .define("showNameColors", true);

        IGNORE_COMMANDS = builder
                .comment("Ignorar mensajes que empiezan por ! (comandos de bots).")
                .define("ignoreCommands", true);

        MAX_MESSAGE_LENGTH = builder
                .comment("Longitud maxima de cada mensaje mostrado en Minecraft.")
                .defineInRange("maxMessageLength", 220, 32, 500);

        MAX_MESSAGES_PER_SECOND = builder
                .comment("Limite de mensajes mostrados por segundo para evitar floods. 0 = sin limite.")
                .defineInRange("maxMessagesPerSecond", 8, 0, 100);

        BLOCKED_USERS = builder
                .comment("Usuarios cuyos mensajes nunca se muestran (sin distinguir mayusculas).")
                .defineListAllowEmpty(java.util.Arrays.asList("blockedUsers"), () -> java.util.Collections.emptyList(), value -> value instanceof String);

        builder.pop();

        builder.comment("Ajustes especificos del puente local de TikTok.").push("tiktok");
        TIKTOK_BRIDGE_URL = builder
                .comment("Direccion WebSocket del puente. Debe coincidir con tiktok-bridge/config.json.")
                .define("bridgeUrl", "ws://127.0.0.1:8787");
        builder.pop();

        builder.comment(
                "Ajustes de Kick. Normalmente no hace falta tocar nada.",
                "Si Kick bloquea la consulta del canal, puedes poner los IDs a mano.",
                "Puedes obtenerlos en https://kick.com/api/v2/channels/TU_CANAL"
        ).push("kick");
        KICK_API_BASE = builder
                .comment("Base de la API interna de Kick usada para abrir el chat.")
                .define("apiBase", "https://web.kick.com");
        KICK_CHANNEL_ID = builder
                .comment("ID de canal de Kick. 0 = resolverlo automaticamente.")
                .defineInRange("channelId", 0, 0, Integer.MAX_VALUE);
        KICK_CHATROOM_ID = builder
                .comment("ID del chatroom de Kick. 0 = usar el mismo que el canal.")
                .defineInRange("chatroomId", 0, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.comment(
                "Overlay transparente dentro del juego, inspirado en Transparent Twitch Chat Overlay.",
                "Los mensajes aparecen apilados sobre el juego y desaparecen solos.",
                "El chat normal de Minecraft se queda limpio salvo que actives mirrorToVanillaChat."
        ).push("overlay");

        OVERLAY_ENABLED = builder
                .comment("Mostrar el overlay dentro del juego.")
                .define("enabled", true);

        OVERLAY_MIRROR_VANILLA = builder
                .comment("Enviar tambien los mensajes al chat normal de Minecraft (por defecto no).")
                .define("mirrorToVanillaChat", false);

        OVERLAY_WIDTH = builder
                .comment("Ancho maximo del texto en pixeles de GUI antes de partir la linea.")
                .defineInRange("width", 300, 120, 800);

        OVERLAY_MAX_MESSAGES = builder
                .comment("Cantidad maxima de mensajes visibles a la vez.")
                .defineInRange("maxMessages", 12, 1, 40);

        OVERLAY_LIFETIME = builder
                .comment("Segundos que un mensaje permanece en pantalla antes de desaparecer.")
                .defineInRange("lifetimeSeconds", 18, 3, 120);

        OVERLAY_SCALE = builder
                .comment("Escala del overlay. 1.0 = tamano normal.")
                .defineInRange("scale", 1.0, 0.5, 2.0);

        OVERLAY_ANCHOR = builder
                .comment("Esquina donde se ancla: top_left, top_right, bottom_left o bottom_right.")
                .define("anchor", "top_left");

        OVERLAY_MARGIN_X = builder
                .comment("Margen horizontal respecto a la esquina, en pixeles de GUI.")
                .defineInRange("marginX", 8, 0, 400);

        OVERLAY_MARGIN_Y = builder
                .comment("Margen vertical respecto a la esquina, en pixeles de GUI.")
                .defineInRange("marginY", 8, 0, 400);

        OVERLAY_BACKGROUND = builder
                .comment("Opacidad del fondo detras del overlay (0.0 = totalmente transparente).")
                .defineInRange("backgroundOpacity", 0.0, 0.0, 0.8);

        OVERLAY_BACKGROUND_COLOR = builder
                .comment("Color del fondo del overlay en formato #RRGGBB.")
                .define("backgroundColor", "#000000");

        OVERLAY_TEXT_SHADOW = builder
                .comment("Sombra del texto para que se lea mejor sobre el juego.")
                .define("textShadow", true);

        OVERLAY_TEXT_OPACITY = builder
                .comment("Opacidad general del texto del overlay (0.05 = casi invisible, 1.0 = solido).")
                .defineInRange("textOpacity", 1.0, 0.05, 1.0);

        OVERLAY_MESSAGE_COLOR = builder
                .comment("Color del texto de los mensajes en formato #RRGGBB.")
                .define("messageColor", "#FFFFFF");

        OVERLAY_SYSTEM_COLOR = builder
                .comment("Color de los avisos del sistema (conexion, estado, etc.).")
                .define("systemColor", "#AAAAAA");

        OVERLAY_ERROR_COLOR = builder
                .comment("Color de los errores.")
                .define("errorColor", "#FF5555");

        OVERLAY_SEPARATOR_COLOR = builder
                .comment("Color de los separadores y textos fijos del formato.")
                .define("separatorColor", "#888888");

        OVERLAY_TWITCH_COLOR = builder
                .comment("Color de la etiqueta [Twitch] y del nombre sin color propio.")
                .define("twitchColor", "#9146FF");

        OVERLAY_KICK_COLOR = builder
                .comment("Color de la etiqueta [Kick] y del nombre sin color propio.")
                .define("kickColor", "#53FC18");

        OVERLAY_TIKTOK_COLOR = builder
                .comment("Color de la etiqueta [TikTok] y del nombre sin color propio.")
                .define("tiktokColor", "#25F4EE");

        OVERLAY_PLATFORM_TAG = builder
                .comment("Texto de la etiqueta de plataforma. {platform} se sustituye por Twitch/Kick/TikTok.",
                        "Ejemplo: \"[{platform}] \" o \"\" para ocultarla.")
                .define("platformTag", "[{platform}] ");

        OVERLAY_MESSAGE_FORMAT = builder
                .comment("Formato de cada mensaje. Placeholders: {tag}, {platform}, {name}, {message}.",
                        "Ejemplo: \"{tag}{name}: {message}\" o \"{name} > {message}\".")
                .define("messageFormat", "{tag}{name}: {message}");

        OVERLAY_SYSTEM_PREFIX = builder
                .comment("Prefijo de los avisos internos del mod.")
                .define("systemPrefix", "[Simplify] ");

        builder.pop();

        builder.comment(
                "Emotes de BTTV, FFZ y 7TV con sus imagenes reales dentro del overlay.",
                "Las imagenes se descargan al conectar y se guardan en config/streamchat-emotes."
        ).push("emotes");

        EMOTES_ENABLED = builder
                .comment("Mostrar imagenes de emotes en lugar del texto :codigo:.")
                .define("enabled", true);

        EMOTES_ANIMATED = builder
                .comment("Reproducir emotes animados (GIF) cuando el proveedor los ofrezca.")
                .define("animated", true);

        EMOTES_CHANNEL_SPECIFIC = builder
                .comment("Cargar tambien los emotes propios del canal de Twitch/Kick.")
                .define("channelSpecific", true);

        EMOTES_SIZE = builder
                .comment("Altura de cada emote en pixeles de GUI (el ancho mantiene la proporcion).")
                .defineInRange("size", 12, 8, 28);

        EMOTES_PROVIDERS = builder
                .comment("Proveedores activos: bttv, ffz, 7tv.")
                .defineListAllowEmpty(java.util.Arrays.asList("providers"),
                        () -> java.util.Arrays.asList("bttv", "ffz", "7tv"),
                        value -> value instanceof String);

        builder.pop();

        SPEC = builder.build();
    }

    private ChatConfig() {
    }

    public static void load() {
        try {
            enabled = ENABLED.get();
            announceStatus = ANNOUNCE_STATUS.get();
            platform = PLATFORM.get();
            channel = CHANNEL.get();

            LinkedHashSet<String> parsedSources = new LinkedHashSet<>();
            for (String entry : SOURCES.get()) {
                ChatSource source = ChatSource.parse(entry);
                if (source != null) {
                    parsedSources.add(source.configValue());
                }
            }
            List<ChatSource> parsed = new ArrayList<>();
            for (String entry : parsedSources) {
                ChatSource source = ChatSource.parse(entry);
                if (source != null) {
                    parsed.add(source);
                }
            }
            sources = parsed;

            showPlatformTag = SHOW_PLATFORM_TAG.get();
            showNameColors = SHOW_NAME_COLORS.get();
            ignoreCommands = IGNORE_COMMANDS.get();
            maxMessageLength = MAX_MESSAGE_LENGTH.get();
            maxMessagesPerSecond = MAX_MESSAGES_PER_SECOND.get();
            tiktokBridgeUrl = TIKTOK_BRIDGE_URL.get();
            kickApiBase = KICK_API_BASE.get();
            kickChannelId = KICK_CHANNEL_ID.get();
            kickChatroomId = KICK_CHATROOM_ID.get();

            overlayEnabled = OVERLAY_ENABLED.get();
            overlayMirrorVanilla = OVERLAY_MIRROR_VANILLA.get();
            overlayWidth = OVERLAY_WIDTH.get();
            overlayMaxMessages = OVERLAY_MAX_MESSAGES.get();
            overlayLifetimeSeconds = OVERLAY_LIFETIME.get();
            overlayScale = OVERLAY_SCALE.get();
            overlayAnchor = normalizeAnchor(OVERLAY_ANCHOR.get());
            overlayMarginX = OVERLAY_MARGIN_X.get();
            overlayMarginY = OVERLAY_MARGIN_Y.get();
            overlayBackground = OVERLAY_BACKGROUND.get();
            overlayTextShadow = OVERLAY_TEXT_SHADOW.get();
            overlayTextOpacity = OVERLAY_TEXT_OPACITY.get();
            overlayBackgroundColor = colorOrDefault(OVERLAY_BACKGROUND_COLOR.get(), 0x000000);
            overlayMessageColor = colorOrDefault(OVERLAY_MESSAGE_COLOR.get(), 0xFFFFFF);
            overlaySystemColor = colorOrDefault(OVERLAY_SYSTEM_COLOR.get(), 0xAAAAAA);
            overlayErrorColor = colorOrDefault(OVERLAY_ERROR_COLOR.get(), 0xFF5555);
            overlaySeparatorColor = colorOrDefault(OVERLAY_SEPARATOR_COLOR.get(), 0x888888);
            overlayTwitchColor = colorOrDefault(OVERLAY_TWITCH_COLOR.get(), 0x9146FF);
            overlayKickColor = colorOrDefault(OVERLAY_KICK_COLOR.get(), 0x53FC18);
            overlayTikTokColor = colorOrDefault(OVERLAY_TIKTOK_COLOR.get(), 0x25F4EE);
            overlayPlatformTagFormat = OVERLAY_PLATFORM_TAG.get();
            overlayMessageFormat = OVERLAY_MESSAGE_FORMAT.get();
            overlaySystemPrefix = OVERLAY_SYSTEM_PREFIX.get();

            emotesEnabled = EMOTES_ENABLED.get();
            emotesAnimated = EMOTES_ANIMATED.get();
            emotesChannelSpecific = EMOTES_CHANNEL_SPECIFIC.get();
            emotesSize = EMOTES_SIZE.get();
            Set<String> providers = new LinkedHashSet<>();
            for (String entry : EMOTES_PROVIDERS.get()) {
                if (entry != null) {
                    String normalized = entry.trim().toLowerCase(Locale.ROOT);
                    if (normalized.equals("bttv") || normalized.equals("ffz") || normalized.equals("7tv")) {
                        providers.add(normalized);
                    }
                }
            }
            emoteProviders = new HashSet<>(providers);

            Set<String> blocked = new HashSet<>();
            for (String entry : BLOCKED_USERS.get()) {
                if (entry != null && !entry.trim().isEmpty()) {
                    blocked.add(entry.toLowerCase(Locale.ROOT));
                }
            }
            blockedUsers = java.util.Collections.unmodifiableSet(new HashSet<>(blocked));
        } catch (IllegalStateException ignored) {
            // El spec todavia no se ha cargado; los valores por defecto son validos.
        }
    }

    public static void setPlatform(String value) {
        PLATFORM.set(value);
        PLATFORM.save();
        platform = value;
    }

    public static void setChannel(String value) {
        CHANNEL.set(value);
        CHANNEL.save();
        channel = value;
    }

    public static List<ChatSource> effectiveSources() {
        if (sources != null && !sources.isEmpty()) {
            return sources;
        }
        if (channel == null || channel.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        ChatPlatform selected = ChatPlatform.byIdOrDefault(platform);
        String normalized = ChannelNames.normalize(selected, channel);
        if (normalized.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<ChatSource> single = new ArrayList<>();
        single.add(new ChatSource(selected, normalized));
        return single;
    }

    public static void setSources(List<ChatSource> newSources) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (ChatSource source : newSources) {
            if (source != null) {
                values.add(source.configValue());
            }
        }
        SOURCES.set(new ArrayList<>(values));
        SOURCES.save();
        List<ChatSource> parsed = new ArrayList<>();
        for (String value : values) {
            ChatSource source = ChatSource.parse(value);
            if (source != null) {
                parsed.add(source);
            }
        }
        sources = parsed;
    }

    public static boolean addSource(ChatSource source) {
        if (source == null) {
            return false;
        }
        List<ChatSource> current = new ArrayList<>(effectiveSources());
        for (ChatSource existing : current) {
            if (existing.key().equals(source.key())) {
                return false;
            }
        }
        current.add(source);
        setSources(current);
        return true;
    }

    public static boolean removeSource(ChatPlatform platform, String channel) {
        if (platform == null) {
            return false;
        }
        String normalized = channel == null ? "" : ChannelNames.normalize(platform, channel);
        List<ChatSource> current = new ArrayList<>(effectiveSources());
        boolean changed = false;
        for (int index = current.size() - 1; index >= 0; index--) {
            ChatSource source = current.get(index);
            if (source.platform() == platform
                    && (normalized.isEmpty() || source.channel().equalsIgnoreCase(normalized))) {
                current.remove(index);
                changed = true;
            }
        }
        if (changed) {
            setSources(current);
        }
        return changed;
    }

    public static void setEmotesEnabled(boolean value) {
        EMOTES_ENABLED.set(value);
        EMOTES_ENABLED.save();
        emotesEnabled = value;
    }

    public static void setEmotesAnimated(boolean value) {
        EMOTES_ANIMATED.set(value);
        EMOTES_ANIMATED.save();
        emotesAnimated = value;
    }

    public static void setEmotesSize(int value) {
        int clamped = Math.max(8, Math.min(28, value));
        EMOTES_SIZE.set(clamped);
        EMOTES_SIZE.save();
        emotesSize = clamped;
    }

    public static void setOverlayEnabled(boolean value) {
        OVERLAY_ENABLED.set(value);
        OVERLAY_ENABLED.save();
        overlayEnabled = value;
    }

    public static int colorFor(ChatPlatform platform) {
        if (platform == ChatPlatform.KICK) {
            return overlayKickColor;
        }
        if (platform == ChatPlatform.TIKTOK) {
            return overlayTikTokColor;
        }
        return overlayTwitchColor;
    }

    public static void setOverlayTextOpacity(double value) {
        double clamped = Math.max(0.05D, Math.min(1.0D, value));
        OVERLAY_TEXT_OPACITY.set(clamped);
        OVERLAY_TEXT_OPACITY.save();
        overlayTextOpacity = clamped;
    }

    public static void setOverlayBackgroundOpacity(double value) {
        double clamped = Math.max(0.0D, Math.min(0.8D, value));
        OVERLAY_BACKGROUND.set(clamped);
        OVERLAY_BACKGROUND.save();
        overlayBackground = clamped;
    }

    public static boolean setAnchor(String value) {
        if (!isValidAnchor(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        OVERLAY_ANCHOR.set(normalized);
        OVERLAY_ANCHOR.save();
        overlayAnchor = normalized;
        return true;
    }

    public static void setMargins(int x, int y) {
        int clampedX = Math.max(0, Math.min(400, x));
        int clampedY = Math.max(0, Math.min(400, y));
        OVERLAY_MARGIN_X.set(clampedX);
        OVERLAY_MARGIN_X.save();
        OVERLAY_MARGIN_Y.set(clampedY);
        OVERLAY_MARGIN_Y.save();
        overlayMarginX = clampedX;
        overlayMarginY = clampedY;
    }

    public static void setOverlayScale(double value) {
        double clamped = Math.max(0.5D, Math.min(2.0D, value));
        OVERLAY_SCALE.set(clamped);
        OVERLAY_SCALE.save();
        overlayScale = clamped;
    }

    public static void setOverlayWidth(int value) {
        int clamped = Math.max(120, Math.min(800, value));
        OVERLAY_WIDTH.set(clamped);
        OVERLAY_WIDTH.save();
        overlayWidth = clamped;
    }

    public static boolean setColor(String target, String hex) {
        Integer parsed = TextUtil.parseColor(hex);
        if (parsed == null) {
            return false;
        }
        int rgb = parsed;
        String normalized = target == null ? "" : target.trim().toLowerCase(Locale.ROOT);
        if ("twitch".equals(normalized)) {
            OVERLAY_TWITCH_COLOR.set(hexString(rgb));
            OVERLAY_TWITCH_COLOR.save();
            overlayTwitchColor = rgb;
        } else if ("kick".equals(normalized)) {
            OVERLAY_KICK_COLOR.set(hexString(rgb));
            OVERLAY_KICK_COLOR.save();
            overlayKickColor = rgb;
        } else if ("tiktok".equals(normalized)) {
            OVERLAY_TIKTOK_COLOR.set(hexString(rgb));
            OVERLAY_TIKTOK_COLOR.save();
            overlayTikTokColor = rgb;
        } else if ("message".equals(normalized)) {
            OVERLAY_MESSAGE_COLOR.set(hexString(rgb));
            OVERLAY_MESSAGE_COLOR.save();
            overlayMessageColor = rgb;
        } else if ("system".equals(normalized)) {
            OVERLAY_SYSTEM_COLOR.set(hexString(rgb));
            OVERLAY_SYSTEM_COLOR.save();
            overlaySystemColor = rgb;
        } else if ("error".equals(normalized)) {
            OVERLAY_ERROR_COLOR.set(hexString(rgb));
            OVERLAY_ERROR_COLOR.save();
            overlayErrorColor = rgb;
        } else if ("background".equals(normalized)) {
            OVERLAY_BACKGROUND_COLOR.set(hexString(rgb));
            OVERLAY_BACKGROUND_COLOR.save();
            overlayBackgroundColor = rgb;
        } else if ("separator".equals(normalized)) {
            OVERLAY_SEPARATOR_COLOR.set(hexString(rgb));
            OVERLAY_SEPARATOR_COLOR.save();
            overlaySeparatorColor = rgb;
        } else {
            return false;
        }        return true;
    }

    public static void setMessageFormat(String value) {
        String format = value == null || value.trim().isEmpty() ? "{tag}{name}: {message}" : value;
        OVERLAY_MESSAGE_FORMAT.set(format);
        OVERLAY_MESSAGE_FORMAT.save();
        overlayMessageFormat = format;
    }

    public static void setPlatformTagFormat(String value) {
        String format = value == null ? "" : value;
        OVERLAY_PLATFORM_TAG.set(format);
        OVERLAY_PLATFORM_TAG.save();
        overlayPlatformTagFormat = format;
    }

    public static void setSystemPrefix(String value) {
        String prefix = value == null ? "" : value;
        OVERLAY_SYSTEM_PREFIX.set(prefix);
        OVERLAY_SYSTEM_PREFIX.save();
        overlaySystemPrefix = prefix;
    }

    private static String hexString(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb);
    }

    private static int colorOrDefault(String value, int fallback) {
        Integer parsed = TextUtil.parseColor(value);
        return parsed == null ? fallback : parsed;
    }

    private static boolean isValidAnchor(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "top_left".equals(normalized) || "top_right".equals(normalized)
                || "bottom_left".equals(normalized) || "bottom_right".equals(normalized);
    }

    private static String normalizeAnchor(String value) {
        return isValidAnchor(value) ? value.trim().toLowerCase(Locale.ROOT) : "top_left";
    }

    public static void save() {
        SPEC.save();
    }
}
