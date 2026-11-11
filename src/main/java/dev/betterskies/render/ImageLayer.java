package dev.betterskies.render;

import dev.betterskies.BetterSkies;
import dev.betterskies.config.Grade;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import dev.betterskies.util.Colors;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Sky pictures: still or animated (GIF / numbered frames, crossfaded), with
 * gentle motion: drift (turning), flow (contents ripple and roll) and shimmer
 * (waves of brightness). Glow pictures are stored as "light with alpha" so
 * every mode draws through the same alpha-blended pipeline.
 */
final class ImageLayer {
    private ImageLayer() {
    }

    private record Key(String name, int maxWidth, double fade, int tint, int grade, boolean glow, long stamp) {
    }

    private static final Async<Key, List<ImageLibrary.Picture>> job = new Async<>("Sky image");
    private static final List<Gpu.Tex> frames = new ArrayList<>();
    private static final Noise NOISE = new Noise(4711);
    private static final Geometry dome = new Geometry();

    static boolean busy() {
        return job.busy();
    }

    static String error() {
        return job.error();
    }

    static void draw(Gpu.Mesh sphere, Profile p, SkyState s, Grade grade, Matrix4f camera, Matrix4f celestial,
                     double time, long folderStamp, double r) {
        String name = Settings.IMAGE.get(p);
        if (name.isEmpty()) {
            if (!frames.isEmpty()) {
                release();
            }
            return;
        }
        int maxW = Settings.QUALITY.is(p, "LOW") ? 2048 : 4096;
        boolean gr = Settings.GRADE_IMAGES.on(p);
        boolean glow = Settings.IMAGE_BLEND.is(p, "ADD");
        double fade = Settings.IMAGE_HORIZON_FADE.v(p);
        int tint = Settings.IMAGE_TINT.rgb(p);
        Grade g = gr ? grade : Grade.NONE;
        Key key = new Key(name, maxW, fade, tint, gr ? Objects.hash(grade.apply(0x808080), grade.apply(0xFF4020),
                grade.apply(0x2040FF), grade.apply(0x202020), grade.apply(0xE0E0E0)) : 0, glow, folderStamp);
        ImageLibrary lib = BetterSkies.images();
        List<ImageLibrary.Picture> pics = job.get(key, () -> {
            ImageLibrary.Entry e = lib.find(name);
            if (e == null) {
                throw new IllegalStateException("\"" + name + "\" is no longer in the images folder");
            }
            try {
                List<ImageLibrary.Picture> out = new ArrayList<>();
                for (ImageLibrary.Picture pic : lib.loadFrames(e, maxW)) {
                    out.add(process(pic, fade, tint, g, glow));
                }
                return out;
            } catch (java.io.IOException ex) {
                throw new IllegalStateException(ex.getMessage(), ex);
            }
        });
        if (job.takeFresh()) {
            release();
            if (pics != null) {
                for (int i = 0; i < pics.size(); i++) {
                    ImageLibrary.Picture pic = pics.get(i);
                    frames.add(Gpu.texture("image " + i, pic.argb(), pic.width(), pic.height()));
                }
            }
        }
        if (frames.isEmpty()) {
            return;
        }
        double vis = switch (Settings.IMAGE_SHOW.get(p)) {
            case "NIGHT" -> s.darkness;
            case "DAY" -> 1 - s.darkness;
            default -> 1;
        } * Settings.IMAGE_OPACITY.v(p);
        if (vis < 0.003) {
            return;
        }
        Matrix4f m = new Matrix4f(Settings.IMAGE_FOLLOW_SUN.on(p) ? celestial : camera);
        double spin = Settings.IMAGE_SPIN.v(p) * time * 0.02;
        m.rotateY((float) (-Math.toRadians(Settings.IMAGE_ROTATION.v(p)) - spin % SkyMath.TAU));

        // which frame(s) to show
        int n = frames.size();
        int a = 0, b = -1;
        double mix = 0;
        if (n > 1) {
            double f = time * Settings.IMAGE_FPS.v(p);
            a = (int) Math.floorMod((long) Math.floor(f), n);
            if (Settings.IMAGE_FRAME_BLEND.on(p)) {
                b = (a + 1) % n;
                mix = f - Math.floor(f);
            }
        }
        double flow = Settings.IMAGE_FLOW.v(p), shimmer = Settings.IMAGE_SHIMMER.v(p);
        boolean moving = (flow > 0 || shimmer > 0) && Settings.EFFECTS.on(p);
        if (moving) {
            buildDome(flow, shimmer, time * (0.05 + Settings.IMAGE_FLOW_SPEED.v(p) * 0.5), r);
        }
        drawFrame(sphere, moving, m, frames.get(a), vis);
        if (b >= 0 && mix > 0.003) {
            drawFrame(sphere, moving, m, frames.get(b), vis * mix);
        }
    }

