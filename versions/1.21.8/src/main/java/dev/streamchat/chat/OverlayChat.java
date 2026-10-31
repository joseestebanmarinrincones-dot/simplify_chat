package dev.streamchat.chat;

import dev.streamchat.ChatConfig;
import dev.streamchat.ChatMessage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Buffer de mensajes del overlay. Solo se toca desde el hilo del cliente.
 */
public final class OverlayChat {
    private static final Deque<Line> LINES = new ArrayDeque<>();

    private OverlayChat() {
    }

    public static void add(ChatMessage message, boolean system, boolean error) {
        if (message == null) {
            return;
        }
        addLine(new Line(message, null, 0, 0L, system, error));
    }

    public static void addSystem(String text, int color, boolean error) {
        if (text == null || text.isBlank()) {
            return;
        }
        addLine(new Line(null, text, color, 0L, true, error));
    }

    private static void addLine(Line line) {
        LINES.addLast(line);
        int max = Math.max(1, ChatConfig.overlayMaxMessages);
        while (LINES.size() > max) {
            LINES.removeFirst();
        }
        prune(line.createdAt());
    }

    public static void prune() {
        prune(System.currentTimeMillis());
    }

    private static void prune(long now) {
        long lifetime = Math.max(3, ChatConfig.overlayLifetimeSeconds) * 1000L;
        LINES.removeIf(line -> now - line.createdAt() > lifetime);
    }

    public static List<Line> snapshot() {
        return new ArrayList<>(LINES);
    }

    public static void clear() {
        LINES.clear();
    }

    public record Line(ChatMessage message, String text, int color, long createdAt,
                       boolean system, boolean error) {
        public Line {
            createdAt = createdAt == 0L ? System.currentTimeMillis() : createdAt;
        }
    }
}
