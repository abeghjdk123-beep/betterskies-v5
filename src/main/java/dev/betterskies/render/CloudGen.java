package dev.betterskies.render;

import dev.betterskies.config.Grade;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import dev.betterskies.util.Colors;

import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Paints a seamlessly tiling cloud texture on the background worker: alpha is
 * how much cloud there is, rgb blends the sunlit and shadow colors by thickness.
 * Lighting for the time of day is applied per frame by the renderer. Pure Java.
 */
public final class CloudGen {
    private CloudGen() {
    }

    public record Input(int size, String type, double coverage, double density, double cloudSize, double softness,
                        double detail, int seed, int lit, int shadow, int gradeKey, Grade grade) {

        public static Input of(Profile p) {
            int size = switch (Settings.QUALITY.get(p)) {
                case "LOW" -> 512;
                case "BALANCED" -> 1024;
                default -> 2048;
            };
            Grade g = new Grade(p);
            int gk = Objects.hash(g.apply(0x808080), g.apply(0xFF4020), g.apply(0x2040FF), g.apply(0x202020), g.apply(0xE0E0E0));
            return new Input(size, Settings.SC_TYPE.get(p), Settings.SC_COVERAGE.v(p), Settings.SC_DENSITY.v(p),
                    Settings.SC_SIZE.v(p), Settings.SC_SOFTNESS.v(p), Settings.SC_DETAIL.v(p), (int) Settings.SC_SEED.v(p),
                    Settings.SC_LIT.rgb(p), Settings.SC_SHADOW.rgb(p), gk, g);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Input i && size == i.size && type.equals(i.type) && coverage == i.coverage
                    && density == i.density && cloudSize == i.cloudSize && softness == i.softness && detail == i.detail
                    && seed == i.seed && lit == i.lit && shadow == i.shadow && gradeKey == i.gradeKey;
        }

        @Override
        public int hashCode() {
            return Objects.hash(size, type, coverage, density, cloudSize, softness, detail, seed, lit, shadow, gradeKey);
        }
    }

    public record Result(int size, int[] argb) {
    }

    // ================================================================== tiling noise

    /** Perlin noise that repeats every (px, py) units. */
    static final class TileNoise {
        private final int[] perm = new int[512];

        TileNoise(long seed) {
            int[] p = new int[256];
            for (int i = 0; i < 256; i++) {
                p[i] = i;
            }
            for (int i = 255; i > 0; i--) {
                int j = (int) (SkyMath.hash(seed, i) * (i + 1));
                int t = p[i];
                p[i] = p[j];
                p[j] = t;
            }
            for (int i = 0; i < 512; i++) {
                perm[i] = p[i & 255];
            }
        }

        private double grad(int ix, int iy, double dx, double dy) {
            int h = perm[(perm[ix & 255] + iy) & 511] & 7;
            double ang = h * (Math.PI / 4) + 0.3926990817;
            return Math.cos(ang) * dx + Math.sin(ang) * dy;
        }

        double noise(double x, double y, int px, int py) {
            int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
            double fx = x - x0, fy = y - y0;
            int ax = Math.floorMod(x0, px), bx = Math.floorMod(x0 + 1, px);
            int ay = Math.floorMod(y0, py), by = Math.floorMod(y0 + 1, py);
            double u = fx * fx * fx * (fx * (fx * 6 - 15) + 10), v = fy * fy * fy * (fy * (fy * 6 - 15) + 10);
            double n00 = grad(ax, ay, fx, fy), n10 = grad(bx, ay, fx - 1, fy);
            double n01 = grad(ax, by, fx, fy - 1), n11 = grad(bx, by, fx - 1, fy - 1);
            return SkyMath.lerp(SkyMath.lerp(n00, n10, u), SkyMath.lerp(n01, n11, u), v) * 1.41;
        }

