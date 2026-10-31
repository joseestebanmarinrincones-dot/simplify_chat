package dev.streamchat.emote;

/**
 * Metadatos de un emote de BTTV, FFZ o 7TV.
 */
public final class Emote {
    private final String code;
    private final String url;
    private final int width;
    private final int height;
    private final String provider;

    public Emote(String code, String url, int width, int height, String provider) {
        this.code = code;
        this.url = url;
        this.width = width;
        this.height = height;
        this.provider = provider;
    }

    public String code() {
        return code;
    }

    public String url() {
        return url;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public String provider() {
        return provider;
    }

    public float aspect() {
        if (height <= 0) {
            return 1.0F;
        }
        return Math.max(0.2F, Math.min(5.0F, width / (float) height));
    }
}
