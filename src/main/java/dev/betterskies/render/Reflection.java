package dev.betterskies.render;

import dev.betterskies.config.Grade;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import dev.betterskies.util.Colors;

import java.util.function.UnaryOperator;

/**
 * Mirror water: the lower half of the sky shows the upper half reflected, with
 * gentle ripples and a Fresnel falloff (water reflects strongly at grazing angles
 * near the horizon, and lets its own deep color through when you look down).
 */
final class Reflection {
    private Reflection() {
    }

    /**
     * Fills {@code out} with the lower hemisphere of a sphere of radius r whose texture
     * coordinates come from the mirrored (and rippled) view direction, through {@code uvOf}
     * (a world direction to {u, v}). {@code wrapU} fixes quads that cross the u seam.
     */
    static void sphere(Geometry out, double r, UnaryOperator<double[]> uvOf, double ripple, double time, boolean low) {
        int rows = low ? 24 : 40, cols = low ? 48 : 72;
        double[][] prevPos = null, prevUv = null;
        for (int k = 0; k <= rows; k++) {
            double el = -90 * Math.pow((double) k / rows, 1.7);
            double[][] pos = new double[cols + 1][];
            double[][] uv = new double[cols + 1][];
            for (int i = 0; i <= cols; i++) {
                double az = 360.0 * i / cols;
                pos[i] = SkyMath.scale(SkyMath.dirFromAngles(az, el), r);
                double up = -el;
                double amp = ripple * 1.1 * Math.min(1, up / 5);
                double phase = up * 0.55 + time * 0.8 + 0.6 * Math.sin(Math.toRadians(az) * 2 + time * 0.25);
                double mEl = Math.max(0, up + amp * Math.sin(phase));
                double mAz = az + amp * 0.6 * Math.sin(up * 0.9 - time * 0.6 + Math.toRadians(az) * 5);
                uv[i] = uvOf.apply(SkyMath.dirFromAngles(mAz, Math.min(89.9, mEl)));
            }
            if (prevPos != null) {
                for (int i = 0; i < cols; i++) {
                    double[] u = {prevUv[i][0], prevUv[i + 1][0], uv[i + 1][0], uv[i][0]};
                    double lo = Math.min(Math.min(u[0], u[1]), Math.min(u[2], u[3]));
                    double hi = Math.max(Math.max(u[0], u[1]), Math.max(u[2], u[3]));
                    if (hi - lo > 0.5) {
                        for (int q = 0; q < 4; q++) {
                            if (u[q] < 0.5) {
                                u[q] += 1;
                            }
                        }
                    }
                    out.quad(prevPos[i], u[0], prevUv[i][1], -1,
                            prevPos[i + 1], u[1], prevUv[i + 1][1], -1,
                            pos[i + 1], u[2], uv[i + 1][1], -1,
                            pos[i], u[3], uv[i][1], -1);
                }
            }
            prevPos = pos;
            prevUv = uv;
        }
    }

    /** How much of the sky the water reflects when looking down at an angle (degrees below the horizon). */
    static double reflectance(Profile p, double down) {
        double c = Math.sin(Math.toRadians(Math.max(0, down)));
        double schlick = 0.02 + 0.98 * Math.pow(1 - c, 5);
        return schlick + (1 - schlick) * Settings.REFLECT_CLARITY.v(p) * 0.9;
    }

    /** The water's own color laid over the reflection, stronger the more you look down. */
    static void water(Geometry out, Profile p, SkyState s, Grade grade, int horizon, double r) {
        int base = Colors.mixOklab(grade.apply(Settings.REFLECT_COLOR.rgb(p)), horizon, 0.18);
        base = Colors.scale(base, 1 - 0.65 * s.darkness);
        int rows = 24, cols = 36;
        for (int k = 0; k < rows; k++) {
            double e0 = -90 * Math.pow((double) k / rows, 1.7), e1 = -90 * Math.pow((double) (k + 1) / rows, 1.7);
            int c0 = Colors.argbf(alpha(p, -e0), base), c1 = Colors.argbf(alpha(p, -e1), base);
            for (int i = 0; i < cols; i++) {
                double a0 = 360.0 * i / cols, a1 = 360.0 * (i + 1) / cols;
                out.quad(SkyMath.scale(SkyMath.dirFromAngles(a0, e0), r), 0.5, 0.5, c0,
                        SkyMath.scale(SkyMath.dirFromAngles(a1, e0), r), 0.5, 0.5, c0,
                        SkyMath.scale(SkyMath.dirFromAngles(a1, e1), r), 0.5, 0.5, c1,
                        SkyMath.scale(SkyMath.dirFromAngles(a0, e1), r), 0.5, 0.5, c1);
            }
        }
    }

    private static double alpha(Profile p, double down) {
        return Math.min(1, Math.max(0.06, 1 - reflectance(p, down)) * SkyMath.smooth(0, 1.2, down));
    }
}
