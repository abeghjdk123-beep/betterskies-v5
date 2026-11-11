package dev.betterskies.render;

/**
 * Directions, time of day and small vector helpers. Pure Java.
 *
 * <p>Coordinates match Minecraft: +X east, +Y up, -Z north. Sky "uv" coordinates
 * are equirectangular: u is the compass azimuth from north (0) through east (0.25),
 * v is 0 at the zenith and 1 at the nadir.</p>
 */
public final class SkyMath {
    private SkyMath() {
    }

    public static final double TAU = Math.PI * 2;

    // ================================================================== time

    /** Vanilla's celestial angle (0 = noon, 0.25 ≈ sunset, 0.5 = midnight, 0.75 ≈ sunrise). */
    public static double celestialAngle(long dayTime) {
        double d = frac(dayTime / 24000.0 - 0.25);
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        return (d * 2.0 + e) / 3.0;
    }

    public static int moonPhase(long dayTime) {
        return (int) Math.floorMod(dayTime / 24000L, 8L);
    }

    /**
     * Direction of the sun for a celestial angle and orbit tilt (degrees). The moon is
     * the opposite direction.
     */
    public static double[] sunDir(double angle, double tiltDeg) {
        double t = angle * TAU;
        double x = -Math.sin(t), y = Math.cos(t), z = 0;
        double a = Math.toRadians(tiltDeg);
        // rotate around the X axis (east-west line) to tilt the orbit toward the south
        double y2 = y * Math.cos(a) - z * Math.sin(a);
        double z2 = y * Math.sin(a) + z * Math.cos(a);
        return new double[]{x, y2, z2};
    }

    // ================================================================== directions

    public static double[] dirFromUv(double u, double v) {
        double az = u * TAU;
        double el = (0.5 - v) * Math.PI;
        double c = Math.cos(el);
        return new double[]{c * Math.sin(az), Math.sin(el), -c * Math.cos(az)};
    }

    /** {u, v} for a unit direction. */
    public static double[] uvFromDir(double[] d) {
        double az = Math.atan2(d[0], -d[2]);
        if (az < 0) {
            az += TAU;
        }
        double el = Math.asin(Math.max(-1, Math.min(1, d[1])));
        return new double[]{az / TAU, 0.5 - el / Math.PI};
    }

    /** Direction from compass azimuth (degrees from north, clockwise) and elevation (degrees). */
    public static double[] dirFromAngles(double azimuthDeg, double elevationDeg) {
        double az = Math.toRadians(azimuthDeg), el = Math.toRadians(elevationDeg);
        double c = Math.cos(el);
        return new double[]{c * Math.sin(az), Math.sin(el), -c * Math.cos(az)};
    }

    // ================================================================== vectors

    public static double[] scale(double[] a, double k) {
        return new double[]{a[0] * k, a[1] * k, a[2] * k};
    }

    public static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    public static double[] add(double[] c, double[] a, double ka, double[] b, double kb) {
        return new double[]{c[0] + a[0] * ka + b[0] * kb, c[1] + a[1] * ka + b[1] * kb, c[2] + a[2] * ka + b[2] * kb};
    }

    public static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    public static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    public static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    public static double[] norm(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return l < 1e-12 ? new double[]{0, 1, 0} : scale(a, 1 / l);
    }

    /** Any unit vector perpendicular to d. */
    public static double[] tangent(double[] d) {
        double[] ref = Math.abs(d[1]) < 0.95 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        return norm(cross(ref, d));
    }

    /** Spherical interpolation between unit vectors. */
    public static double[] slerp(double[] a, double[] b, double t) {
        double d = Math.max(-1, Math.min(1, dot(a, b)));
        double ang = Math.acos(d);
        if (ang < 1e-6) {
            return a.clone();
        }
        double s = Math.sin(ang);
        double ka = Math.sin((1 - t) * ang) / s, kb = Math.sin(t * ang) / s;
        return new double[]{a[0] * ka + b[0] * kb, a[1] * ka + b[1] * kb, a[2] * ka + b[2] * kb};
    }

    /** Rotates v around unit axis k by angle (radians). */
    public static double[] rotate(double[] v, double[] k, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        double[] kxv = cross(k, v);
        double kd = dot(k, v) * (1 - c);
        return new double[]{v[0] * c + kxv[0] * s + k[0] * kd, v[1] * c + kxv[1] * s + k[1] * kd, v[2] * c + kxv[2] * s + k[2] * kd};
    }

    // ================================================================== scalar

    public static double frac(double x) {
        return x - Math.floor(x);
    }

    public static double clamp01(double x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }

    public static double smooth(double e0, double e1, double x) {
        double t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3 - 2 * t);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Stable pseudo-random value in [0,1) for an integer seed. */
    public static double hash(long n) {
        n = (n ^ (n >>> 33)) * 0xff51afd7ed558ccdL;
        n = (n ^ (n >>> 33)) * 0xc4ceb9fe1a85ec53L;
        n ^= n >>> 33;
        return (n >>> 11) * 0x1.0p-53;
    }

    public static double hash(long a, long b) {
        return hash(a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + 0x165667B19E3779F9L);
    }
}
