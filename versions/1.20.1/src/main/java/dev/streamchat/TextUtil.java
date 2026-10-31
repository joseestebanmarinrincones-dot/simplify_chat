package dev.streamchat;

import java.util.Locale;

public final class TextUtil {
    private TextUtil() {
    }

    /**
     * Limpia el texto que llega de una plataforma externa antes de meterlo
     * en un Component. Nunca se interpretan codigos de formato de Minecraft.
     */
    public static String cleanMessage(String input, int maxLength) {
        if (input == null) {
            return "";
        }

        // Kick manda los emotes como [emote:123:nombre]; se muestran como :nombre:
        String text = input.replaceAll("\\[emote:\\d+:([^\\]]+)]", ":$1:");

        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7') {
                // Saltar el codigo de color y el caracter que lo sigue.
                i++;
                continue;
            }
            if (c == '\r' || c == '\n' || c == '\t') {
                out.append(' ');
            } else if (c >= 0x20 && c != 0x7f) {
                out.append(c);
            }
        }

        String result = out.toString().replaceAll("\\s{2,}", " ").trim();
        if (result.length() > maxLength) {
            result = result.substring(0, Math.max(0, maxLength - 1)) + "\u2026";
        }
        return result;
    }

    public static String cleanName(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\u00a7') {
                i++;
                continue;
            }
            if (c >= 0x20 && c != 0x7f) {
                out.append(c);
            }
        }
        String result = out.toString().trim();
        if (result.length() > maxLength) {
            result = result.substring(0, maxLength);
        }
        return result;
    }

    public static Integer parseColor(String hex) {
        if (hex == null) {
            return null;
        }
        String value = hex.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != 6) {
            return null;
        }
        try {
            return Integer.parseInt(value, 16);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static String normalizeChannelKey(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
