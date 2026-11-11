package dev.betterskies.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Sky pictures in config/betterskies/images. Every format is converted to one
 * equirectangular (2:1) picture so the renderer has a single path:
 * <ul>
 *   <li>2:1 panoramas (.png / .jpg)</li>
 *   <li>3:2 cube sheets (OptiFine/MCPatcher layout: bottom, top, south / west, north, east)</li>
 *   <li>folders or .zip files with six faces named *north, *south, *east, *west, *top (up), *bottom (down)</li>
 * </ul>
 * Pure Java (ImageIO), safe on a background thread.
 */
public final class ImageLibrary {
    public record Entry(String name, String kind, Path path) {
    }

    public record Picture(int width, int height, int[] argb) {
    }

    private static final Pattern FACE = Pattern.compile("(?i)^(?:.*?[_\\-. ])?(north|south|east|west|top|bottom|up|down|px|nx|py|ny|pz|nz)\\.(png|jpe?g)$");
    private static final String[] NAMES = {"north", "east", "south", "west", "top", "bottom"};
    /** For each face: centre, right, up (viewer inside the box, looking at the face). */
    private static final double[][][] BASIS = {
            {{0, 0, -1}, {1, 0, 0}, {0, 1, 0}},
            {{1, 0, 0}, {0, 0, 1}, {0, 1, 0}},
            {{0, 0, 1}, {-1, 0, 0}, {0, 1, 0}},
            {{-1, 0, 0}, {0, 0, -1}, {0, 1, 0}},
            {{0, 1, 0}, {1, 0, 0}, {0, 0, 1}},
            {{0, -1, 0}, {1, 0, 0}, {0, 0, -1}},
    };

    private final Path dir;

    public ImageLibrary(Path dir) {
        this.dir = dir;
    }

    public Path folder() {
        return dir;
    }

    public static final String BUILTIN_CLOUDS = "Drifting clouds (built-in)";
    public static final String BUILTIN_AURORA = "Aurora veil (built-in)";
    private static final Pattern FRAME = Pattern.compile("(?i)^(.*?)(\\d+)\\.(png|jpe?g)$");

    public static boolean isBuiltin(String name) {
        return BUILTIN_CLOUDS.equals(name) || BUILTIN_AURORA.equals(name);
    }

