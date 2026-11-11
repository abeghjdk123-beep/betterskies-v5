package dev.betterskies.render;

import dev.betterskies.config.Grade;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import dev.betterskies.util.Colors;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Paints the night sky (stars, Milky Way, nebulae, cosmic dust) into an
 * equirectangular texture on a background thread. The brightest stars are
 * returned separately so they can twinkle. Pure Java.
 */
public final class SpaceGen {
    private SpaceGen() {
    }

    public record Star(double[] dir, float size, int rgb, float brightness, float phase) {
    }

    public record Result(int width, int height, int[] argb, List<Star> bright, Input input) {
    }

    /** All settings that affect the space texture (a change means regenerate). */
    public record Input(int width, int seed, boolean stars, double density, double brightness, double size,
                        String colors, int c1, int c2, double twinkle,
                        boolean milky, double mwBright, double mwWidth, double mwTilt, int mwColor, int mwCore, double mwDust,
                        boolean nebula, int n1, int n2, double nInt, double nCov, double nScale, double nDetail,
                        boolean dust, double dustDensity, int dustColor, Grade grade, int gradeKey) {

        public static Input of(Profile p) {
            int w = switch (Settings.QUALITY.get(p)) {
                case "LOW" -> 1024;
                case "ULTRA" -> 4096;
                default -> 2048;
            };
            boolean g = Settings.GRADE_SPACE.on(p);
            Grade grade = g ? new Grade(p) : Grade.NONE;
            int gradeKey = g ? Objects.hash(Settings.EXPOSURE.v(p), Settings.CONTRAST.v(p), Settings.HIGHLIGHTS.v(p),
                    Settings.SHADOWS.v(p), Settings.GAMMA.v(p), Settings.SATURATION.v(p), Settings.VIBRANCE.v(p),
                    Settings.TEMPERATURE.v(p), Settings.TINT.v(p), Settings.HUE_SHIFT.v(p)) : 0;
            return new Input(w, (int) Settings.STAR_SEED.v(p), Settings.STARS.on(p),
                    Settings.STAR_DENSITY.v(p), Settings.STAR_BRIGHTNESS.v(p), Settings.STAR_SIZE.v(p),
                    Settings.STAR_COLORS.get(p), Settings.STAR_COLOR.rgb(p), Settings.STAR_COLOR2.rgb(p),
                    Settings.EFFECTS.on(p) ? Settings.TWINKLE.v(p) : 0,
                    Settings.MILKY_WAY.on(p), Settings.MW_BRIGHTNESS.v(p), Settings.MW_WIDTH.v(p), Settings.MW_TILT.v(p),
                    Settings.MW_COLOR.rgb(p), Settings.MW_CORE.rgb(p), Settings.MW_DUST.v(p),
                    Settings.NEBULA.on(p), Settings.NEB_COLOR.rgb(p), Settings.NEB_COLOR2.rgb(p), Settings.NEB_INTENSITY.v(p),
                    Settings.NEB_COVERAGE.v(p), Settings.NEB_SCALE.v(p), Settings.NEB_DETAIL.v(p),
                    Settings.DUST.on(p), Settings.DUST_DENSITY.v(p), Settings.DUST_COLOR.rgb(p), grade, gradeKey);
        }

        /** Equality ignoring the Grade object itself (compared through gradeKey). */
        public boolean same(Input o) {
            return o != null && width == o.width && seed == o.seed && stars == o.stars && density == o.density
                    && brightness == o.brightness && size == o.size && colors.equals(o.colors) && c1 == o.c1 && c2 == o.c2
                    && (twinkle > 0) == (o.twinkle > 0) && milky == o.milky && mwBright == o.mwBright && mwWidth == o.mwWidth
                    && mwTilt == o.mwTilt && mwColor == o.mwColor && mwCore == o.mwCore && mwDust == o.mwDust
                    && nebula == o.nebula && n1 == o.n1 && n2 == o.n2 && nInt == o.nInt && nCov == o.nCov
                    && nScale == o.nScale && nDetail == o.nDetail && dust == o.dust && dustDensity == o.dustDensity
                    && dustColor == o.dustColor && gradeKey == o.gradeKey;
        }
    }