    private static void drawFrame(Gpu.Mesh sphere, boolean moving, Matrix4f m, Gpu.Tex tex, double alpha) {
        Vector4f tint = new Vector4f(1, 1, 1, (float) alpha);
        if (moving) {
            Gpu.drawOnce(dome, Gpu.alpha(), m, tint, tex.view(), Gpu.sampler(true), "moving image");
        } else {
            Gpu.draw(sphere, m, tint, tex.view(), Gpu.sampler(true));
        }
    }

    /**
     * A sphere whose texture coordinates gently swirl over time (flow) and whose
     * brightness ripples (shimmer). Noise is sampled around a circle in u, so the
     * seam at u = 0/1 always matches.
     */
    private static void buildDome(double flow, double shimmer, double t, double r) {
        dome.clear();
        int lon = 72, lat = 36;
        double[][] uv = new double[(lon + 1) * (lat + 1)][];
        int[] col = new int[(lon + 1) * (lat + 1)];
        for (int j = 0; j <= lat; j++) {
            double v = (double) j / lat;
            // keep the poles steady so the picture doesn't pinch
            double keep = Math.sin(Math.PI * v);
            for (int i = 0; i <= lon; i++) {
                double u = (double) i / lon;
                double cx = Math.cos(u * SkyMath.TAU) * 2.2, sx = Math.sin(u * SkyMath.TAU) * 2.2;
                double nu = NOISE.noise(cx + t * 0.7, sx, v * 4 + t * 0.35);
                double nv = NOISE.noise(cx - 3.1, sx + t * 0.5, v * 4 + 7.3 - t * 0.3);
                double du = flow * 0.018 * nu * keep;
                double dv = flow * 0.012 * nv * keep;
                uv[j * (lon + 1) + i] = new double[]{u + du, Math.max(0, Math.min(1, v + dv))};
                double wave = 0.5 + 0.5 * NOISE.noise(cx * 2.5 + t * 1.6, sx * 2.5, v * 9 - t * 1.1);
                double a = 1 - shimmer * 0.75 * (1 - wave * wave);
                col[j * (lon + 1) + i] = Colors.argbf(a, 0xFFFFFF);
            }
        }
        for (int j = 0; j < lat; j++) {
            for (int i = 0; i < lon; i++) {
                int i00 = j * (lon + 1) + i, i10 = i00 + 1, i01 = i00 + lon + 1, i11 = i01 + 1;
                double u0 = (double) i / lon, u1 = (double) (i + 1) / lon, v0 = (double) j / lat, v1 = (double) (j + 1) / lat;
                dome.quad(SkyMath.scale(SkyMath.dirFromUv(u0, v0), r), uv[i00][0], uv[i00][1], col[i00],
                        SkyMath.scale(SkyMath.dirFromUv(u1, v0), r), uv[i10][0], uv[i10][1], col[i10],
                        SkyMath.scale(SkyMath.dirFromUv(u1, v1), r), uv[i11][0], uv[i11][1], col[i11],
                        SkyMath.scale(SkyMath.dirFromUv(u0, v1), r), uv[i01][0], uv[i01][1], col[i01]);
            }
        }
    }

    /** Tint, grade and horizon fade; glow pictures become "light with alpha". */
    private static ImageLibrary.Picture process(ImageLibrary.Picture pic, double fade, int tint, Grade grade, boolean glow) {
        int w = pic.width(), h = pic.height();
        int[] out = new int[w * h];
        boolean tinted = (tint & 0xFFFFFF) != 0xFFFFFF;
        for (int y = 0; y < h; y++) {
            double el = (0.5 - (y + 0.5) / h) * 180;
            double fa = fade <= 0 ? 1 : SkyMath.smooth(-2, 4 + fade * 40, el);
            for (int x = 0; x < w; x++) {
                int c = pic.argb()[y * w + x];
                int rgb = c & 0xFFFFFF;
                if (tinted) {
                    rgb = Colors.rgb(Colors.r(rgb) * Colors.r(tint) / 255, Colors.g(rgb) * Colors.g(tint) / 255, Colors.b(rgb) * Colors.b(tint) / 255);
                }
                if (!grade.neutral()) {
                    rgb = glow ? grade.applyEmissive(rgb) : grade.apply(rgb);
                }
                double alpha = ((c >>> 24) & 255) / 255.0 * fa;
                if (glow) {
                    int m = Math.max(Colors.r(rgb), Math.max(Colors.g(rgb), Colors.b(rgb)));
                    if (m == 0) {
                        out[y * w + x] = 0;
                        continue;
                    }
                    alpha *= m / 255.0;
                    rgb = Colors.rgb(Colors.r(rgb) * 255 / m, Colors.g(rgb) * 255 / m, Colors.b(rgb) * 255 / m);
                }
                out[y * w + x] = Colors.argbf(alpha, rgb);
            }
        }
        return new ImageLibrary.Picture(w, h, out);
    }

    static void release() {
        for (Gpu.Tex t : frames) {
            t.close();
        }
        frames.clear();
    }

    static void forget() {
        release();
        job.clear();
    }
}
