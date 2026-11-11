package dev.betterskies.render;

import dev.betterskies.util.Colors;

/**
 * Procedural textures for the sun, moon and small sprites, as ARGB arrays.
 * Additive textures are "premultiplied" (brightness in rgb, alpha 255). Pure Java.
 */
public final class Bodies {
    private Bodies() {
    }

    public static final int SIZE = 256;
    /** Sun disc radius as a fraction of the sun quad's half-size. */
    public static final double SUN_DISC = 0.22;
    /** Moon disc radius as a fraction of the moon quad's half-size. */
    public static final double MOON_DISC = 0.92;

    /** Additive sun texture: disc, limb darkening, glow and optional corona rays. */
    public static int[] sun(String style, int color, double glow, int glowColor, double corona) {
        int n = SIZE;
        int[] out = new int[n * n];
        Noise rays = new Noise(99);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                double r = Math.sqrt(dx * dx + dy * dy);
                double disc;
                double ang = Math.atan2(dy, dx);
                if ("PIXEL".equals(style)) {
                    double px = Math.floor((dx / SUN_DISC + 1) * 4) / 4 - 1, py = Math.floor((dy / SUN_DISC + 1) * 4) / 4 - 1;
                    boolean in = Math.abs(dx) <= SUN_DISC && Math.abs(dy) <= SUN_DISC;
                    disc = in ? (Math.abs(px) > 0.7 || Math.abs(py) > 0.7 ? 0.82 : 1.0) : 0;
                } else {
                    double edge = "ORB".equals(style) ? 0.05 : 0.012;
                    double cover = SkyMath.smooth(SUN_DISC + edge, SUN_DISC - edge, r);
                    double mu = Math.sqrt(Math.max(0, 1 - Math.pow(Math.min(1, r / SUN_DISC), 2)));
                    double limb = 1 - 0.35 * (1 - mu);
                    disc = cover * limb;
                }
                double g = Math.exp(-Math.max(0, r - SUN_DISC * 0.85) * ("ORB".equals(style) ? 4.5 : 7.5));
                g *= glow * ("ORB".equals(style) ? 1.4 : 1.0);
                double c = 0;
                if (corona > 0 && r > SUN_DISC * 0.9) {
                    double k = rays.fbm(Math.cos(ang) * 6, Math.sin(ang) * 6, 1.3, 3, 0.6);
                    c = corona * Math.pow(k, 3) * 2.2 * Math.exp(-(r - SUN_DISC) * 4) * SkyMath.smooth(1.0, 0.6, r);
                }
                double fade = SkyMath.smooth(1.0, 0.8, r);
                g *= fade;
                int body = Colors.scale(color, Math.min(1, disc));
                int gl = Colors.scale(glowColor, Math.min(1, g + c));
                out[y * n + x] = 0xFF000000 | addClamp(body, gl);
            }
        }
        return out;
    }

    /**
     * Alpha-blended moon disc lit for a phase (0 = full ... 4 = new ... 7).
     * The unlit side keeps a little alpha (earthshine) so it hides stars behind it.
     */
    public static int[] moon(String style, int phase, int color, double earthshine) {
        int n = SIZE;
        int[] out = new int[n * n];
        double a = phase * Math.PI / 4;
        double lx = Math.sin(a), lz = Math.cos(a);
        Noise maria = new Noise(4242);
        long craterSeed = 777;
        int craters = 18;
        double[][] cr = new double[craters][];
        for (int i = 0; i < craters; i++) {
            double cx = SkyMath.hash(craterSeed, i * 3L) * 1.6 - 0.8, cy = SkyMath.hash(craterSeed, i * 3L + 1) * 1.6 - 0.8;
            double rad = 0.03 + 0.11 * Math.pow(SkyMath.hash(craterSeed, i * 3L + 2), 2.2);
            cr[i] = new double[]{cx, cy, rad};
        }
        boolean pixel = "PIXEL".equals(style);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                if (pixel) {
                    dx = (Math.floor((dx + 1) * 8) + 0.5) / 8 - 1;
                    dy = (Math.floor((dy + 1) * 8) + 0.5) / 8 - 1;
                }
                double mx = dx / MOON_DISC, my = dy / MOON_DISC;
                double rr = mx * mx + my * my;
                if (rr > 1.04) {
                    out[y * n + x] = 0;
                    continue;
                }
                double mz = Math.sqrt(Math.max(0, 1 - rr));
                double albedo = 1;
                if ("REALISTIC".equals(style)) {
                    double m = maria.fbm(mx * 1.6 + 3, my * 1.6, mz * 1.6, 4, 0.55);
                    albedo = 0.92 - 0.38 * SkyMath.smooth(0.48, 0.7, m);
                    albedo *= 0.94 + 0.12 * maria.fbm(mx * 9, my * 9, mz * 9, 3, 0.5);
                    for (double[] c : cr) {
                        double d = Math.sqrt((mx - c[0]) * (mx - c[0]) + (my - c[1]) * (my - c[1])) / c[2];
                        if (d < 1.25) {
                            double rim = Math.exp(-Math.pow((d - 1) / 0.2, 2)) * 0.07;
                            double bowl = d < 1 ? -0.07 * (1 - d * d) : 0;
                            albedo += rim + bowl;
                        }
                    }
                } else if (pixel) {
                    double m = maria.fbm(mx * 1.6 + 3, my * 1.6, mz * 1.6, 3, 0.55);
                    albedo = m > 0.58 ? 0.72 : 0.95;
                }
                double lit = mx * lx + mz * lz;
                double term = pixel ? (lit > 0 ? 1 : 0) : SkyMath.smooth(-0.04, 0.12, lit);
                double shade = pixel ? 1 : 0.55 + 0.45 * Math.max(0, lit);
                double edge = pixel ? 1 : SkyMath.smooth(1.02, 0.97, Math.sqrt(rr));
                double bright = Math.max(0, albedo) * (term * shade + (1 - term) * earthshine * 0.18);
                double alpha = edge * (term + (1 - term) * (0.25 + 0.6 * earthshine));
                int c = Colors.scale(color, Math.min(1.2, bright));
                out[y * n + x] = Colors.argbf(alpha, c);
            }
        }
        return out;
    }

    /** Soft radial glow, white, for additive use (rgb = intensity). */
    public static int[] glow(int n, double falloff) {
        int[] out = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                double r = Math.sqrt(dx * dx + dy * dy);
                double k = Math.exp(-r * r * falloff) * SkyMath.smooth(1, 0.7, r);
                int v = Colors.clamp255(k * 255);
                out[y * n + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
            }
        }
        return out;
    }

    /** Soft round dot with alpha falloff (alpha-blended sprites: stars, meteor heads). */
    public static int[] dot(int n) {
        int[] out = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                double r2 = dx * dx + dy * dy;
                double k = Math.exp(-r2 * 9) + 0.25 * Math.exp(-r2 * 2.2);
                k *= SkyMath.smooth(1, 0.8, Math.sqrt(r2));
                out[y * n + x] = Colors.argbf(Math.min(1, k), 0xFFFFFF);
            }
        }
        return out;
    }

    /** A thin luminous ring (moon halo), additive. */
    public static int[] ring(int n) {
        int[] out = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1;
                double r = Math.sqrt(dx * dx + dy * dy);
                double k = Math.exp(-Math.pow((r - 0.78) / 0.05, 2)) * 0.9 + Math.exp(-Math.pow((r - 0.7) / 0.18, 2)) * 0.15;
                int inner = Colors.mix(0xFFD8B0, 0xB8D4FF, SkyMath.smooth(0.72, 0.84, r));
                out[y * n + x] = 0xFF000000 | Colors.scale(inner, Math.min(1, k));
            }
        }
        return out;
    }

    private static int addClamp(int a, int b) {
        return Colors.rgb(Colors.r(a) + Colors.r(b), Colors.g(a) + Colors.g(b), Colors.b(a) + Colors.b(b));
    }

    /** Applies a color function to every pixel, keeping alpha. */
    public static int[] map(int[] argb, java.util.function.IntUnaryOperator f) {
        int[] out = new int[argb.length];
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            out[i] = (c & 0xFF000000) | (f.applyAsInt(c & 0xFFFFFF) & 0xFFFFFF);
        }
        return out;
    }
}