    public static Result generate(Input in) {
        int w = in.width, h = w / 2;
        float[] r = new float[w * h], g = new float[w * h], b = new float[w * h];
        Noise noise = new Noise(in.seed * 7919L + 13);
        Noise noise2 = new Noise(in.seed * 104729L + 7);

        // ---- Milky Way geometry
        double tilt = Math.toRadians(in.mwTilt);
        double[] bandNormal = SkyMath.norm(new double[]{Math.sin(tilt), Math.cos(tilt) * 0.35, Math.cos(tilt)});
        double[] core = SkyMath.norm(SkyMath.cross(bandNormal, new double[]{0.3, 0.2, -1}));
        double bandWidth = 0.05 + 0.2 * in.mwWidth;

        // ---- diffuse layers (per pixel, parallel by row)
        boolean diffuse = in.milky || in.nebula || in.dust;
        if (diffuse) {
            dev.betterskies.util.Parallel.rows(h, y -> {
                double v = (y + 0.5) / h;
                for (int x = 0; x < w; x++) {
                    double u = (x + 0.5) / w;
                    double[] d = SkyMath.dirFromUv(u, v);
                    double rr = 0, gg = 0, bb = 0;
                    if (in.milky) {
                        double off = Math.asin(Math.max(-1, Math.min(1, SkyMath.dot(d, bandNormal))));
                        double band = Math.exp(-Math.pow(off / bandWidth, 2));
                        if (band > 0.004) {
                            double tex = noise.fbm(d[0] * 3.1, d[1] * 3.1, d[2] * 3.1, 5, 0.55);
                            double clumps = noise2.fbm(d[0] * 9, d[1] * 9, d[2] * 9, 3, 0.5);
                            double cDist = Math.acos(Math.max(-1, Math.min(1, SkyMath.dot(d, core))));
                            double coreGlow = Math.exp(-cDist * cDist / 0.28);
                            double lane = 1;
                            if (in.mwDust > 0) {
                                double rid = noise2.ridged(d[0] * 4.5, d[1] * 4.5, d[2] * 4.5, 4);
                                double centre = Math.exp(-Math.pow(off / (bandWidth * 0.35), 2));
                                lane = 1 - in.mwDust * centre * SkyMath.smooth(0.35, 0.8, rid) * 0.95;
                            }
                            double k = band * (0.35 + 0.9 * tex * tex) * (0.75 + 0.5 * clumps) * lane
                                    * (1 + 1.6 * coreGlow) * in.mwBright * 0.55;
                            int col = Colors.mix(in.mwColor, in.mwCore, Math.min(1, coreGlow * 1.3));
                            rr += Colors.r(col) / 255.0 * k;
                            gg += Colors.g(col) / 255.0 * k;
                            bb += Colors.b(col) / 255.0 * k;
                        }
                    }
                    if (in.nebula) {
                        double sc = 1.2 + (1 - in.nScale) * 3.5;
                        double wx = noise2.fbm(d[0] * sc + 5.2, d[1] * sc, d[2] * sc, 3, 0.5) - 0.5;
                        double wy = noise2.fbm(d[0] * sc, d[1] * sc + 1.7, d[2] * sc - 3.1, 3, 0.5) - 0.5;
                        double n = noise.fbm(d[0] * sc + wx * 2.2, d[1] * sc + wy * 2.2, d[2] * sc, 5, 0.5 + 0.12 * in.nDetail);
                        // big, slow regions decide where nebulae live; coverage widens them
                        double region = noise2.fbm(d[0] * 0.9 + 11, d[1] * 0.9, d[2] * 0.9 - 7, 3, 0.5);
                        double mask = SkyMath.smooth(0.72 - 0.42 * in.nCov, 0.9 - 0.3 * in.nCov, region);
                        double m = mask * SkyMath.smooth(0.38, 0.75, n);
                        if (m > 0.002) {
                            double fil = in.nDetail > 0 ? noise2.ridged(d[0] * sc * 2.5, d[1] * sc * 2.5, d[2] * sc * 2.5, 4) : 0.5;
                            double k = m * (0.45 + 1.0 * fil * in.nDetail) * in.nInt * 0.75;
                            double mixT = noise.fbm(d[0] * sc * 0.7 - 9, d[1] * sc * 0.7, d[2] * sc * 0.7 + 4, 3, 0.5);
                            int col = Colors.mixOklab(in.n1, in.n2, SkyMath.smooth(0.3, 0.7, mixT));
                            rr += Colors.r(col) / 255.0 * k;
                            gg += Colors.g(col) / 255.0 * k;
                            bb += Colors.b(col) / 255.0 * k;
                        }
                    }
                    if (in.dust) {
                        double n = noise.fbm(d[0] * 14, d[1] * 14, d[2] * 14, 3, 0.6);
                        double k = Math.pow(n, 3) * in.dustDensity * 0.35;
                        rr += Colors.r(in.dustColor) / 255.0 * k;
                        gg += Colors.g(in.dustColor) / 255.0 * k;
                        bb += Colors.b(in.dustColor) / 255.0 * k;
                    }
                    int i = y * w + x;
                    r[i] = (float) rr;
                    g[i] = (float) gg;
                    b[i] = (float) bb;
                }
            });
        }

        // ---- stars
        List<Star> bright = new ArrayList<>();
        if (in.stars && in.density > 0) {
            double pxPerRad = w / SkyMath.TAU;
            int count = (int) (Math.pow(in.density, 1.4) * 9000 * (w >= 2048 ? 1.4 : 1.0)) + 60;
            int extraBand = in.milky ? (int) (count * 0.8 * in.mwBright) : 0;
            int brightCap = in.twinkle > 0 ? (int) (40 + 260 * in.density) : 0;
            long seed = in.seed * 31L;
            int made = 0;
            for (int i = 0; made < count + extraBand && i < (count + extraBand) * 6; i++) {
                double[] d = randomDir(seed, i);
                boolean bandStar = made >= count;
                if (bandStar) {
                    double off = Math.asin(Math.max(-1, Math.min(1, SkyMath.dot(d, bandNormal))));
                    if (SkyMath.hash(seed + 5, i) > Math.exp(-Math.pow(off / bandWidth, 2))) {
                        continue;
                    }
                }
                made++;
                double mag = SkyMath.hash(seed + 1, i);
                double lum = bandStar ? 0.15 + 0.5 * Math.pow(mag, 3) : 0.12 + 1.6 * Math.pow(mag, 7);
                int col = starColor(in, SkyMath.hash(seed + 2, i));
                double sizePx = (0.55 + 1.1 * Math.pow(mag, 4)) * in.size * (w / 2048.0);
                lum *= in.brightness;
                if (!bandStar && brightCap > 0 && mag > 0.93 && bright.size() < brightCap) {
                    bright.add(new Star(d, (float) (sizePx / pxPerRad * 2.6), col, (float) Math.min(1.6, lum),
                            (float) (SkyMath.hash(seed + 3, i) * SkyMath.TAU)));
                    continue;
                }
                splat(r, g, b, w, h, d, sizePx, lum, col);
            }
        }

        // ---- pack
        int[] out = new int[w * h];
        Grade grade = in.grade;
        for (int i = 0; i < out.length; i++) {
            int c = Colors.rgbf(tone(r[i]), tone(g[i]), tone(b[i]));
            if (!grade.neutral() && c != 0) {
                c = grade.applyEmissive(c);
            }
            out[i] = 0xFF000000 | c;
        }
        if (!grade.neutral()) {
            List<Star> graded = new ArrayList<>(bright.size());
            for (Star s : bright) {
                graded.add(new Star(s.dir(), s.size(), grade.apply(s.rgb()), s.brightness(), s.phase()));
            }
            bright = graded;
        }
        return new Result(w, h, out, bright, in);
    }

