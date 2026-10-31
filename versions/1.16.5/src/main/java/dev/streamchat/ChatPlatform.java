package dev.streamchat;

import net.minecraft.util.text.TextFormatting;

import java.util.Locale;

public enum ChatPlatform {
    TWITCH("twitch", "Twitch", TextFormatting.LIGHT_PURPLE, 0x9146FF),
    KICK("kick", "Kick", TextFormatting.GREEN, 0x53FC18),
    TIKTOK("tiktok", "TikTok", TextFormatting.AQUA, 0x25F4EE);

    private final String id;
    private final String displayName;
    private final TextFormatting fallbackColor;
    private final int rgb;

    ChatPlatform(String id, String displayName, TextFormatting fallbackColor, int rgb) {
        this.id = id;
        this.displayName = displayName;
        this.fallbackColor = fallbackColor;
        this.rgb = rgb;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public TextFormatting fallbackColor() {
        return fallbackColor;
    }

    public int rgb() {
        return rgb;
    }

    public static ChatPlatform byId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (ChatPlatform platform : values()) {
            if (platform.id.equals(normalized)) {
                return platform;
            }
        }
        return null;
    }

    public static ChatPlatform byIdOrDefault(String id) {
        ChatPlatform platform = byId(id);
        return platform == null ? TWITCH : platform;
    }
}
