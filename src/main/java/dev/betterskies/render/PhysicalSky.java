package dev.betterskies.render;

import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import dev.betterskies.util.Colors;
import dev.betterskies.util.Parallel;

/**
 * A physically based sky: single scattering through a spherical Earth atmosphere
 * with Rayleigh (air), Mie (haze) and ozone, including the planet's shadow. This
 * naturally produces blue daytime skies, white haze near the horizon, the glow
 * around the sun, deep red sunsets, the pink "Belt of Venus" and the dark band of
 * Earth's shadow rising opposite the sun at dusk. Pure Java.
 */
public final class PhysicalSky {
    private PhysicalSky() {
    }

    private static final double RE = 6360e3;
    private static final double RA = 6460e3;
    private static final double HR = 8000;
    private static final double HM = 1200;
    private static final double[] BETA_R = {5.802e-6, 13.558e-6, 33.1e-6};
    private static final double[] BETA_O = {0.650e-6, 1.881e-6, 0.085e-6};
    private static final double BETA_M = 3.996e-6;
    private static final double SUN = 22;
    private static final int VIEW_STEPS = 16;
    private static final int LIGHT_STEPS = 6;

    public record Params(double air, double haze, double ozone, double g, double exposure, double altitude,
                         int ground, int tint) {
        public static Params of(Profile p) {
            return new Params(Settings.PHYS_AIR.v(p), Settings.PHYS_HAZE.v(p), Settings.PHYS_OZONE.v(p),
                    0.62 + 0.3 * Settings.PHYS_SUN_GLOW.v(p), Settings.PHYS_EXPOSURE.v(p),
                    Settings.PHYS_ALTITUDE.v(p) * 12000, Settings.PHYS_GROUND.rgb(p), Settings.PHYS_TINT.rgb(p));
        }

        double mie() {
            return BETA_M * (0.3 + haze * 9);
        }
    }

    // ================================================================== core

    /** Distance along the ray to the far side of a sphere of radius r (origin at planet centre), or -1. */
    private static double exit(double ox, double oy, double oz, double dx, double dy, double dz, double r) {
        double b = ox * dx + oy * dy + oz * dz;
        double c = ox * ox + oy * oy + oz * oz - r * r;
        double disc = b * b - c;
        if (disc < 0) {
            return -1;
        }
        return -b + Math.sqrt(disc);
    }

    /** Distance to the near side of a sphere (ground hit), or -1 when it is missed or behind. */
    private static double hit(double ox, double oy, double oz, double dx, double dy, double dz, double r) {
        double b = ox * dx + oy * dy + oz * dz;
        double c = ox * ox + oy * oy + oz * oz - r * r;
        double disc = b * b - c;
        if (disc < 0) {
            return -1;
        }
        double t = -b - Math.sqrt(disc);
        return t > 0 ? t : -1;
    }

    private static double ozone(double h) {
        return Math.max(0, 1 - Math.abs(h - 25000) / 15000);
    }

