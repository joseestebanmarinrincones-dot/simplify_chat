package dev.streamchat;

public final class ChatMessage {
    private final ChatPlatform platform;
    private final String user;
    private final String message;
    private final String colorHex;

    public ChatMessage(ChatPlatform platform, String user, String message, String colorHex) {
        this.platform = platform;
        this.user = user;
        this.message = message;
        this.colorHex = colorHex;
    }

    public ChatPlatform platform() {
        return platform;
    }

    public String user() {
        return user;
    }

    public String message() {
        return message;
    }

    public String colorHex() {
        return colorHex;
    }
}
