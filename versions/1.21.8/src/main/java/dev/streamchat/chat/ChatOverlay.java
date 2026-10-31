package dev.streamchat.chat;

import dev.streamchat.ChatConfig;
import dev.streamchat.emote.Emote;
import dev.streamchat.emote.EmoteManager;
import dev.streamchat.emote.EmoteTextures;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;


import net.minecraft.util.Mth;



import java.util.ArrayList;
import java.util.List;

/**
 * Overlay transparente dibujado sobre el HUD. El estilo sigue la idea de
 * Transparent Twitch Chat Overlay: sin fondo por defecto, texto con sombra,
 * mensajes apilados y desvanecimiento suave.
 */
public final class ChatOverlay implements ForgeLayer {
    private static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath("streamchat", "chat_overlay");
    public static void register(ForgeLayeredDraw draw) {
        draw.addAbove(ForgeLayeredDraw.POST_SLEEP_STACK, LAYER_ID, ForgeLayeredDraw.CHAT_OVERLAY, new ChatOverlay());
    }
    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        draw(graphics);
    }

    private static final int LINE_SPACING = 2;
    private static final int FADE_IN_MS = 220;
    private static final int FADE_OUT_MS = 1200;

    private void draw(GuiGraphics graphics) {
        if (!ChatConfig.overlayEnabled) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        if (minecraft.screen != null || minecraft.options.hideGui) {
            return;
        }

        List<OverlayChat.Line> messages = OverlayChat.snapshot();
        if (messages.isEmpty()) {
            return;
        }

        Font font = minecraft.font;
        int width = Mth.clamp(ChatConfig.overlayWidth, 80, 800);
        long now = System.currentTimeMillis();
        long lifetime = Math.max(3, ChatConfig.overlayLifetimeSeconds) * 1000L;
        long fadeOut = Math.min(FADE_OUT_MS, lifetime / 3L);
        float textOpacity = (float) Mth.clamp(ChatConfig.overlayTextOpacity, 0.05D, 1.0D);
        float scale = (float) Mth.clamp(ChatConfig.overlayScale, 0.5D, 2.0D);
        int marginX = Math.max(0, ChatConfig.overlayMarginX);
        int marginY = Math.max(0, ChatConfig.overlayMarginY);

        List<MessageLayout> layouts = new ArrayList<>();
        for (OverlayChat.Line message : messages) {
            long age = Math.max(0L, now - message.createdAt());
            if (age > lifetime) {
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
            alpha = Mth.clamp(alpha, 0.0F, 1.0F) * textOpacity;
            if (alpha <= 0.02F) {
                continue;
            }

            List<LineLayout> lines = layout(font, width, message);
            if (!lines.isEmpty()) {
                layouts.add(new MessageLayout(lines, alpha, age));
            }
        }

        if (layouts.isEmpty()) {
            return;
        }

        int maxHeight = Math.max(font.lineHeight,
                Math.round((graphics.guiHeight() - marginY * 2) / Math.max(0.5F, scale)));
        while (totalHeight(layouts) > maxHeight) {
            MessageLayout oldest = layouts.get(0);
            if (oldest.lines.size() > 1) {
                oldest.lines.remove(oldest.lines.size() - 1);
            } else {
                layouts.remove(0);
            }
            if (layouts.isEmpty()) {
                return;
            }
        }

        int totalHeight = totalHeight(layouts);
        int contentWidth = layouts.stream()
                .flatMap(layout -> layout.lines.stream())
                .mapToInt(line -> line.width)
                .max()
                .orElse(width);
        contentWidth = Math.max(1, Math.min(contentWidth, width));
        int scaledWidth = Math.max(1, Math.round(contentWidth * scale));
        int scaledHeight = Math.max(1, Math.round(totalHeight * scale));

        String anchor = ChatConfig.overlayAnchor;
        boolean right = anchor != null && anchor.endsWith("right");
        boolean bottom = anchor != null && anchor.startsWith("bottom");

        int x = right ? graphics.guiWidth() - marginX - scaledWidth : marginX;
        int y = bottom ? graphics.guiHeight() - marginY - scaledHeight : marginY;
        x = Mth.clamp(x, 0, Math.max(0, graphics.guiWidth() - scaledWidth));
        y = Mth.clamp(y, 0, Math.max(0, graphics.guiHeight() - scaledHeight));

        double background = Mth.clamp(ChatConfig.overlayBackground, 0.0D, 0.8D);
        if (background > 0.001D) {
            int alpha = (int) (background * 255.0D);
            graphics.fill(x - 3, y - 3, x + scaledWidth + 3, y + scaledHeight + 3,
                    (alpha << 24) | (ChatConfig.overlayBackgroundColor & 0xFFFFFF));
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);

        int cursorY = 0;
        for (MessageLayout layout : layouts) {
            float reveal = Math.min(1.0F, layout.age / (float) FADE_IN_MS);
            float eased = 1.0F - (1.0F - reveal) * (1.0F - reveal) * (1.0F - reveal);
            int slide = Math.round((1.0F - eased) * -12.0F);
            int alpha = (int) (layout.alpha * 255.0F);

            for (LineLayout line : layout.lines) {
                int cursorX = 0;
                for (Atom atom : line.atoms) {
                    int atomY = cursorY + (line.contentHeight - atom.height);
                    if (atom.text != null) {
                        int color = (alpha << 24) | (atom.color & 0xFFFFFF);
                        graphics.drawString(font, atom.text, cursorX + slide, atomY, color, ChatConfig.overlayTextShadow);
                    } else {
                        EmoteTextures.render(graphics, atom.emote, cursorX + slide, atomY,
                                atom.width, atom.height, alpha);
                    }
                    cursorX += atom.width;
                }
                cursorY += line.contentHeight + LINE_SPACING;
            }
        }

        graphics.pose().popMatrix();
    }

    private static List<LineLayout> layout(Font font, int maxWidth, OverlayChat.Line line) {
        List<ChatPart> parts = line.message() != null
                ? MessageFormatter.parts(line.message())
                : List.of(new ChatPart.Text(line.text(), line.color()));

        LayoutState state = new LayoutState();
        for (ChatPart part : parts) {
            if (part instanceof ChatPart.Text text) {
                appendText(font, maxWidth, state, text);
            } else if (part instanceof ChatPart.Emote emotePart) {
                Emote emote = EmoteManager.resolve(emotePart.code());
                if (emote == null) {
                    appendText(font, maxWidth, state,
                            new ChatPart.Text(emotePart.code(), ChatConfig.overlayMessageColor));
                } else {
                    int size = Math.max(8, ChatConfig.emotesSize);
                    int emoteWidth = Math.max(4, Math.round(size * emote.aspect()));
                    if (state.current.hasContent && state.current.width + emoteWidth > maxWidth) {
                        state.newLine();
                    }
                    state.current.add(new Atom(null, 0, emote, emoteWidth, size));
                }
            }
        }
        return state.finish();
    }

    private static void appendText(Font font, int maxWidth, LayoutState state, ChatPart.Text part) {
        String text = part.text();
        if (text == null || text.isEmpty()) {
            return;
        }
        int index = 0;
        while (index < text.length()) {
            int start = index;
            boolean space = Character.isWhitespace(text.charAt(index));
            while (index < text.length() && Character.isWhitespace(text.charAt(index)) == space) {
                index++;
            }
            String token = text.substring(start, index);
            int tokenWidth = font.width(token);
            if (space) {
                if (state.current.hasContent && state.current.width + tokenWidth <= maxWidth) {
                    state.current.add(new Atom(token, part.color(), null, tokenWidth, font.lineHeight));
                }
                continue;
            }

            if (state.current.hasContent && state.current.width + tokenWidth > maxWidth) {
                state.newLine();
            }

            if (tokenWidth > maxWidth) {
                for (int offset = 0; offset < token.length(); offset++) {
                    String character = String.valueOf(token.charAt(offset));
                    int charWidth = font.width(character);
                    if (state.current.hasContent && state.current.width + charWidth > maxWidth) {
                        state.newLine();
                    }
                    state.current.add(new Atom(character, part.color(), null, charWidth, font.lineHeight));
                }
            } else {
                state.current.add(new Atom(token, part.color(), null, tokenWidth, font.lineHeight));
            }
        }
    }

    private static int totalHeight(List<MessageLayout> layouts) {
        int total = 0;
        for (MessageLayout layout : layouts) {
            for (LineLayout line : layout.lines) {
                total += line.contentHeight + LINE_SPACING;
            }
        }
        return Math.max(0, total - LINE_SPACING);
    }

    private static final class MessageLayout {
        private final List<LineLayout> lines;
        private final float alpha;
        private final long age;

        private MessageLayout(List<LineLayout> lines, float alpha, long age) {
            this.lines = new ArrayList<>(lines);
            this.alpha = alpha;
            this.age = age;
        }
    }

    private static final class LayoutState {
        private final List<LineLayout> lines = new ArrayList<>();
        private LineLayout current = new LineLayout();

        private void newLine() {
            if (current.hasContent) {
                lines.add(current);
                current = new LineLayout();
            }
        }

        private List<LineLayout> finish() {
            if (current.hasContent) {
                lines.add(current);
            }
            return lines;
        }
    }

    private static final class LineLayout {
        private final List<Atom> atoms = new ArrayList<>();
        private int width;
        private int contentHeight = 9;
        private boolean hasContent;

        private void add(Atom atom) {
            atoms.add(atom);
            width += atom.width;
            contentHeight = Math.max(contentHeight, atom.height);
            hasContent = true;
        }
    }

    private record Atom(String text, int color, Emote emote, int width, int height) {
    }
}
