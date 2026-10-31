package dev.streamchat.emote;

import net.minecraft.client.renderer.texture.NativeImage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Decodifica PNG y GIF a NativeImage. Los GIF se componen frame a frame
 * respetando el rectangulo y el metodo de disposicion de cada frame.
 */
final class EmoteImageCodec {
    private static final int MAX_EDGE = 96;
    private static final int MAX_FRAMES = 24;

    static final class Frame {
        private final NativeImage image;
        private final int delayMs;

        Frame(NativeImage image, int delayMs) {
            this.image = image;
            this.delayMs = delayMs;
        }

        NativeImage image() {
            return image;
        }

        int delayMs() {
            return delayMs;
        }
    }

    static final class Decoded {
        private final NativeImage image;
        private final List<Frame> frames;

        Decoded(NativeImage image, List<Frame> frames) {
            this.image = image;
            this.frames = frames;
        }

        NativeImage image() {
            return image;
        }

        List<Frame> frames() {
            return frames;
        }

        boolean animated() {
            return frames != null && frames.size() > 1;
        }

        int textureCount() {
            return animated() ? frames.size() : (image != null ? 1 : 0);
        }

        void close() {
            if (image != null) {
                image.close();
            }
            if (frames != null) {
                for (Frame frame : frames) {
                    if (frame.image() != null) {
                        frame.image().close();
                    }
                }
            }
        }
    }

    static {
        ImageIO.setUseCache(false);
    }

    private EmoteImageCodec() {
    }

