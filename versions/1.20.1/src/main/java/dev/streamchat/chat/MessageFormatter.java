package dev.streamchat.chat;

import dev.streamchat.ChatConfig;
import dev.streamchat.ChatMessage;
import dev.streamchat.ChatPlatform;
import dev.streamchat.TextUtil;
import dev.streamchat.emote.Emote;
import dev.streamchat.emote.EmoteManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Convierte un mensaje de plataforma en partes de texto/emote.
 * El formato y los colores salen de ChatConfig.
 *
 * Placeholders: {tag}, {platform}, {name}, {message}
 */
public final class MessageFormatter {
    private MessageFormatter() {
    }

    public static MutableComponent format(ChatMessage message) {
        MutableComponent result = Component.empty();
        for (ChatPart part : parts(message)) {
            if (part instanceof ChatPart.Text text) {
                result.append(literal(text.text(), text.color()));
            } else if (part instanceof ChatPart.Emote emote) {
                result.append(literal(emote.code(), ChatConfig.overlayMessageColor));
            }
        }
        return result;
    }

    public static List<ChatPart> parts(ChatMessage message) {
        ChatPlatform platform = message.platform();
        int platformColor = ChatConfig.colorFor(platform);
        int separatorColor = ChatConfig.overlaySeparatorColor;

        String name = TextUtil.cleanName(message.user(), 32);
        if (name.isBlank()) {
            name = platform.displayName();
        }

        Integer userColor = ChatConfig.showNameColors ? TextUtil.parseColor(message.colorHex()) : null;
        int nameColor = userColor == null ? platformColor : userColor;

        String body = TextUtil.cleanMessage(message.message(), ChatConfig.maxMessageLength);
        String tag = ChatConfig.showPlatformTag ? ChatConfig.overlayPlatformTagFormat : "";
        if (tag == null) {
            tag = "";
        }
        tag = tag.replace("{platform}", platform.displayName());

        String format = ChatConfig.overlayMessageFormat;
        if (format == null || format.isBlank()) {
            format = "{tag}{name}: {message}";
        }

        List<ChatPart> result = new ArrayList<>();
        boolean usedMessage = false;
        int index = 0;

        while (index < format.length()) {
            int open = format.indexOf('{', index);
            if (open < 0) {
                result.add(new ChatPart.Text(format.substring(index), separatorColor));
                break;
            }
            if (open > index) {
                result.add(new ChatPart.Text(format.substring(index, open), separatorColor));
            }

            int close = format.indexOf('}', open + 1);
            if (close < 0) {
                result.add(new ChatPart.Text(format.substring(open), separatorColor));
                break;
            }

            String key = format.substring(open + 1, close);
            switch (key) {
                case "tag", "platformTag" -> result.add(new ChatPart.Text(tag, platformColor));
                case "platform" -> result.add(new ChatPart.Text(platform.displayName(), platformColor));
                case "name", "user" -> result.add(new ChatPart.Text(name, nameColor));
                case "message" -> {
                    appendMessage(result, body, ChatConfig.overlayMessageColor);
                    usedMessage = true;
                }
                default -> result.add(new ChatPart.Text(format.substring(open, close + 1), separatorColor));
            }
            index = close + 1;
        }

        // Si el formato no incluye {message}, no perder el mensaje.
        if (!usedMessage) {
            result.add(new ChatPart.Text(" ", separatorColor));
            appendMessage(result, body, ChatConfig.overlayMessageColor);
        }

        return result;
    }

    private static void appendMessage(List<ChatPart> target, String body, int color) {
        if (body == null || body.isEmpty()) {
            return;
        }
        int index = 0;
        while (index < body.length()) {
            int start = index;
            boolean space = Character.isWhitespace(body.charAt(index));
            while (index < body.length() && Character.isWhitespace(body.charAt(index)) == space) {
                index++;
            }
            String token = body.substring(start, index);
            if (!space) {
                Emote emote = EmoteManager.resolve(token);
                if (emote != null) {
                    target.add(new ChatPart.Emote(token));
                    continue;
                }
            }
            target.add(new ChatPart.Text(token, color));
        }
    }

    private static MutableComponent literal(String text, int rgb) {
        return Component.literal(text).withStyle(style -> style.withColor(TextColor.fromRgb(rgb)));
    }
}
