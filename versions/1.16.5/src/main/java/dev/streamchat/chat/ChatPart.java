package dev.streamchat.chat;

/**
 * Fragmento de una linea del overlay. Los emotes se resuelven a textura en
 * el momento de dibujar, asi que si la imagen termina de cargarse despues,
 * la linea se actualiza sola.
 */
public interface ChatPart {
    final class Text implements ChatPart {
        private final String text;
        private final int color;

        public Text(String text, int color) {
            this.text = text;
            this.color = color;
        }

        public String text() {
            return text;
        }

        public int color() {
            return color;
        }
    }

    final class Emote implements ChatPart {
        private final String code;

        public Emote(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
