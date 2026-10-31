package dev.streamchat.chat;

import dev.streamchat.ChatConfig;
import dev.streamchat.ChatMessage;
import dev.streamchat.ChatPlatform;
import dev.streamchat.TextUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

/**
 * Convierte un mensaje de plataforma en el Component que pinta el overlay.
 * El formato y los colores salen de ChatConfig.
 *
 * Placeholders: {tag}, {platform}, {name}, {message}
 */
public final class MessageFormatter {
    private MessageFormatter() {
    }

    public static MutableComponent format(ChatMessage message) {
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

        MutableComponent result = Component.empty();
        boolean usedMessage = false;
        int index = 0;

        while (index < format.length()) {
            int open = format.indexOf('{', index);
            if (open < 0) {
                result.append(literal(format.substring(index), separatorColor));
                break;
            }
            if (open > index) {
                result.append(literal(format.substring(index, open), separatorColor));
            }

            int close = format.indexOf('}', open + 1);
            if (close < 0) {
                result.append(literal(format.substring(open), separatorColor));
                break;
            }

            String key = format.substring(open + 1, close);
            switch (key) {
                case "tag", "platformTag" -> result.append(literal(tag, platformColor));
                case "platform" -> result.append(literal(platform.displayName(), platformColor));
                case "name", "user" -> result.append(literal(name, nameColor));
                case "message" -> {
                    result.append(literal(body, ChatConfig.overlayMessageColor));
                    usedMessage = true;
                }
                default -> result.append(literal(format.substring(open, close + 1), separatorColor));
            }
            index = close + 1;
        }

        // Si el formato no incluye {message}, no perder el mensaje.
        if (!usedMessage) {
            result.append(literal(" ", separatorColor));
            result.append(literal(body, ChatConfig.overlayMessageColor));
        }

        return result;
    }

    private static MutableComponent literal(String text, int rgb) {
        return Component.literal(text).withStyle(style -> style.withColor(TextColor.fromRgb(rgb)));
    }
}
