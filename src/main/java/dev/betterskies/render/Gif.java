package dev.betterskies.render;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Reads animated GIF frames, compositing partial frames the way browsers do. */
final class Gif {
    private Gif() {
    }

    static List<BufferedImage> read(Path file, int maxFrames) throws IOException {
        List<BufferedImage> out = new ArrayList<>();
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IOException("not a readable GIF");
            }
            ImageReader reader = readers.next();
            reader.setInput(in, false);
            int count = reader.getNumImages(true);
            int step = Math.max(1, (int) Math.ceil(count / (double) maxFrames));
            int w = -1, h = -1;
            BufferedImage canvas = null;
            for (int i = 0; i < count; i++) {
                BufferedImage frame = reader.read(i);
                IIOMetadata meta = reader.getImageMetadata(i);
                int x = 0, y = 0;
                String disposal = "none";
                Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
                for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
                    NamedNodeMap at = n.getAttributes();
                    if ("ImageDescriptor".equals(n.getNodeName())) {
                        x = Integer.parseInt(at.getNamedItem("imageLeftPosition").getNodeValue());
                        y = Integer.parseInt(at.getNamedItem("imageTopPosition").getNodeValue());
                    } else if ("GraphicControlExtension".equals(n.getNodeName())) {
                        disposal = at.getNamedItem("disposalMethod").getNodeValue();
                    }
                }
                if (canvas == null) {
                    w = Math.max(frame.getWidth() + x, reader.getWidth(0));
                    h = Math.max(frame.getHeight() + y, reader.getHeight(0));
                    canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                }
                BufferedImage before = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;
                Graphics2D g = canvas.createGraphics();
                g.drawImage(frame, x, y, null);
                g.dispose();
                if (i % step == 0) {
                    out.add(copy(canvas));
                }
                if ("restoreToBackgroundColor".equals(disposal)) {
                    Graphics2D c = canvas.createGraphics();
                    c.setComposite(java.awt.AlphaComposite.Clear);
                    c.fillRect(x, y, frame.getWidth(), frame.getHeight());
                    c.dispose();
                } else if (before != null) {
                    canvas = before;
                }
            }
            reader.dispose();
        }
        if (out.isEmpty()) {
            throw new IOException("the GIF has no frames");
        }
        return out;
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = c.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return c;
    }
}