    /** Soft roll-off so overlapping glow never clips harshly. */
    private static double tone(double v) {
        return v <= 0 ? 0 : 1 - Math.exp(-v * 1.25);
    }

    private static int starColor(Input in, double t) {
        return switch (in.colors) {
            case "TINTED" -> Colors.mix(in.c1, in.c2, t);
            case "WHITE" -> in.c1;
            default -> Colors.starTint(0.5 + (t - 0.5) * 0.9 * (t < 0.5 ? 1.1 : 0.9));
        };
    }

    private static double[] randomDir(long seed, int i) {
        double z = SkyMath.hash(seed, i * 2L) * 2 - 1;
        double a = SkyMath.hash(seed, i * 2L + 1) * SkyMath.TAU;
        double s = Math.sqrt(1 - z * z);
        return new double[]{s * Math.cos(a), z, s * Math.sin(a)};
    }

    /** Draws a small Gaussian star, widened horizontally to undo the equirect stretch. */
    private static void splat(float[] r, float[] g, float[] b, int w, int h, double[] d, double sizePx, double lum, int col) {
        double[] uv = SkyMath.uvFromDir(d);
        double cx = uv[0] * w, cy = uv[1] * h;
        double cosLat = Math.max(0.02, Math.sqrt(1 - d[1] * d[1]));
        double sx = sizePx / cosLat, sy = sizePx;
        int rx = (int) Math.ceil(sx * 2.2), ry = (int) Math.ceil(sy * 2.2);
        if (rx > w / 4) {
            rx = w / 4;
        }
        float cr = (float) (Colors.r(col) / 255.0 * lum), cg = (float) (Colors.g(col) / 255.0 * lum), cb = (float) (Colors.b(col) / 255.0 * lum);
        for (int yy = (int) cy - ry; yy <= (int) cy + ry; yy++) {
            if (yy < 0 || yy >= h) {
                continue;
            }
            double dy = (yy + 0.5 - cy) / sy;
            for (int xx = (int) cx - rx; xx <= (int) cx + rx; xx++) {
                double dx = (xx + 0.5 - cx) / sx;
                double k = Math.exp(-(dx * dx + dy * dy) * 1.6);
                if (k < 0.01) {
                    continue;
                }
                int px = Math.floorMod(xx, w);
                int i = yy * w + px;
                r[i] += (float) (cr * k);
                g[i] += (float) (cg * k);
                b[i] += (float) (cb * k);
            }
        }
    }
}
