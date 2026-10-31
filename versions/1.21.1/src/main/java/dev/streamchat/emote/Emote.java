package dev.streamchat.emote;

/**
 * Metadatos de un emote de BTTV, FFZ o 7TV.
 *
 * @param code     texto que se escribe en el chat
 * @param url      URL de la imagen (PNG o GIF)
 * @param width    ancho original
 * @param height   alto original
 * @param provider proveedor que lo aporta
 */
public record Emote(String code, String url, int width, int height, String provider) {
    public float aspect() {
        if (height <= 0) {
            return 1.0F;
        }
        return Math.max(0.2F, Math.min(5.0F, width / (float) height));
    }
}
