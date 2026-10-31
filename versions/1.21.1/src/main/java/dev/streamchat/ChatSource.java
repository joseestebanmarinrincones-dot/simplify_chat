package dev.streamchat;

import java.util.Locale;

/**
 * Una fuente de chat configurada. Se guarda en el TOML como
 * "plataforma:canal", por ejemplo "twitch:ibai" o "tiktok:usuario".
 */
public record ChatSource(ChatPlatform platform, String channel) {
    public ChatSource {
        if (platform == null) {
            platform = ChatPlatform.TWITCH;
        }
        channel = channel == null ? "" : channel.trim();
    }

    public static ChatSource parse(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        int separator = value.indexOf(':');
        if (separator <= 0 || separator >= value.length() - 1) {
            return null;
        }
        ChatPlatform platform = ChatPlatform.byId(value.substring(0, separator));
        if (platform == null) {
            return null;
        }
        String channel = ChannelNames.normalize(platform, value.substring(separator + 1));
        if (channel.isEmpty()) {
            return null;
        }
        return new ChatSource(platform, channel);
    }

    public String key() {
        return platform.id() + ":" + channel.toLowerCase(Locale.ROOT);
    }

    public String configValue() {
        return platform.id() + ":" + channel;
    }

    public String displayChannel() {
        return platform == ChatPlatform.TIKTOK ? "@" + channel : channel;
    }

    public String display() {
        return platform.displayName() + ": " + displayChannel();
    }
}
