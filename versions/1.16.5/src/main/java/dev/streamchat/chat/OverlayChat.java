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
        addLine(new Line(message, null, 0, System.currentTimeMillis(), system, error));
    }

    public static void addSystem(String text, int color, boolean error) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        addLine(new Line(null, text, color, System.currentTimeMillis(), true, error));
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

    public static final class Line {
        private final ChatMessage message;
        private final String text;
        private final int color;
        private final long createdAt;
        private final boolean system;
        private final boolean error;

        public Line(ChatMessage message, String text, int color, long createdAt,
                    boolean system, boolean error) {
            this.message = message;
            this.text = text;
            this.color = color;
            this.createdAt = createdAt;
            this.system = system;
            this.error = error;
        }

        public ChatMessage message() {
            return message;
        }

        public String text() {
            return text;
        }

        public int color() {
            return color;
        }

        public long createdAt() {
            return createdAt;
        }

        public boolean system() {
            return system;
        }

        public boolean error() {
            return error;
        }
    }
}
