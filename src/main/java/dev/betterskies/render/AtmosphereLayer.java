package dev.betterskies.render;

import dev.betterskies.config.Grade;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import dev.betterskies.util.Colors;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Objects;

/** The "Real atmosphere" sky: a physically scattered sky texture recomputed as the sun moves. */
final class AtmosphereLayer {
    private AtmosphereLayer() {
    }

    private record Key(long sx, long sy, long sz, PhysicalSky.Params params, int w, int gradeKey, int rainQ, int thunderQ) {
    }

    private record Baked(int w, int h, int[] argb, double[] sun) {
    }

    private static final Async<Key, Baked> job = new Async<>("Atmosphere",
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "BetterSkies atmosphere");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }));
    private static Gpu.Tex tex;
    private static Baked shown;

    static boolean busy() {
        return job.busy();
    }

    /** Draws the atmosphere over the gradient. Returns the weight it was drawn with (0 = not drawn). */
    static double draw(Gpu.Mesh sphere, Profile p, SkyState s, Grade grade, Matrix4f camera) {
        double w = SkyColors.physicalWeight(p, s);
        if (w <= 0.003) {
            return 0;
        }
        PhysicalSky.Params params = PhysicalSky.Params.of(p);
        int width = switch (Settings.QUALITY.get(p)) {
            case "LOW" -> 96;
            case "ULTRA" -> 192;
            default -> 128;
        };
        int gk = Objects.hash(grade.apply(0x808080), grade.apply(0xFF4020), grade.apply(0x2040FF), grade.apply(0x202020));
        // quantised sun direction: about 0.15 degrees
        Key key = new Key(Math.round(s.sun[0] * 400), Math.round(s.sun[1] * 400), Math.round(s.sun[2] * 400),
                params, width, gk, (int) Math.round(s.rain * 20), (int) Math.round(s.thunder * 20));
        double[] sun = s.sun.clone();
        Baked baked = job.get(key, () -> {
            int h = width * 4;
            int[] px = PhysicalSky.texture(width, h, sun, params);
            for (int i = 0; i < px.length; i++) {
                px[i] = 0xFF000000 | grade.apply(SkyColors.weather(p, s, px[i] & 0xFFFFFF));
            }
            return new Baked(width, h, px, sun);
        });
        if (job.takeFresh() && baked != null) {
            if (tex != null && tex.width != baked.w()) {
                tex.close();
                tex = null;
            }
            if (tex == null) {
                tex = Gpu.texture("atmosphere", baked.argb(), baked.w(), baked.h());
            } else {
                tex.update(baked.argb());
            }
            shown = baked;
        }
        if (tex == null || shown == null) {
            return 0;
        }
        // after a time jump the old sky would be wrong: show the gradient until the new one is ready
        double drift = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, SkyMath.dot(shown.sun(), s.sun)))));
        if (drift > 3) {
            return 0;
        }
        Gpu.draw(sphere, camera, new Vector4f(1, 1, 1, (float) w), tex.view(), Gpu.sampler(true));
        return w;
    }

    /** Atmosphere color just above the horizon in a compass direction (degrees), or -1 if not ready. */
    static int horizonToward(double azimuthDeg) {
        Baked b = shown;
        if (b == null) {
            return -1;
        }
        double v = 0.5 - 4.0 / 180;
        int y = Math.max(0, Math.min(b.h() - 1, (int) (v * b.h())));
        long r = 0, g = 0, bl = 0;
        int n = 0;
        for (int k = -6; k <= 6; k++) {
            double u = SkyMath.frac((azimuthDeg + k * 6) / 360.0);
            int c = b.argb()[y * b.w() + Math.min(b.w() - 1, (int) (u * b.w()))];
            r += Colors.r(c);
            g += Colors.g(c);
            bl += Colors.b(c);
            n++;
        }
        return Colors.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
    }

    static void release() {
        if (tex != null) {
            tex.close();
            tex = null;
        }
        shown = null;
        job.clear();
    }
}
