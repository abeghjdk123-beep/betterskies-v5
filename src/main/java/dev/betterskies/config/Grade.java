package dev.betterskies.config;

import dev.betterskies.util.Colors;

/**
 * The sky-only color grade, captured from a profile so it can be applied to
 * thousands of pixels on a background thread without touching the profile.
 */
public final class Grade {
    private final double exposure;
    private final double contrast;
    private final double highlights;
    private final double shadows;
    private final double gamma;
    private final double saturation;
    private final double vibrance;
    private final double temperature;
    private final double tint;
    private final double hue;
    private final boolean neutral;

    public static final Grade NONE = new Grade();

    private Grade() {
        exposure = 1;
        contrast = 0;
        highlights = 0;
        shadows = 0;
        gamma = 1;
        saturation = 0;
        vibrance = 0;
        temperature = 0;
        tint = 0;
        hue = 0;
        neutral = true;
    }

    public Grade(Profile p) {
        exposure = Math.pow(2, Settings.EXPOSURE.v(p));
        contrast = Settings.CONTRAST.v(p);
        highlights = Settings.HIGHLIGHTS.v(p);
        shadows = Settings.SHADOWS.v(p);
        gamma = Settings.GAMMA.v(p);
        saturation = Settings.SATURATION.v(p);
        vibrance = Settings.VIBRANCE.v(p);
        temperature = Settings.TEMPERATURE.v(p);
        tint = Settings.TINT.v(p);
        hue = Settings.HUE_SHIFT.v(p);
        neutral = exposure == 1 && contrast == 0 && highlights == 0 && shadows == 0 && gamma == 1
                && saturation == 0 && vibrance == 0 && temperature == 0 && tint == 0 && hue == 0;
    }

    public boolean neutral() {
        return neutral;
    }

    /** Grades an RGB color. */
    public int apply(int rgb) {
        if (neutral) {
            return rgb & 0xFFFFFF;
        }
        if (hue != 0) {
            double[] hsv = Colors.toHsv(rgb);
            rgb = Colors.hsv(hsv[0] + hue / 360.0, hsv[1], hsv[2]);
        }
        double r = Colors.r(rgb) / 255.0, g = Colors.g(rgb) / 255.0, b = Colors.b(rgb) / 255.0;
        // white balance
        r *= 1 + 0.18 * temperature;
        b *= 1 - 0.18 * temperature;
        g *= 1 - 0.12 * tint;
        r *= 1 + 0.04 * tint;
        b *= 1 + 0.04 * tint;
        // exposure
        r *= exposure;
        g *= exposure;
        b *= exposure;
        // saturation and vibrance around luminance
        double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double sat = max <= 1e-6 ? 0 : (max - min) / max;
        double k = (1 + saturation) * (1 + vibrance * (1 - sat));
        r = lum + (r - lum) * k;
        g = lum + (g - lum) * k;
        b = lum + (b - lum) * k;
        r = tone(r);
        g = tone(g);
        b = tone(b);
        return Colors.rgbf(r, g, b);
    }

    /**
     * Grades light that is added on top of the sky (stars, glows). Pure black must
     * stay black, otherwise lifted shadows would show the outline of every sprite.
     */
    public int applyEmissive(int rgb) {
        if (neutral || (rgb & 0xFFFFFF) == 0) {
            return rgb & 0xFFFFFF;
        }
        int g = apply(rgb);
        int max = Math.max(Colors.r(rgb), Math.max(Colors.g(rgb), Colors.b(rgb)));
        double k = Math.min(1, max / 40.0);
        int lifted = Colors.mix(rgb, g, k);
        return Colors.luminance(lifted) > 0 ? lifted : 0;
    }

    public int applyArgb(int argb) {
        return (argb & 0xFF000000) | apply(argb);
    }

    private double tone(double v) {
        v = (v - 0.5) * (1 + contrast * 1.4) + 0.5;
        double l = Math.max(0, Math.min(1, v));
        v += highlights * l * l * 0.35 + shadows * (1 - l) * (1 - l) * 0.35;
        v = Math.max(0, v);
        return gamma == 1 ? v : Math.pow(v, 1 / gamma);
    }
}
