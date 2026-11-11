package dev.betterskies.render;

import dev.betterskies.config.Grade;
import dev.betterskies.config.Gradient;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import dev.betterskies.util.Colors;

/**
 * Aurora colors. "Natural colors" uses the real emission colors (oxygen green low
 * down, oxygen red and pink higher up, a nitrogen purple-pink fringe along the
 * bottom edge) and lets your sky nudge them: the night sky's hue, nebula colors
 * and moonlight all leave a mark. Pure Java.
 */
public final class AuroraColors {
    private AuroraColors() {
    }

    /** {lower, upper, fringe, glow} as RGB. */
    public static int[] palette(Profile p, SkyState s, Grade grade) {
        int lower, upper, fringe;
        String style = Settings.AURORA_STYLE.get(p);
        if (Settings.AURORA_AUTO.on(p)) {
            lower = 0x44FF92;
            upper = 0xFF3DA0;
            fringe = 0xC65CFF;
            // the night sky's own hue tints the aurora a little (more when the sky is colorful)
            int sky = nightSky(p);
            double[] hsv = Colors.toHsv(sky);
            double sat = Math.min(1, hsv[1] * 1.4);
            int skyHue = Colors.hsv(hsv[0], 0.75, 1);
            lower = Colors.mixOklab(lower, skyHue, 0.12 * sat);
            upper = Colors.mixOklab(upper, skyHue, 0.3 * sat);
            fringe = Colors.mixOklab(fringe, skyHue, 0.2 * sat);
            if (Settings.NEBULA.on(p)) {
                upper = Colors.mixOklab(upper, Settings.NEB_COLOR2.rgb(p), 0.3);
                fringe = Colors.mixOklab(fringe, Settings.NEB_COLOR.rgb(p), 0.25);
            }
            if ("STORM".equals(style)) {
                // strong storms push more pink and violet into the display
                upper = Colors.mixOklab(upper, 0xFF4FD8, 0.35);
                fringe = Colors.mixOklab(fringe, 0xFF5AC8, 0.4);
            }
        } else {
            lower = Settings.AURORA_COLOR.rgb(p);
            upper = Settings.AURORA_COLOR2.rgb(p);
            fringe = Colors.mixOklab(lower, upper, 0.65);
        }
        int glow = Colors.mixOklab(lower, upper, 0.35);
        return new int[]{grade.apply(lower), grade.apply(upper), grade.apply(fringe), grade.apply(glow)};
    }

    /** How bright the display is overall: bright moonlight and a bright sky wash it out. */
    public static double visibility(Profile p, SkyState s) {
        double moon = Settings.MOON_STYLE.is(p, "HIDDEN") ? 0 : 0.5 + 0.5 * Math.cos(s.moonPhase * Math.PI / 4);
        double moonUp = SkyMath.clamp01(-s.sunHeight * 3);
        return 1 - 0.18 * moon * moonUp;
    }

    private static int nightSky(Profile p) {
        if (Settings.SKY_MODEL.is(p, "PHYSICAL") && !Settings.PHYS_NIGHT.on(p)) {
            return 0x0B1428;
        }
        Gradient night = Settings.DAY_CYCLE.on(p) ? Settings.GRAD_NIGHT.get(p) : Settings.GRAD_DAY.get(p);
        return night.average();
    }
}
