package dev.streamchat;

import java.util.Locale;

public final class ChannelNames {
    private ChannelNames() {
    }

    /**
     * Acepta "canal", "@canal", "#canal" o una URL completa de Twitch/Kick/TikTok.
     */
    public static String normalize(ChatPlatform platform, String raw) {
        if (raw == null) {
            return "";
        }

        String value = raw.trim();
        if (value.isEmpty()) {
            return "";
        }

        value = value.replaceAll("(?i)^https?://", "");
        if (value.matches("(?i)^(www\\.)?(twitch\\.tv|kick\\.com|tiktok\\.com)/.*")) {
            value = value.substring(value.indexOf('/') + 1);
        }

        int cut = value.indexOf('/');
        if (cut >= 0) {
            value = value.substring(0, cut);
        }
        cut = value.indexOf('?');
        if (cut >= 0) {
            value = value.substring(0, cut);
        }
        cut = value.indexOf('#');
        if (cut >= 0) {
            value = value.substring(0, cut);
        }

        while (value.startsWith("@") || value.startsWith("#")) {
            value = value.substring(1);
        }

        value = value.trim();
        if (platform != ChatPlatform.TIKTOK) {
            value = value.toLowerCase(Locale.ROOT);
        }
        return value;
    }
}
