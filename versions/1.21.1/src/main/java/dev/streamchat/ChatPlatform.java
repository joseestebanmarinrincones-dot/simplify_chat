package dev.streamchat;

import net.minecraft.ChatFormatting;

import java.util.Locale;

public enum ChatPlatform {
    TWITCH("twitch", "Twitch", ChatFormatting.DARK_PURPLE, 0x9146FF),
    KICK("kick", "Kick", ChatFormatting.GREEN, 0x53FC18),
    TIKTOK("tiktok", "TikTok", ChatFormatting.AQUA, 0x25F4EE);

    private final String id;
    private final String displayName;
    private final ChatFormatting fallbackColor;
    private final int rgb;

    ChatPlatform(String id, String displayName, ChatFormatting fallbackColor, int rgb) {
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

    public ChatFormatting fallbackColor() {
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