        /** Tiling cellular noise: 1 near feature points, falling to 0 between them (round puffs). */
        double worley(double x, double y, int p) {
            int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
            double best = 9;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int gx = cx + dx, gy = cy + dy;
                    int wx = Math.floorMod(gx, p), wy = Math.floorMod(gy, p);
                    int h = perm[(perm[wx & 255] + (wy & 255)) & 511];
                    double fx = gx + SkyMath.hash(h, wx * 31L + wy), fy = gy + SkyMath.hash(h + 7, wy * 17L + wx);
                    double d = (fx - x) * (fx - x) + (fy - y) * (fy - y);
                    if (d < best) {
                        best = d;
                    }
                }
            }
            return Math.max(0, 1 - Math.sqrt(best));
        }

        double worleyFbm(double x, double y, int p, int octaves) {
            double sum = 0, amp = 0.6, norm = 0;
            int f = 1;
            for (int o = 0; o < octaves; o++) {
                sum += amp * worley(x * f, y * f, p * f);
                norm += amp;
                amp *= 0.45;
                f *= 2;
            }
            return sum / norm;
        }

        /** Fractal noise in ~[0,1] over a tile of (px, py) base cells. */
        double fbm(double x, double y, int px, int py, int octaves, double gain, boolean billow) {
            double sum = 0, amp = 0.5, norm = 0;
            int f = 1;
            for (int o = 0; o < octaves; o++) {
                double n = noise(x * f, y * f, px * f, py * f);
                sum += amp * (billow ? 1 - Math.abs(n) : 0.5 + 0.5 * n);
                norm += amp;
                amp *= gain;
                f *= 2;
            }
            return sum / norm;
        }
    }

    // ================================================================== generation

    public static Result generate(Input in) {
        int n = in.size;
        int[] out = new int[n * n];
        TileNoise a = new TileNoise(in.seed * 92821L + 3);
        TileNoise b = new TileNoise(in.seed * 31337L + 11);
        TileNoise c = new TileNoise(in.seed * 7777L + 29);
        // base cells per tile: bigger clouds = fewer cells
        int cells = 2 * Math.max(1, (int) Math.round(4.6 - in.cloudSize * 3.6)); // even, so half-scale layers tile too
        int octaves = 4 + (int) Math.round(in.detail * 3);
        double gain = 0.42 + 0.16 * in.detail;
        double cover = in.coverage;
        double soft = 0.04 + in.softness * 0.3;
        int lit = in.grade.apply(in.lit), shadow = in.grade.apply(in.shadow);
        String type = in.type;
        IntStream.range(0, n).parallel().forEach(y -> {
            for (int x = 0; x < n; x++) {
                double u = (x + 0.5) / n, v = (y + 0.5) / n;
                double field;
                double threshold;
                switch (type) {
                    case "CIRRUS" -> {
                        field = cirrus(a, b, u, v, cells, octaves);
                        threshold = 0.78 - 0.5 * cover;
                    }
                    case "STRATUS" -> {
                        double base = a.fbm(u * cells * 0.5, v * cells * 0.5, Math.max(1, cells / 2), Math.max(1, cells / 2), octaves, gain, false);
                        double tex = b.fbm(u * cells * 2, v * cells * 2, cells * 2, cells * 2, 3, 0.5, false);
                        field = base * 0.8 + tex * 0.2;
                        threshold = 0.62 - 0.55 * cover;
                    }
                    case "SCATTERED" -> {
                        field = cumulus(a, b, u, v, cells * 2, octaves, gain);
                        threshold = 0.66 - 0.45 * cover;
                    }
                    case "MIXED" -> {
                        double cu = cumulus(a, b, u, v, cells, octaves, gain);
                        double ci = cirrus(c, b, u, v, cells, octaves) * 0.92;
                        field = Math.max(cu, ci);
                        threshold = 0.68 - 0.47 * cover;
                    }
                    default -> {
                        field = cumulus(a, b, u, v, cells, octaves, gain);
                        threshold = 0.68 - 0.5 * cover;
                    }
                }
                double d = SkyMath.smooth(threshold - soft * 0.25, threshold + soft, field);
                if (d <= 0.002) {
                    out[y * n + x] = 0;
                    continue;
                }
                double thick = SkyMath.smooth(threshold + 0.08, threshold + 0.5, field);
                double alpha = Math.pow(d, 0.85) * (0.3 + 0.7 * in.density);
                if ("CIRRUS".equals(type)) {
                    alpha *= 0.75;
                }
                int rgb = Colors.mixOklab(lit, shadow, thick * thick * (0.2 + 0.4 * in.density));
                out[y * n + x] = Colors.argbf(Math.min(1, alpha), rgb);
            }
        });
        return new Result(n, out);
    }

    private static double cumulus(TileNoise a, TileNoise b, double u, double v, int cells, int octaves, double gain) {
        // Perlin-Worley: soft Perlin decides where clouds gather, cellular noise gives round puffy tops
        double wx = b.fbm(u * cells, v * cells, cells, cells, 3, 0.5, false) - 0.5;
        double wy = b.fbm(u * cells + 17.3, v * cells + 9.1, cells, cells, 3, 0.5, false) - 0.5;
        double x = u * cells + wx * 0.35, y = v * cells + wy * 0.35;
        double shape = a.fbm(x, y, cells, cells, Math.min(octaves, 4), 0.5, false);
        double puffs = a.worleyFbm(x * 2, y * 2, cells * 2, 3);
        double fine = b.worleyFbm(x * 8, y * 8, cells * 8, 2);
        double detail = (octaves - 4) / 3.0;
        return shape * 0.75 + puffs * 0.55 - (1 - fine) * 0.12 * (0.4 + detail) - 0.18;
    }

    private static double cirrus(TileNoise a, TileNoise b, double u, double v, int cells, int octaves) {
        // stretched along x: long streaks that the renderer aligns with the wind
        int px = Math.max(1, cells / 2), py = cells * 3, q = Math.max(1, py / 4);
        double wy = b.fbm(u * px, v * q, px, q, 3, 0.5, false) - 0.5;
        double streak = a.fbm(u * px, v * py + wy * 3, px, py, octaves, 0.5, false);
        double patches = b.fbm(u * cells * 0.5 + 5, v * cells * 0.5, Math.max(1, cells / 2), Math.max(1, cells / 2), 3, 0.5, false);
        return streak * 0.7 + patches * 0.45 - 0.08;
    }
}