    public List<Entry> scan() {
        List<Entry> out = new ArrayList<>();
        List<Entry> builtins = List.of(new Entry(BUILTIN_CLOUDS, "Built-in", null), new Entry(BUILTIN_AURORA, "Built-in", null));
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.toList()) {
                String n = f.getFileName().toString();
                String lower = n.toLowerCase(Locale.ROOT);
                if (Files.isDirectory(f)) {
                    List<Path> inside = listDir(f);
                    if (countFaces(inside) == 6) {
                        out.add(new Entry(n, "Six faces", f));
                    } else if (countFrames(inside.stream().map(x -> x.getFileName().toString()).toList()) >= 2) {
                        out.add(new Entry(n, "Animated", f));
                    }
                } else if (lower.endsWith(".gif")) {
                    out.add(new Entry(stripExt(n), "Animated", f));
                } else if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                    out.add(new Entry(stripExt(n), "Picture", f));
                } else if (lower.endsWith(".zip")) {
                    out.add(new Entry(stripExt(n), "Zip", f));
                }
            }
        } catch (IOException ignored) {
            // folder missing: no images
        }
        out.sort(Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT)));
        List<Entry> all = new ArrayList<>(builtins);
        all.addAll(out);
        return all;
    }

    public Entry find(String name) {
        for (Entry e : scan()) {
            if (e.name().equals(name)) {
                return e;
            }
        }
        return null;
    }

    /** Copies dropped/imported files into the images folder. Returns a short report. */
    public String importFiles(List<Path> files) {
        int ok = 0;
        List<String> skipped = new ArrayList<>();
        for (Path f : files) {
            String n = f.getFileName().toString();
            String lower = n.toLowerCase(Locale.ROOT);
            try {
                if (Files.isDirectory(f) || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".zip")
                        || lower.endsWith(".gif")) {
                    Path target = dir.resolve(n);
                    if (Files.isDirectory(f)) {
                        Files.createDirectories(target);
                        try (Stream<Path> s = Files.list(f)) {
                            for (Path c : s.toList()) {
                                if (Files.isRegularFile(c)) {
                                    Files.copy(c, target.resolve(c.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                                }
                            }
                        }
                    } else {
                        Files.copy(f, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    ok++;
                } else {
                    skipped.add(n);
                }
            } catch (IOException e) {
                skipped.add(n);
            }
        }
        String msg = "Imported " + ok + " sky picture" + (ok == 1 ? "" : "s");
        if (!skipped.isEmpty()) {
            msg += " (skipped " + skipped.size() + ": not .png/.jpg/.gif/.zip)";
        }
        return msg;
    }

    // ================================================================== loading

    public static final int MAX_FRAMES = 32;

    /**
     * Loads an entry as one or more equirectangular frames (animated GIFs and
     * numbered frame sequences give several; everything else gives one).
     */
    public List<Picture> loadFrames(Entry e, int maxWidth) throws IOException {
        if (e.path() == null) {
            return List.of(BuiltinPanoramas.generate(e.name(), maxWidth));
        }
        String lower = e.path().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".gif")) {
            List<BufferedImage> frames = Gif.read(e.path(), MAX_FRAMES);
            List<Picture> out = new ArrayList<>();
            for (BufferedImage f : frames) {
                out.add(fromImage(f, Math.min(maxWidth, 1024)));
            }
            return out;
        }
        if (Files.isDirectory(e.path()) && countFaces(listDir(e.path())) != 6) {
            List<Path> files = new ArrayList<>(listDir(e.path()).stream().filter(f -> FRAME.matcher(f.getFileName().toString()).matches()).toList());
            files.sort(Comparator.comparingLong(f -> frameNumber(f.getFileName().toString())));
            List<Picture> out = new ArrayList<>();
            for (Path f : sample(files)) {
                out.add(fromImage(read(Files.newInputStream(f)), Math.min(maxWidth, 1024)));
            }
            return out;
        }
        if (lower.endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(e.path().toFile())) {
                List<ZipEntry> frames = new ArrayList<>(zip.stream().filter(z -> !z.isDirectory()).filter(z -> {
                    String leaf = z.getName().substring(z.getName().replace('\\', '/').lastIndexOf('/') + 1);
                    return FRAME.matcher(leaf).matches() && faceOf(leaf) == null;
                }).toList());
                if (frames.size() >= 2) {
                    frames.sort(Comparator.comparingLong(z -> frameNumber(z.getName().substring(z.getName().replace('\\', '/').lastIndexOf('/') + 1))));
                    List<Picture> out = new ArrayList<>();
                    for (ZipEntry z : sample(frames)) {
                        out.add(fromImage(read(zip.getInputStream(z)), Math.min(maxWidth, 1024)));
                    }
                    return out;
                }
            }
        }
        return List.of(load(e, maxWidth));
    }

    private static <T> List<T> sample(List<T> all) {
        if (all.size() <= MAX_FRAMES) {
            return all;
        }
        List<T> out = new ArrayList<>();
        for (int i = 0; i < MAX_FRAMES; i++) {
            out.add(all.get(i * all.size() / MAX_FRAMES));
        }
        return out;
    }

    private static long frameNumber(String name) {
        Matcher m = FRAME.matcher(name);
        return m.matches() ? Long.parseLong(m.group(2).length() > 18 ? m.group(2).substring(0, 18) : m.group(2)) : 0;
    }

    private static int countFrames(List<String> names) {
        int n = 0;
        for (String s : names) {
            if (FRAME.matcher(s).matches() && faceOf(s) == null) {
                n++;
            }
        }
        return n;
    }

    /** Loads an entry as an equirectangular picture at most maxWidth wide. */
    public Picture load(Entry e, int maxWidth) throws IOException {
        if (e.path() == null) {
            return BuiltinPanoramas.generate(e.name(), maxWidth);
        }
        if (Files.isDirectory(e.path())) {
            Map<String, BufferedImage> faces = new HashMap<>();
            for (Path f : listDir(e.path())) {
                String face = faceOf(f.getFileName().toString());
                if (face != null) {
                    faces.put(face, read(Files.newInputStream(f)));
                }
            }
            return fromFaces(faces, maxWidth);
        }
        if (e.path().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(e.path().toFile())) {
                Map<String, BufferedImage> faces = new HashMap<>();
                BufferedImage single = null;
                for (ZipEntry z : zip.stream().filter(z -> !z.isDirectory()).toList()) {
                    String name = z.getName().replace('\\', '/');
                    String leaf = name.substring(name.lastIndexOf('/') + 1);
                    String lower = leaf.toLowerCase(Locale.ROOT);
                    if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) {
                        continue;
                    }
                    String face = faceOf(leaf);
                    if (face != null) {
                        faces.put(face, read(zip.getInputStream(z)));
                    } else if (single == null) {
                        single = read(zip.getInputStream(z));
                    }
                }
                if (faces.size() == 6) {
                    return fromFaces(faces, maxWidth);
                }
                if (single != null) {
                    return fromImage(single, maxWidth);
                }
                throw new IOException("the zip has no sky picture or six cube faces");
            }
        }
        return fromImage(read(Files.newInputStream(e.path())), maxWidth);
    }

    private static BufferedImage read(InputStream in) throws IOException {
        try (in) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                throw new IOException("unsupported picture format");
            }
            return img;
        }
    }

    private Picture fromImage(BufferedImage img, int maxWidth) throws IOException {
        int w = img.getWidth(), h = img.getHeight();
        double aspect = (double) w / h;
        if (Math.abs(aspect - 1.5) < 0.02) {
            int s = w / 3;
            Map<String, BufferedImage> faces = new HashMap<>();
            faces.put("bottom", img.getSubimage(0, 0, s, s));
            faces.put("top", img.getSubimage(s, 0, s, s));
            faces.put("south", img.getSubimage(2 * s, 0, s, s));
            faces.put("west", img.getSubimage(0, s, s, s));
            faces.put("north", img.getSubimage(s, s, s, s));
            faces.put("east", img.getSubimage(2 * s, s, s, s));
            return fromFaces(faces, maxWidth);
        }
        int[] src = img.getRGB(0, 0, w, h, null, 0, w);
        int ow = Math.min(maxWidth, Math.max(64, w)), oh = ow / 2;
        int[] out = new int[ow * oh];
        for (int y = 0; y < oh; y++) {
            for (int x = 0; x < ow; x++) {
                double sx = (x + 0.5) / ow * w - 0.5, sy = (y + 0.5) / oh * h - 0.5;
                out[y * ow + x] = bilinear(src, w, h, sx, sy, true);
            }
        }
        return new Picture(ow, oh, out);
    }

    private Picture fromFaces(Map<String, BufferedImage> faces, int maxWidth) throws IOException {
        int size = -1;
        int[][] px = new int[6][];
        for (int i = 0; i < 6; i++) {
            BufferedImage img = faces.get(NAMES[i]);
            if (img == null) {
                throw new IOException("missing the " + NAMES[i] + " face");
            }
            if (size < 0) {
                size = Math.min(img.getWidth(), img.getHeight());
            }
            px[i] = scaleSquare(img, size);
        }
        int w = Math.min(maxWidth, size * 4), h = w / 2;
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double[] d = SkyMath.dirFromUv((x + 0.5) / w, (y + 0.5) / h);
                int best = 0;
                double bestK = -2;
                for (int f = 0; f < 6; f++) {
                    double k = SkyMath.dot(d, BASIS[f][0]);
                    if (k > bestK) {
                        bestK = k;
                        best = f;
                    }
                }
                double a = SkyMath.dot(d, BASIS[best][1]) / bestK;
                double b = SkyMath.dot(d, BASIS[best][2]) / bestK;
                out[y * w + x] = bilinear(px[best], size, size, (a + 1) / 2 * size - 0.5, (1 - b) / 2 * size - 0.5, false);
            }
        }
        return new Picture(w, h, out);
    }

    private static int[] scaleSquare(BufferedImage img, int size) {
        int w = img.getWidth(), h = img.getHeight();
        int[] src = img.getRGB(0, 0, w, h, null, 0, w);
        if (w == size && h == size) {
            return src;
        }
        int[] out = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                out[y * size + x] = bilinear(src, w, h, (x + 0.5) / size * w - 0.5, (y + 0.5) / size * h - 0.5, false);
            }
        }
        return out;
    }

    /** Downscaled copy for editor thumbnails. */
    public static int[] thumbnail(Picture p, int tw, int th) {
        int[] out = new int[tw * th];
        for (int y = 0; y < th; y++) {
            for (int x = 0; x < tw; x++) {
                // average a few samples per thumbnail pixel
                long a = 0, r = 0, g = 0, b = 0;
                int n = 0;
                for (int sy = 0; sy < 3; sy++) {
                    for (int sx = 0; sx < 3; sx++) {
                        int px = (int) ((x + (sx + 0.5) / 3) / tw * p.width());
                        int py = (int) ((y + (sy + 0.5) / 3) / th * p.height());
                        int c = p.argb()[Math.min(p.height() - 1, py) * p.width() + Math.min(p.width() - 1, px)];
                        a += (c >>> 24) & 255;
                        r += (c >> 16) & 255;
                        g += (c >> 8) & 255;
                        b += c & 255;
                        n++;
                    }
                }
                out[y * tw + x] = (int) (a / n) << 24 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
            }
        }
        return out;
    }

    // ================================================================== helpers

    private static List<Path> listDir(Path d) {
        try (Stream<Path> s = Files.list(d)) {
            return s.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static int countFaces(List<Path> files) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Path f : files) {
            String face = faceOf(f.getFileName().toString());
            if (face != null) {
                seen.add(face);
            }
        }
        return seen.size();
    }

    static String faceOf(String fileName) {
        Matcher m = FACE.matcher(fileName);
        if (!m.matches()) {
            return null;
        }
        return switch (m.group(1).toLowerCase(Locale.ROOT)) {
            case "up", "py" -> "top";
            case "down", "ny" -> "bottom";
            case "px" -> "east";
            case "nx" -> "west";
            case "pz" -> "south";
            case "nz" -> "north";
            default -> m.group(1).toLowerCase(Locale.ROOT);
        };
    }

    private static String stripExt(String n) {
        int i = n.lastIndexOf('.');
        return i > 0 ? n.substring(0, i) : n;
    }

    private static int bilinear(int[] px, int w, int h, double s, double t, boolean wrapX) {
        t = Math.max(0, Math.min(h - 1.001, t));
        if (!wrapX) {
            s = Math.max(0, Math.min(w - 1.001, s));
        }
        int x0 = (int) Math.floor(s), y0 = (int) t;
        double fx = s - x0, fy = t - y0;
        int x1 = x0 + 1;
        if (wrapX) {
            x0 = Math.floorMod(x0, w);
            x1 = Math.floorMod(x1, w);
        }
        int c00 = px[y0 * w + x0], c10 = px[y0 * w + x1];
        int c01 = px[(y0 + 1) * w + x0], c11 = px[(y0 + 1) * w + x1];
        int out = 0;
        for (int sh = 0; sh <= 24; sh += 8) {
            double v00 = (c00 >>> sh) & 255, v10 = (c10 >>> sh) & 255, v01 = (c01 >>> sh) & 255, v11 = (c11 >>> sh) & 255;
            double v = (v00 * (1 - fx) + v10 * fx) * (1 - fy) + (v01 * (1 - fx) + v11 * fx) * fy;
            out |= ((int) Math.round(v) & 255) << sh;
        }
        return out;
    }
}
