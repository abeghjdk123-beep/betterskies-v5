package dev.betterskies.render;

import dev.betterskies.config.Grade;
import dev.betterskies.config.Gradient;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import dev.betterskies.util.Colors;

/**
 * Turns gradients + time + weather into actual sky colors, as a vertical strip
 * (top row = zenith, bottom row = nadir). Used by the renderer and the editor's
 * previews. Pure Java.
 */
public final class SkyColors {
    private SkyColors() {
    }

    /** The day-cycle-blended color at a gradient position (0 horizon, 1 zenith). */
    public static int gradientAt(Profile p, SkyState s, double t) {
        Gradient.Blend blend = Gradient.Blend.valueOf(Settings.BLEND.get(p));
        double bias = Settings.BIAS.v(p);
        int day = Settings.GRAD_DAY.get(p).sample(t, blend, bias);
        if (!Settings.DAY_CYCLE.on(p) || s.day >= 0.999) {
            return day;
        }
        Gradient duskG = s.morning && Settings.SEPARATE_DAWN.on(p) ? Settings.GRAD_DAWN.get(p) : Settings.GRAD_DUSK.get(p);
        int dusk = duskG.sample(t, blend, bias);
        int night = Settings.GRAD_NIGHT.get(p).sample(t, blend, bias);
        // night -> dusk -> day, mixed perceptually
        int c = Colors.mixOklab(night, dusk, s.dusk / Math.max(1e-6, s.dusk + s.night));
        return Colors.mixOklab(c, day, s.day);
    }

    /** Sky color at an elevation in degrees, before weather and grading. */
    public static int colorAtElevation(Profile p, SkyState s, double elevation) {
        double horizon = Settings.HORIZON_LINE.v(p);
        double span = Math.max(horizon + 5, Settings.GRADIENT_SPAN.v(p));
        int c;
        if (elevation >= horizon) {
            c = gradientAt(p, s, (elevation - horizon) / (span - horizon));
        } else {
            int h = gradientAt(p, s, 0);
            switch (Settings.BELOW.get(p)) {
                case "EXTEND" -> c = h;
                case "MIRROR" -> c = gradientAt(p, s, (horizon - elevation) / (span - horizon));
                default -> {
                    double k = SkyMath.smooth(0, 25, horizon - elevation);
                    int ground = Settings.GROUND.rgb(p);
                    // the ground color follows the light a little so nights stay dark
                    double light = 0.35 + 0.65 * (1 - s.darkness);
                    c = Colors.mixOklab(h, Colors.scale(ground, light), k);
                }
            }
        }
        double haze = Settings.HAZE.v(p);
        if (haze > 0) {
            double band = Math.exp(-Math.pow((elevation - horizon) / 9.0, 2));
            int bright = Colors.mix(gradientAt(p, s, 0), 0xFFFFFF, 0.35 * (1 - s.darkness * 0.8));
            c = Colors.mix(c, bright, band * haze * 0.6);
        }
        return c;
    }

    /** How strongly the Real atmosphere shows over the gradient right now (0 when using gradients). */
    public static double physicalWeight(Profile p, SkyState s) {
        if (!Settings.SKY_MODEL.is(p, "PHYSICAL")) {
            return 0;
        }
        double w = 1 - Settings.PHYS_BLEND.v(p);
        if (Settings.PHYS_NIGHT.on(p)) {
            w *= 1 - 0.97 * s.darkness;
        }
        return w;
    }

    /** The color you'd actually see at an elevation (gradient mixed with the real atmosphere), for previews. */
    public static int skyColorAt(Profile p, SkyState s, double elevation) {
        int c = colorAtElevation(p, s, elevation);
        double w = physicalWeight(p, s);
        if (w > 0.003) {
            double sunAz = Math.toDegrees(Math.atan2(s.sun[0], -s.sun[2]));
            int phys = PhysicalSky.color(SkyMath.dirFromAngles(sunAz + 100, elevation), s.sun, PhysicalSky.Params.of(p));
            c = Colors.mix(c, phys, w);
        }
        return c;
    }

    /** Applies rain darkening and the storm tint. */
    public static int weather(Profile p, SkyState s, int c) {
        double storm = Math.max(s.rain, s.thunder) * Settings.STORM_STRENGTH.v(p);
        if (storm > 0) {
            c = Colors.mix(c, Colors.scale(Settings.STORM_TINT.rgb(p), 1 - 0.7 * s.darkness), storm * 0.75);
        }
        double dim = s.rain * Settings.RAIN_DIM.v(p) * 0.6 + s.thunder * 0.15;
        return dim > 0 ? Colors.scale(c, 1 - dim) : c;
    }

    /**
     * Fills {@code out} with sky colors from the zenith (index 0) to the nadir
     * (last index), opaque ARGB, graded.
     */
    public static void strip(Profile p, SkyState s, Grade grade, int[] out) {
        int n = out.length;
        for (int i = 0; i < n; i++) {
            double v = (i + 0.5) / n;
            double el = (0.5 - v) * 180.0;
            int c = weather(p, s, colorAtElevation(p, s, el));
            out[i] = 0xFF000000 | grade.apply(c);
        }
    }

    /** Current horizon color (graded) for fog and clouds. */
    public static int horizon(Profile p, SkyState s, Grade grade) {
        return grade.apply(weather(p, s, colorAtElevation(p, s, Settings.HORIZON_LINE.v(p) + 3)));
    }
}