    static Decoded decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        if (isGif(bytes)) {
            Decoded gif = decodeGif(bytes);
            if (gif != null) {
                return gif;
            }
        }
        NativeImage image = decodeStatic(bytes);
        return image == null ? null : new Decoded(image, null);
    }

    static boolean isGif(byte[] bytes) {
        return bytes != null && bytes.length >= 6
                && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a';
    }

    private static NativeImage decodeStatic(byte[] bytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            NativeImage image = NativeImage.read(in);
            if (image != null && image.getWidth() <= MAX_EDGE && image.getHeight() <= MAX_EDGE) {
                return image;
            }
            if (image != null) {
                image.close();
            }
        } catch (Exception ignored) {
            // Intentamos con ImageIO (algunos PNG/GIF raros).
        }

        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage buffered = ImageIO.read(in);
            return buffered == null ? null : toNative(downscale(buffered));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Decoded decodeGif(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, false);
                int frameCount = reader.getNumImages(true);
                if (frameCount <= 0) {
                    return null;
                }

                int[] canvasSize = canvasSize(reader);
                int canvasWidth = Math.max(1, canvasSize[0]);
                int canvasHeight = Math.max(1, canvasSize[1]);
                BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
                BufferedImage previous = null;

                int step = frameCount <= MAX_FRAMES ? 1 : (int) Math.ceil(frameCount / (double) MAX_FRAMES);
                List<Frame> frames = new ArrayList<>(Math.min(frameCount, MAX_FRAMES));

                for (int index = 0; index < frameCount; index++) {
                    BufferedImage frame = reader.read(index);
                    if (frame == null) {
                        continue;
                    }
                    FrameMeta meta = frameMeta(reader.getImageMetadata(index));
                    if ("restoreToPrevious".equals(meta.disposal)) {
                        previous = copy(canvas);
                    }

                    Graphics2D graphics = canvas.createGraphics();
                    graphics.drawImage(frame, meta.left, meta.top, null);
                    graphics.dispose();

                    if (index % step == 0 || index == frameCount - 1) {
                        NativeImage nativeFrame = toNative(downscale(copy(canvas)));
                        if (nativeFrame != null) {
                            frames.add(new Frame(nativeFrame, Math.max(20, meta.delayMs * step)));
                        }
                    }

                    if ("restoreToBackgroundColor".equals(meta.disposal)) {
                        Graphics2D clear = canvas.createGraphics();
                        clear.setComposite(java.awt.AlphaComposite.Clear);
                        clear.fillRect(meta.left, meta.top, frame.getWidth(), frame.getHeight());
                        clear.dispose();
                    } else if ("restoreToPrevious".equals(meta.disposal) && previous != null) {
                        canvas = previous;
                        previous = null;
                    }
                }

                if (frames.isEmpty()) {
                    return null;
                }
                if (frames.size() == 1) {
                    return new Decoded(frames.get(0).image(), null);
                }
                return new Decoded(null, new ArrayList<>(frames));
            } finally {
                reader.dispose();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static NativeImage toNative(BufferedImage source) {
        if (source == null) {
            return null;
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(source, "png", out)) {
                return null;
            }
            try (ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray())) {
                return NativeImage.read(in);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BufferedImage downscale(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= MAX_EDGE && height <= MAX_EDGE) {
            return source;
        }
        float scale = Math.min(MAX_EDGE / (float) width, MAX_EDGE / (float) height);
        int newWidth = Math.max(1, Math.round(width * scale));
        int newHeight = Math.max(1, Math.round(height * scale));
        BufferedImage result = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, newWidth, newHeight, null);
        graphics.dispose();
        return result;
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return result;
    }

    private static int[] canvasSize(ImageReader reader) {
        try {
            IIOMetadata metadata = reader.getStreamMetadata();
            if (metadata != null) {
                String format = metadata.getNativeMetadataFormatName();
                if (format != null) {
                    Node node = metadata.getAsTree(format).getFirstChild();
                    while (node != null) {
                        if ("LogicalScreenDescriptor".equals(node.getNodeName())) {
                            NamedNodeMap attributes = node.getAttributes();
                            if (attributes != null) {
                                Node width = attributes.getNamedItem("logicalScreenWidth");
                                Node height = attributes.getNamedItem("logicalScreenHeight");
                                if (width != null && height != null) {
                                    return new int[]{
                                            Integer.parseInt(width.getNodeValue()),
                                            Integer.parseInt(height.getNodeValue())
                                    };
                                }
                            }
                            break;
                        }
                        node = node.getNextSibling();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new int[]{28, 28};
    }

    private static final class FrameMeta {
        private final int left;
        private final int top;
        private final int delayMs;
        private final String disposal;

        private FrameMeta(int left, int top, int delayMs, String disposal) {
            this.left = left;
            this.top = top;
            this.delayMs = delayMs;
            this.disposal = disposal;
        }
    }

    private static FrameMeta frameMeta(IIOMetadata metadata) {
        int left = 0;
        int top = 0;
        int delayMs = 100;
        String disposal = "none";
        if (metadata == null) {
            return new FrameMeta(left, top, delayMs, disposal);
        }
        try {
            String format = metadata.getNativeMetadataFormatName();
            if (format == null) {
                return new FrameMeta(left, top, delayMs, disposal);
            }
            Node node = metadata.getAsTree(format).getFirstChild();
            while (node != null) {
                NamedNodeMap attributes = node.getAttributes();
                if ("GraphicControlExtension".equals(node.getNodeName()) && attributes != null) {
                    Node delay = attributes.getNamedItem("delayTime");
                    if (delay != null) {
                        delayMs = Math.max(20, Integer.parseInt(delay.getNodeValue()) * 10);
                    }
                    Node method = attributes.getNamedItem("disposalMethod");
                    if (method != null) {
                        disposal = method.getNodeValue();
                    }
                } else if ("ImageDescriptor".equals(node.getNodeName()) && attributes != null) {
                    Node leftNode = attributes.getNamedItem("imageLeftPosition");
                    Node topNode = attributes.getNamedItem("imageTopPosition");
                    if (leftNode != null) {
                        left = Integer.parseInt(leftNode.getNodeValue());
                    }
                    if (topNode != null) {
                        top = Integer.parseInt(topNode.getNodeValue());
                    }
                }
                node = node.getNextSibling();
            }
        } catch (Exception ignored) {
        }
        return new FrameMeta(left, top, delayMs, disposal);
    }
}
