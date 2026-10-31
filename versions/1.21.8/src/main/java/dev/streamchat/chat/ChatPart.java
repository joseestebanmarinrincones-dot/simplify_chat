package dev.streamchat.chat;

/**
 * Fragmento de una linea del overlay. Los emotes se resuelven a textura en
 * el momento de dibujar, asi que si la imagen termina de cargarse despues,
 * la linea se actualiza sola.
 */
public sealed interface ChatPart {
    record Text(String text, int color) implements ChatPart {
    }

    record Emote(String code) implements ChatPart {
    }
}
