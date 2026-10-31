package dev.streamchat.chat;

import dev.streamchat.ChatMessage;

public interface ChatListener {
    void onMessage(ChatMessage message);

    void onStatus(String status);

    void onError(String error);
}
