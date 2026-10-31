package dev.streamchat.chat;

import dev.streamchat.ChatConfig;
import net.minecraft.network.chat.Component;

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

    public static void add(Component component, boolean system, boolean error) {
        long now = System.currentTimeMillis();
        LINES.addLast(new Line(component, now, system, error));

        int max = Math.max(1, ChatConfig.overlayMaxMessages);
        while (LINES.size() > max) {
            LINES.removeFirst();
        }
        prune(now);
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

    public record Line(Component component, long createdAt, boolean system, boolean error) {
    }
}
