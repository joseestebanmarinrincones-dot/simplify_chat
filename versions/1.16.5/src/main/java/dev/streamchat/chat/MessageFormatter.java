package dev.streamchat.chat;

import dev.streamchat.ChatConfig;
import dev.streamchat.ChatMessage;
import dev.streamchat.ChatPlatform;
import dev.streamchat.TextUtil;
import dev.streamchat.emote.Emote;
import dev.streamchat.emote.EmoteManager;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Convierte un mensaje de plataforma en partes de texto/emote.
 * Placeholders: {tag}, {platform}, {name}, {message}
 */
public final class MessageFormatter {
    private MessageFormatter() {
    }

    public static ITextComponent format(ChatMessage message) {
        StringTextComponent result = new StringTextComponent("");
        for (ChatPart part : parts(message)) {
            if (part instanceof ChatPart.Text) {
                ChatPart.Text text = (ChatPart.Text) part;
                result.append(literal(text.text(), text.color()));
            } else if (part instanceof ChatPart.Emote) {
                ChatPart.Emote emote = (ChatPart.Emote) part;
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
            if ("tag".equals(key) || "platformTag".equals(key)) {
                result.add(new ChatPart.Text(tag, platformColor));
            } else if ("platform".equals(key)) {
                result.add(new ChatPart.Text(platform.displayName(), platformColor));
            } else if ("name".equals(key) || "user".equals(key)) {
                result.add(new ChatPart.Text(name, nameColor));
            } else if ("message".equals(key)) {
                appendMessage(result, body, ChatConfig.overlayMessageColor);
                usedMessage = true;
            } else {
                result.add(new ChatPart.Text(format.substring(open, close + 1), separatorColor));
            }
            index = close + 1;
        }

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

    private static StringTextComponent literal(String text, int rgb) {
        StringTextComponent component = new StringTextComponent(text);
        component.setStyle(component.getStyle().withColor(Color.fromRgb(rgb)));
        return component;
    }
}
