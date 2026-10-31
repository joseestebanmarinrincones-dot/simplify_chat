package dev.streamchat.chat;

import dev.streamchat.ChatConfig;
import dev.streamchat.ChatMessage;
import dev.streamchat.ChatPlatform;
import dev.streamchat.TextUtil;
import net.minecraft.util.text.Color;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.StringTextComponent;

public final class MessageFormatter {
    private MessageFormatter() {
    }

    public static IFormattableTextComponent format(ChatMessage message) {
        ChatPlatform platform = message.platform();
        int platformColor = ChatConfig.colorFor(platform);
        int separatorColor = ChatConfig.overlaySeparatorColor;

        String name = TextUtil.cleanName(message.user(), 32);
        if (name.trim().isEmpty()) {
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
        if (format == null || format.trim().isEmpty()) {
            format = "{tag}{name}: {message}";
        }

        IFormattableTextComponent result = new StringTextComponent("");
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
            if ("tag".equals(key) || "platformTag".equals(key)) {
                result.append(literal(tag, platformColor));
            } else if ("platform".equals(key)) {
                result.append(literal(platform.displayName(), platformColor));
            } else if ("name".equals(key) || "user".equals(key)) {
                result.append(literal(name, nameColor));
            } else if ("message".equals(key)) {
                result.append(literal(body, ChatConfig.overlayMessageColor));
                usedMessage = true;
            } else {
                result.append(literal(format.substring(open, close + 1), separatorColor));
            }
            index = close + 1;
        }

        if (!usedMessage) {
            result.append(literal(" ", separatorColor));
            result.append(literal(body, ChatConfig.overlayMessageColor));
        }
        return result;
    }

    private static IFormattableTextComponent literal(String text, int rgb) {
        return new StringTextComponent(text).withStyle(style -> style.withColor(Color.fromRgb(rgb)));
    }
}