    /** Linear (HDR) radiance arriving along unit direction view, for sun direction sun. */
    public static double[] radiance(double[] view, double[] sun, Params p) {
        double ox = 0, oy = RE + 2 + p.altitude, oz = 0;
        double dx = view[0], dy = view[1], dz = view[2];
        double tGround = hit(ox, oy, oz, dx, dy, dz, RE);
        double tMax = tGround > 0 ? tGround : exit(ox, oy, oz, dx, dy, dz, RA);
        if (tMax <= 0) {
            return new double[3];
        }
        double betaM = p.mie();
        // long grazing paths near the horizon need more samples
        int steps = VIEW_STEPS + (int) (20 * Math.pow(1 - Math.min(1, Math.abs(dy) * 4), 2));
        double ds = tMax / steps;
        double odR = 0, odM = 0, odO = 0;
        double sR0 = 0, sR1 = 0, sR2 = 0, sM0 = 0, sM1 = 0, sM2 = 0;
        for (int i = 0; i < steps; i++) {
            double t = (i + 0.5) * ds;
            double px = ox + dx * t, py = oy + dy * t, pz = oz + dz * t;
            double h = Math.sqrt(px * px + py * py + pz * pz) - RE;
            double dR = Math.exp(-h / HR) * p.air * ds, dM = Math.exp(-h / HM) * ds, dO = ozone(h) * p.ozone * ds;
            odR += dR;
            odM += dM;
            odO += dO;
            // light path to the sun (skip if the planet is in the way)
            if (hit(px, py, pz, sun[0], sun[1], sun[2], RE) > 0) {
                continue;
            }
            double tl = exit(px, py, pz, sun[0], sun[1], sun[2], RA);
            double dl = tl / LIGHT_STEPS;
            double lR = 0, lM = 0, lO = 0;
            for (int j = 0; j < LIGHT_STEPS; j++) {
                double u = (j + 0.5) * dl;
                double qx = px + sun[0] * u, qy = py + sun[1] * u, qz = pz + sun[2] * u;
                double hh = Math.sqrt(qx * qx + qy * qy + qz * qz) - RE;
                lR += Math.exp(-hh / HR) * p.air * dl;
                lM += Math.exp(-hh / HM) * dl;
                lO += ozone(hh) * p.ozone * dl;
            }
            double tR = odR + lR, tM = odM + lM, tO = odO + lO;
            double a0 = Math.exp(-(BETA_R[0] * tR + betaM * 1.11 * tM + BETA_O[0] * tO));
            double a1 = Math.exp(-(BETA_R[1] * tR + betaM * 1.11 * tM + BETA_O[1] * tO));
            double a2 = Math.exp(-(BETA_R[2] * tR + betaM * 1.11 * tM + BETA_O[2] * tO));
            sR0 += dR * a0;
            sR1 += dR * a1;
            sR2 += dR * a2;
            sM0 += dM * a0;
            sM1 += dM * a1;
            sM2 += dM * a2;
        }
        double mu = dx * sun[0] + dy * sun[1] + dz * sun[2];
        double phR = 3 / (16 * Math.PI) * (1 + mu * mu);
        double g = p.g;
        double phM = 3 / (8 * Math.PI) * ((1 - g * g) * (1 + mu * mu))
                / ((2 + g * g) * Math.pow(1 + g * g - 2 * g * mu, 1.5));
        double[] L = {
                SUN * (sR0 * BETA_R[0] * phR + sM0 * betaM * phM),
                SUN * (sR1 * BETA_R[1] * phR + sM1 * betaM * phM),
                SUN * (sR2 * BETA_R[2] * phR + sM2 * betaM * phM)};
        // a touch of multiple scattering keeps twilight from going black too quickly
        double ms = 0.06;
        L[0] += ms * SUN * sR0 * BETA_R[0] * 0.08;
        L[1] += ms * SUN * sR1 * BETA_R[1] * 0.08;
        L[2] += ms * SUN * sR2 * BETA_R[2] * 0.08;
        if (tGround > 0) {
            // light bounced off the ground below the horizon
            double gx = ox + dx * tGround, gy = oy + dy * tGround, gz = oz + dz * tGround;
            double len = Math.sqrt(gx * gx + gy * gy + gz * gz);
            double cos = Math.max(0, (gx * sun[0] + gy * sun[1] + gz * sun[2]) / len);
            double[] ts = transmittance(gx, gy, gz, sun, p);
            double[] tv = {Math.exp(-(BETA_R[0] * odR + betaM * 1.11 * odM)), Math.exp(-(BETA_R[1] * odR + betaM * 1.11 * odM)),
                    Math.exp(-(BETA_R[2] * odR + betaM * 1.11 * odM))};
            double k = SUN * cos / Math.PI * 0.12;
            L[0] += tv[0] * ts[0] * k * Colors.r(p.ground) / 255.0;
            L[1] += tv[1] * ts[1] * k * Colors.g(p.ground) / 255.0;
            L[2] += tv[2] * ts[2] * k * Colors.b(p.ground) / 255.0;
        }
        return L;
    }

    private static double[] transmittance(double px, double py, double pz, double[] dir, Params p) {
        if (hit(px, py, pz, dir[0], dir[1], dir[2], RE - 1) > 0) {
            return new double[3];
        }
        double tl = exit(px, py, pz, dir[0], dir[1], dir[2], RA);
        double dl = tl / 12;
        double lR = 0, lM = 0, lO = 0;
        for (int j = 0; j < 12; j++) {
            double u = (j + 0.5) * dl;
            double qx = px + dir[0] * u, qy = py + dir[1] * u, qz = pz + dir[2] * u;
            double hh = Math.sqrt(qx * qx + qy * qy + qz * qz) - RE;
            lR += Math.exp(-hh / HR) * p.air * dl;
            lM += Math.exp(-hh / HM) * dl;
            lO += ozone(hh) * p.ozone * dl;
        }
        double betaM = p.mie() * 1.11;
        return new double[]{Math.exp(-(BETA_R[0] * lR + betaM * lM + BETA_O[0] * lO)),
                Math.exp(-(BETA_R[1] * lR + betaM * lM + BETA_O[1] * lO)),
                Math.exp(-(BETA_R[2] * lR + betaM * lM + BETA_O[2] * lO))};
    }

