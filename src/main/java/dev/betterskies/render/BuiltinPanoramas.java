package dev.betterskies.render;

import dev.betterskies.config.Grade;
import dev.betterskies.util.Colors;
import dev.betterskies.util.Parallel;

/**
 * Sky pictures painted by BetterSkies itself, made to be animated with the
 * Flow and Shimmer settings. Transparent where there is no cloud or light, so
 * they sit on top of any sky. Pure Java.
 */
final class BuiltinPanoramas {
    private BuiltinPanoramas() {
    }

    static ImageLibrary.Picture generate(String name, int maxWidth) {
        int w = Math.min(maxWidth, 2048), h = w / 2;
        return ImageLibrary.BUILTIN_AURORA.equals(name) ? aurora(w, h) : clouds(w, h);
    }

    /** Big soft cumulus clouds over the whole upper sky. */
    private static ImageLibrary.Picture clouds(int w, int h) {
        CloudGen.Result c = CloudGen.generate(new CloudGen.Input(1024, "MIXED", 0.5, 0.7, 0.75, 0.55, 0.7, 314,
                0xFFFFFF, 0xA3AEC8, 0, Grade.NONE));
        int n = c.size();
        int[] out = new int[w * h];
        double height = 6, tile = 60;
        Parallel.rows(h, y -> {
            double v = (y + 0.5) / h;
            for (int x = 0; x < w; x++) {
                double[] d = SkyMath.dirFromUv((x + 0.5) / w, v);
                if (d[1] <= 0.02) {
                    continue;
                }
                double t = height / d[1];
                double px = d[0] * t / tile, pz = d[2] * t / tile;
                // bilinear lookup in the tiling cloud texture
                double fx = px * n - 0.5, fz = pz * n - 0.5;
                int x0 = (int) Math.floor(fx), z0 = (int) Math.floor(fz);
                double ax = fx - x0, az = fz - z0;
                int c00 = c.argb()[Math.floorMod(z0, n) * n + Math.floorMod(x0, n)];
                int c10 = c.argb()[Math.floorMod(z0, n) * n + Math.floorMod(x0 + 1, n)];
                int c01 = c.argb()[Math.floorMod(z0 + 1, n) * n + Math.floorMod(x0, n)];
                int c11 = c.argb()[Math.floorMod(z0 + 1, n) * n + Math.floorMod(x0 + 1, n)];
                int top = Colors.mixArgb(c00, c10, ax), bottom = Colors.mixArgb(c01, c11, ax);
                int col = Colors.mixArgb(top, bottom, az);
                double el = Math.toDegrees(Math.asin(d[1]));
                double fade = SkyMath.smooth(1, 14, el);
                int a = (int) Math.round(Colors.a(col) * fade);
                out[y * w + x] = (a << 24) | (col & 0xFFFFFF);
            }
        });
        return new ImageLibrary.Picture(w, h, out);
    }

    /** Layered aurora curtains all around the northern half of the sky. */
    private static ImageLibrary.Picture aurora(int w, int h) {
        Noise noise = new Noise(77);
        int[] out = new int[w * h];
        int green = 0x4DFFB0, violet = 0x9A5CFF, pink = 0xFF6BD6;
        Parallel.rows(h, y -> {
            double v = (y + 0.5) / h;
            double el = (0.5 - v) * 180;
            if (el < -2) {
                return;
            }
            for (int x = 0; x < w; x++) {
                double u = (x + 0.5) / w;
                double ang = u * SkyMath.TAU;
                double cu = Math.cos(ang), su = Math.sin(ang);
                double north = Math.max(0, cu);
                double r = 0, g = 0, b = 0;
                for (int k = 0; k < 3; k++) {
                    double base = 10 + k * 7 + 7 * noise.noise(cu * 2.2 + k * 3, su * 2.2, k * 1.7)
                            + 4 * Math.sin(ang * (3 + k) + k);
                    double dEl = el - base;
                    double prof = dEl < 0 ? Math.exp(-dEl * dEl / 3.0) : Math.exp(-dEl / (14 + 6 * k));
                    if (prof < 0.01) {
                        continue;
                    }
                    double rays = 0.5 + 0.5 * noise.noise(cu * 40 + k * 10, su * 40, k * 5.1);
                    rays = 0.35 + 0.65 * Math.pow(rays, 1.4);
                    double env = Math.pow(0.15 + 0.85 * north, 1.5) * (0.6 + 0.4 * noise.fbm(cu * 3 + k, su * 3, k, 3, 0.5));
                    double k1 = prof * rays * env * (k == 0 ? 1 : 0.7);
                    double hk = SkyMath.smooth(0, 30, dEl);
                    int col = Colors.mixOklab(green, k == 2 ? pink : violet, hk);
                    r += Colors.r(col) / 255.0 * k1;
                    g += Colors.g(col) / 255.0 * k1;
                    b += Colors.b(col) / 255.0 * k1;
                }
                double m = Math.max(r, Math.max(g, b));
                if (m < 0.004) {
                    continue;
                }
                double a = Math.min(1, m);
                // stored as "light with alpha": bright = opaque, faint = transparent
                out[y * w + x] = Colors.argbf(a, Colors.rgbf(r / m, g / m, b / m));
            }
        });
        return new ImageLibrary.Picture(w, h, out);
    }
}
