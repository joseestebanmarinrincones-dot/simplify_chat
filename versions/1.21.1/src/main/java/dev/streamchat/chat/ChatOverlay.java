package dev.streamchat.chat;

import dev.streamchat.ChatConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public final class ChatOverlay implements LayeredDraw.Layer {
    private static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath("streamchat", "chat_overlay");
    private static final int LINE_SPACING = 2;
    private static final int FADE_IN_MS = 220;
    private static final int FADE_OUT_MS = 1200;

    private ChatOverlay() {
    }

    public static void register(ForgeLayeredDraw draw) {
        draw.addAbove(ForgeLayeredDraw.POST_SLEEP_STACK, LAYER_ID, ForgeLayeredDraw.CHAT_OVERLAY, new ChatOverlay());
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        draw(graphics);
    }

    private void draw(GuiGraphics graphics) {
        if (!ChatConfig.overlayEnabled) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui) {
            return;
        }

        List<OverlayChat.Line> messages = OverlayChat.snapshot();
        if (messages.isEmpty()) {
            return;
        }

        Font font = minecraft.font;
        int width = Math.max(80, Math.min(800, ChatConfig.overlayWidth));
        int lineHeight = font.lineHeight + LINE_SPACING;
        long now = System.currentTimeMillis();
        long lifetime = Math.max(3, ChatConfig.overlayLifetimeSeconds) * 1000L;
        long fadeOut = Math.min(FADE_OUT_MS, lifetime / 3L);

        List<Layout> layouts = new ArrayList<>();
        for (OverlayChat.Line message : messages) {
            long age = Math.max(0L, now - message.createdAt());
            if (age > lifetime) {
                continue;
            }
            List<FormattedCharSequence> lines = font.split(message.component(), width);
            if (lines.isEmpty()) {
                continue;
            }
            float alpha = 1.0F;
            if (age < FADE_IN_MS) {
                alpha = age / (float) FADE_IN_MS;
            }
            long fadeStart = lifetime - fadeOut;
            if (age > fadeStart) {
                alpha = Math.min(alpha, (lifetime - age) / (float) fadeOut);
            }
            alpha = Math.max(0.0F, Math.min(1.0F, alpha)) * (float) ChatConfig.overlayTextOpacity;
            if (alpha <= 0.02F) {
                continue;
            }
            layouts.add(new Layout(new ArrayList<>(lines), alpha, age));
        }

        if (layouts.isEmpty()) {
            return;
        }

        int maxHeight = Math.max(lineHeight, graphics.guiHeight() - ChatConfig.overlayMarginY * 2);
        int totalLines = layouts.stream().mapToInt(layout -> layout.lines.size()).sum();
        while (totalLines * lineHeight > maxHeight && !layouts.isEmpty()) {
            Layout oldest = layouts.get(0);
            if (oldest.lines.size() > 1) {
                oldest.lines.remove(oldest.lines.size() - 1);
                totalLines--;
            } else {
                layouts.remove(0);
                totalLines--;
            }
        }
        if (layouts.isEmpty()) {
            return;
        }

        int totalHeight = totalLines * lineHeight;
        float scale = (float) Math.max(0.5D, Math.min(2.0D, ChatConfig.overlayScale));
        int contentWidth = layouts.stream()
                .flatMap(layout -> layout.lines.stream())
                .mapToInt(font::width)
                .max()
                .orElse(width);
        contentWidth = Math.min(contentWidth, width);
        int scaledWidth = Math.max(1, Math.round(contentWidth * scale));
        int scaledHeight = Math.round(totalHeight * scale);

        String anchor = ChatConfig.overlayAnchor;
        boolean right = anchor != null && anchor.endsWith("right");
        boolean bottom = anchor != null && anchor.startsWith("bottom");
        int marginX = Math.max(0, ChatConfig.overlayMarginX);
        int marginY = Math.max(0, ChatConfig.overlayMarginY);
        int x = right ? graphics.guiWidth() - marginX - scaledWidth : marginX;
        int y = bottom ? graphics.guiHeight() - marginY - scaledHeight : marginY;
        x = Math.max(0, Math.min(x, Math.max(0, graphics.guiWidth() - scaledWidth)));
        y = Math.max(0, Math.min(y, Math.max(0, graphics.guiHeight() - scaledHeight)));

        double background = Math.max(0.0D, Math.min(0.8D, ChatConfig.overlayBackground));
        if (background > 0.001D) {
            int alpha = (int) (background * 255.0D);
            graphics.fill(x - 3, y - 3, x + scaledWidth + 3, y + scaledHeight + 3,
                    (alpha << 24) | ChatConfig.overlayBackgroundColor);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);

        int cursorY = 0;
        for (Layout layout : layouts) {
            float reveal = Math.min(1.0F, layout.age / (float) FADE_IN_MS);
            float eased = 1.0F - (1.0F - reveal) * (1.0F - reveal) * (1.0F - reveal);
            int slide = Math.round((1.0F - eased) * -12.0F);
            int color = ((int) (layout.alpha * 255.0F) << 24) | 0xFFFFFF;
            for (FormattedCharSequence line : layout.lines) {
                graphics.drawString(font, line, slide, cursorY, color, ChatConfig.overlayTextShadow);
                cursorY += lineHeight;
            }
        }

        graphics.pose().popPose();
    }

    private static final class Layout {
        private final List<FormattedCharSequence> lines;
        private final float alpha;
        private final long age;

        private Layout(List<FormattedCharSequence> lines, float alpha, long age) {
            this.lines = lines;
            this.alpha = alpha;
            this.age = age;
        }
    }
}
