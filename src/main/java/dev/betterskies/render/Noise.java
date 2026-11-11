package dev.betterskies.render;

/** Seeded 3D gradient noise and fractal helpers. Sampling on a sphere avoids seams and pole pinching. */
public final class Noise {
    private final int[] perm = new int[512];
    private static final double[][] G = {
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0}, {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1}, {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}};

    public Noise(long seed) {
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

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private double grad(int h, double x, double y, double z) {
        double[] g = G[h & 15];
        return g[0] * x + g[1] * y + g[2] * z;
    }

    /** Perlin noise in roughly [-1, 1]. */
    public double noise(double x, double y, double z) {
        int X = (int) Math.floor(x) & 255, Y = (int) Math.floor(y) & 255, Z = (int) Math.floor(z) & 255;
        x -= Math.floor(x);
        y -= Math.floor(y);
        z -= Math.floor(z);
        double u = fade(x), v = fade(y), w = fade(z);
        int A = perm[X] + Y, AA = perm[A] + Z, AB = perm[A + 1] + Z;
        int B = perm[X + 1] + Y, BA = perm[B] + Z, BB = perm[B + 1] + Z;
        double r = SkyMath.lerp(
                SkyMath.lerp(SkyMath.lerp(grad(perm[AA], x, y, z), grad(perm[BA], x - 1, y, z), u),
                        SkyMath.lerp(grad(perm[AB], x, y - 1, z), grad(perm[BB], x - 1, y - 1, z), u), v),
                SkyMath.lerp(SkyMath.lerp(grad(perm[AA + 1], x, y, z - 1), grad(perm[BA + 1], x - 1, y, z - 1), u),
                        SkyMath.lerp(grad(perm[AB + 1], x, y - 1, z - 1), grad(perm[BB + 1], x - 1, y - 1, z - 1), u), v),
                w);
        return r;
    }

    /** Fractal noise mapped to roughly [0, 1]. */
    public double fbm(double x, double y, double z, int octaves, double gain) {
        double sum = 0, amp = 0.5, norm = 0, f = 1;
        for (int i = 0; i < octaves; i++) {
            sum += amp * noise(x * f, y * f, z * f);
            norm += amp;
            amp *= gain;
            f *= 2.02;
        }
        return 0.5 + 0.5 * sum / norm * 1.4;
    }

    /** Ridged fractal noise (wispy filaments) in [0, 1]. */
    public double ridged(double x, double y, double z, int octaves) {
        double sum = 0, amp = 0.5, norm = 0, f = 1;
        for (int i = 0; i < octaves; i++) {
            double n = 1 - Math.abs(noise(x * f, y * f, z * f));
            sum += amp * n * n;
            norm += amp;
            amp *= 0.5;
            f *= 2.03;
        }
        return sum / norm;
    }
}