    /** Color of direct sunlight reaching the viewer (white at noon, orange-red at sunset). */
    public static int sunlight(double[] sun, Params p) {
        double[] t = transmittance(0, RE + 2 + p.altitude, 0, sun, p);
        double m = Math.max(1e-4, Math.max(t[0], Math.max(t[1], t[2])));
        // keep the hue, but normalise brightness so colors stay usable as a tint
        double k = Math.min(1, m * 4) / m;
        return Colors.rgbf(t[0] * k, t[1] * k, t[2] * k);
    }

    /** Filmic-ish tone mapping to an sRGB color; eyes adapt (brighten) as the sun goes down. */
    public static int toRgb(double[] L, Params p, double sunY) {
        double adapt = 1 + 7 * SkyMath.smooth(0.12, -0.16, sunY);
        double e = 0.9 * Math.pow(2, p.exposure) * adapt;
        double r = 1 - Math.exp(-L[0] * e), g = 1 - Math.exp(-L[1] * e), b = 1 - Math.exp(-L[2] * e);
        r = Math.pow(r, 1 / 2.2);
        g = Math.pow(g, 1 / 2.2);
        b = Math.pow(b, 1 / 2.2);
        double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double sat = 1.4;
        int c = Colors.rgbf(lum + (r - lum) * sat, lum + (g - lum) * sat, lum + (b - lum) * sat);
        if ((p.tint & 0xFFFFFF) != 0xFFFFFF) {
            c = Colors.rgb(Colors.r(c) * Colors.r(p.tint) / 255, Colors.g(c) * Colors.g(p.tint) / 255, Colors.b(c) * Colors.b(p.tint) / 255);
        }
        return c;
    }

    public static int color(double[] view, double[] sun, Params p) {
        if (view[1] < 0.007) {
            double el = Math.toDegrees(Math.asin(Math.max(-1, view[1])));
            double[] flat = SkyMath.norm(new double[]{view[0], 0, view[2]});
            return below(horizonColor(flat, sun, p), el, p);
        }
        return toRgb(radiance(view, sun, p), p, sun[1]);
    }

    private static int horizonColor(double[] flat, double[] sun, Params p) {
        double c = Math.cos(Math.toRadians(0.4)), sn = Math.sin(Math.toRadians(0.4));
        return toRgb(radiance(new double[]{flat[0] * c, sn, flat[2] * c}, sun, p), p, sun[1]);
    }

    /**
     * Below the horizon: the horizon glow carries on and softens into hazy, darker
     * land over a few tens of degrees, instead of stopping in a hard line.
     */
    private static int below(int horizon, double el, Params p) {
        double depth = 0.4 - el;
        double lum = Colors.luminance(horizon);
        int haze = Colors.mixOklab(horizon, Colors.scale(Colors.mix(horizon, 0x8A8F99, 0.25), 0.7), SkyMath.smooth(0, 10, depth));
        int ground = Colors.scale(Colors.mix(p.ground, horizon, 0.35), 0.2 + 0.55 * lum);
        return Colors.mixOklab(haze, ground, SkyMath.smooth(4, 40, depth));
    }

    /**
     * Equirectangular sky (u = azimuth from north, v = 0 at zenith), opaque ARGB.
     * Use a tall texture (h around 4 x w): colors change fastest with height,
     * especially right at the horizon.
     */
    public static int[] texture(int w, int h, double[] sun, Params p) {
        int[] out = new int[w * h];
        int[] horizon = new int[w];
        for (int x = 0; x < w; x++) {
            double[] d = SkyMath.dirFromUv((x + 0.5) / w, 0.5);
            horizon[x] = horizonColor(SkyMath.norm(new double[]{d[0], 0, d[2]}), sun, p);
        }
        Parallel.rows(h, y -> {
            double v = (y + 0.5) / h;
            double el = (0.5 - v) * 180;
            for (int x = 0; x < w; x++) {
                int c;
                if (el < 0.4) {
                    c = below(horizon[x], el, p);
                } else {
                    c = toRgb(radiance(SkyMath.dirFromUv((x + 0.5) / w, v), sun, p), p, sun[1]);
                }
                out[y * w + x] = 0xFF000000 | c;
            }
        });
        return out;
    }
}
