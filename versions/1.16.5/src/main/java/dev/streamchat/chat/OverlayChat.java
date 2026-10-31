package dev.streamchat.chat;

import dev.streamchat.ChatConfig;
import net.minecraft.util.text.ITextComponent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class OverlayChat {
    private static final Deque<Line> LINES = new ArrayDeque<>();

    private OverlayChat() {
    }

    public static void add(ITextComponent component, boolean system, boolean error) {
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

    public static final class Line {
        private final ITextComponent component;
        private final long createdAt;
        private final boolean system;
        private final boolean error;

        public Line(ITextComponent component, long createdAt, boolean system, boolean error) {
            this.component = component;
            this.createdAt = createdAt;
            this.system = system;
            this.error = error;
        }

        public ITextComponent component() {
            return component;
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
