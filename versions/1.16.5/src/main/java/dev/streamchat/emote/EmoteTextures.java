package dev.streamchat.emote;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.renderer.texture.NativeImage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import dev.streamchat.ChatConfig;
import net.minecraft.client.Minecraft;


import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;



import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Descarga, decodifica y registra las texturas de los emotes. La descarga y
 * la decodificacion ocurren fuera del hilo del juego; la subida a GPU se hace
 * en el tick del cliente.
 */
public final class EmoteTextures {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final int MAX_TEXTURES_PER_TICK = 4;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private static final Map<String, ResourceLocation> STATIC_TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, AnimatedSet> ANIMATED_TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, int[]> DIMENSIONS = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static final ConcurrentLinkedQueue<PendingUpload> UPLOADS = new ConcurrentLinkedQueue<>();

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "SimplifyChat-Emotes");
        thread.setDaemon(true);
        return thread;
    });

    private EmoteTextures() {
    }

    public static ResourceLocation textureFor(Emote emote) {
        if (emote == null || !ChatConfig.emotesEnabled) {
            return null;
        }
        String key = key(emote);
        AnimatedSet animated = ANIMATED_TEXTURES.get(key);
        if (animated != null) {
            return animated.currentFrame();
        }
        ResourceLocation existing = STATIC_TEXTURES.get(key);
        if (existing != null) {
            return existing;
        }
        if (FAILED.contains(key)) {
            return null;
        }
        if (PENDING.add(key)) {
            EXECUTOR.execute(() -> downloadAndDecode(emote));
        }
        return null;
    }

    public static boolean render(MatrixStack matrixStack, Emote emote,
                                 int x, int y, int width, int height, int alpha) {
        ResourceLocation texture = textureFor(emote);
        if (texture == null) {
            return false;
        }
        int[] size = DIMENSIONS.getOrDefault(key(emote), new int[]{emote.width(), emote.height()});
        int textureWidth = Math.max(1, size[0]);
        int textureHeight = Math.max(1, size[1]);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        minecraft.getTextureManager().bind(texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, Math.max(0, Math.min(255, alpha)) / 255.0F);
        AbstractGui.blit(matrixStack, x, y, 0.0F, 0.0F, width, height, textureWidth, textureHeight);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        return true;
    }

    public static boolean isReady(Emote emote) {
        if (emote == null) {
            return false;
        }
        String key = key(emote);
        return STATIC_TEXTURES.containsKey(key) || ANIMATED_TEXTURES.containsKey(key);
    }

    public static void drainUploads() {
        int uploaded = 0;
        while (uploaded < MAX_TEXTURES_PER_TICK) {
            PendingUpload pending = UPLOADS.peek();
            if (pending == null) {
                break;
            }
            int cost = Math.max(1, pending.decoded.textureCount());
            if (uploaded > 0 && uploaded + cost > MAX_TEXTURES_PER_TICK) {
                break;
            }
            UPLOADS.poll();
            uploaded += upload(pending);
        }
    }

    private static int upload(PendingUpload pending) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            pending.decoded.close();
            return 0;
        }
        try {
            String key = key(pending.emote);
            EmoteImageCodec.Decoded decoded = pending.decoded;
            if (decoded.animated() && ChatConfig.emotesAnimated) {
                return uploadAnimated(minecraft, key, decoded);
            }
            NativeImage image = decoded.image();
            if (image == null && decoded.frames() != null && !decoded.frames().isEmpty()) {
                image = decoded.frames().get(0).image();
                decoded.frames().get(0).image();
            }
            if (image == null) {
                FAILED.add(key);
                return 0;
            }
            ResourceLocation id = textureId(key, "");
            DynamicTexture texture = new DynamicTexture(image);
            minecraft.getTextureManager().register(id, texture);
            STATIC_TEXTURES.put(key, id);
            DIMENSIONS.put(key, new int[]{image.getWidth(), image.getHeight()});
            return 1;
        } catch (Exception exception) {
            FAILED.add(key(pending.emote));
            LOGGER.warn("No se pudo registrar el emote {}", pending.emote.code(), exception);
            pending.decoded.close();
            return 0;
        }
    }

    private static int uploadAnimated(Minecraft minecraft, String key, EmoteImageCodec.Decoded decoded) {
        List<ResourceLocation> ids = new ArrayList<>(decoded.frames().size());
        List<Integer> delays = new ArrayList<>(decoded.frames().size());
        for (int index = 0; index < decoded.frames().size(); index++) {
            final int frameIndex = index;
            EmoteImageCodec.Frame frame = decoded.frames().get(index);
            ResourceLocation id = textureId(key, "_f" + index);
            DynamicTexture texture = new DynamicTexture(
                    frame.image());
            minecraft.getTextureManager().register(id, texture);
            ids.add(id);
            delays.add(frame.delayMs());
            if (index == 0) {
                DIMENSIONS.put(key, new int[]{frame.image().getWidth(), frame.image().getHeight()});
            }
        }
        ANIMATED_TEXTURES.put(key, new AnimatedSet(ids, delays));
        STATIC_TEXTURES.remove(key);
        return ids.size();
    }

    private static ResourceLocation textureId(String key, String suffix) {
        return new ResourceLocation("streamchat", "emotes/" + key + suffix);
    }

    private static void downloadAndDecode(Emote emote) {
        String key = key(emote);
        try {
            byte[] bytes = loadFromCache(key);
            if (bytes == null || bytes.length == 0) {
                bytes = download(emote.url());
                if (bytes == null || bytes.length == 0) {
                    FAILED.add(key);
                    return;
                }
                saveToCache(key, bytes);
            }
            EmoteImageCodec.Decoded decoded = EmoteImageCodec.decode(bytes);
            if (decoded == null || decoded.textureCount() == 0) {
                FAILED.add(key);
                return;
            }
            UPLOADS.add(new PendingUpload(emote, decoded));
        } catch (Exception exception) {
            FAILED.add(key);
            LOGGER.debug("No se pudo descargar el emote {}: {}", emote.code(), exception.getMessage());
        }
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "image/png,image/gif,image/*;q=0.8");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                int total = 0;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > 4 * 1024 * 1024) {
                        return null;
                    }
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static Path cacheDirectory() {
        Minecraft minecraft = Minecraft.getInstance();
        Path base = minecraft != null && minecraft.gameDirectory != null
                ? minecraft.gameDirectory.toPath()
                : Path.of(".");
        return base.resolve("config").resolve("streamchat-emotes");
    }

    private static Path cacheFile(String key) {
        return cacheDirectory().resolve(key + ".bin");
    }

    private static byte[] loadFromCache(String key) {
        try {
            Path path = cacheFile(key);
            return Files.isRegularFile(path) ? Files.readAllBytes(path) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void saveToCache(String key, byte[] bytes) {
        try {
            Path path = cacheFile(key);
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (Exception ignored) {
        }
    }

    private static String key(Emote emote) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(emote.url().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                builder.append(String.format(Locale.ROOT, "%02x", value));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return Integer.toHexString(emote.url().hashCode());
        }
    }

    private static final class PendingUpload {
        private final Emote emote;
        private final EmoteImageCodec.Decoded decoded;

        private PendingUpload(Emote emote, EmoteImageCodec.Decoded decoded) {
            this.emote = emote;
            this.decoded = decoded;
        }

        private Emote emote() {
            return emote;
        }

        private EmoteImageCodec.Decoded decoded() {
            return decoded;
        }
    }

    private static final class AnimatedSet {
        private final List<ResourceLocation> frames;
        private final List<Integer> delays;
        private final long totalDuration;
        private final long startedAt;

        private AnimatedSet(List<ResourceLocation> frames, List<Integer> delays) {
            this.frames = List.copyOf(frames);
            this.delays = List.copyOf(delays);
            long total = 0L;
            for (int delay : delays) {
                total += Math.max(20, delay);
            }
            this.totalDuration = Math.max(20L, total);
            this.startedAt = System.currentTimeMillis();
        }

        private ResourceLocation currentFrame() {
            if (frames.isEmpty()) {
                return null;
            }
            if (frames.size() == 1 || !ChatConfig.emotesAnimated) {
                return frames.get(0);
            }
            long elapsed = (System.currentTimeMillis() - startedAt) % totalDuration;
            long cursor = 0L;
            for (int index = 0; index < frames.size(); index++) {
                cursor += Math.max(20, delays.get(index));
                if (elapsed < cursor) {
                    return frames.get(index);
                }
            }
            return frames.get(0);
        }
    }
}
