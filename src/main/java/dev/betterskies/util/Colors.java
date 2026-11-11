package dev.betterskies.util;

import java.util.Locale;

/** Small color toolbox. Colors are packed 0xRRGGBB (or 0xAARRGGBB where noted). */
public final class Colors {
    private Colors() {
    }

    public static int r(int c) {
        return (c >> 16) & 255;
    }

    public static int g(int c) {
        return (c >> 8) & 255;
    }

    public static int b(int c) {
        return c & 255;
    }

    public static int a(int c) {
        return (c >>> 24) & 255;
    }

    public static int clamp255(double v) {
        return v <= 0 ? 0 : v >= 255 ? 255 : (int) Math.round(v);
    }

    public static int rgb(int r, int g, int b) {
        return (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    public static int rgbf(double r, double g, double b) {
        return (clamp255(r * 255) << 16) | (clamp255(g * 255) << 8) | clamp255(b * 255);
    }

    public static int argb(int alpha, int rgb) {
        return (clamp255(alpha) << 24) | (rgb & 0xFFFFFF);
    }

    public static int argbf(double alpha, int rgb) {
        return argb((int) Math.round(Math.max(0, Math.min(1, alpha)) * 255), rgb);
    }

    /** Opaque color for GUI drawing. */
    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** Linear mix in sRGB. */
    public static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        return rgb((int) Math.round(r(a) + (r(b) - r(a)) * t),
                (int) Math.round(g(a) + (g(b) - g(a)) * t),
                (int) Math.round(b(a) + (b(b) - b(a)) * t));
    }

    /** Mix including alpha (ARGB). */
    public static int mixArgb(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int al = (int) Math.round(a(a) + (a(b) - a(a)) * t);
        return (clamp255(al) << 24) | mix(a, b, t);
    }

    public static int scale(int c, double k) {
        return rgb((int) (r(c) * k), (int) (g(c) * k), (int) (b(c) * k));
    }

    public static double luminance(int c) {
        return (0.2126 * r(c) + 0.7152 * g(c) + 0.0722 * b(c)) / 255.0;
    }

    // ---------------------------------------------------------------- hex

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** Parses #RGB, #RRGGBB (with or without #). Returns -1 when invalid. */
    public static int parse(String s) {
        if (s == null) {
            return -1;
        }
        s = s.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        } else if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        if (s.length() == 3) {
            s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        }
        if (s.length() != 6) {
            return -1;
        }
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static int parseOr(String s, int fallback) {
        int v = parse(s);
        return v < 0 ? fallback : v;
    }

    // ---------------------------------------------------------------- HSV

    /** h in [0,1), s,v in [0,1]. */
    public static int hsv(double h, double s, double v) {
        h = h - Math.floor(h);
        double c = v * s;
        double x = c * (1 - Math.abs((h * 6) % 2 - 1));
        double m = v - c;
        double r, g, b;
        int seg = (int) (h * 6);
        switch (seg) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return rgbf(r + m, g + m, b + m);
    }

    /** Returns {h, s, v} each in [0,1]. */
    public static double[] toHsv(int rgb) {
        double r = r(rgb) / 255.0, g = g(rgb) / 255.0, b = b(rgb) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double d = max - min;
        double h = 0;
        if (d > 1e-9) {
            if (max == r) {
                h = ((g - b) / d) % 6;
            } else if (max == g) {
                h = (b - r) / d + 2;
            } else {
                h = (r - g) / d + 4;
            }
            h /= 6;
            if (h < 0) {
                h += 1;
            }
        }
        double s = max <= 0 ? 0 : d / max;
        return new double[]{h, s, max};
    }

    // ---------------------------------------------------------------- OKLab (perceptual mixing)

    private static double toLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double toSrgb(double c) {
        c = Math.max(0, c);
        return c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
    }

    public static double[] toOklab(int rgb) {
        double r = toLinear(r(rgb) / 255.0), g = toLinear(g(rgb) / 255.0), b = toLinear(b(rgb) / 255.0);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[]{
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s};
    }

    public static int fromOklab(double L, double A, double B) {
        double l = L + 0.3963377774 * A + 0.2158037573 * B;
        double m = L - 0.1055613458 * A - 0.0638541728 * B;
        double s = L - 0.0894841775 * A - 1.2914855480 * B;
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        return rgbf(toSrgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
                toSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
                toSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s));
    }

    /** Perceptual mix: no muddy grey band between complementary colors. */
    public static int mixOklab(int a, int b, double t) {
        if (a == b) {
            return a;
        }
        t = Math.max(0, Math.min(1, t));
        double[] x = toOklab(a), y = toOklab(b);
        return fromOklab(x[0] + (y[0] - x[0]) * t, x[1] + (y[1] - x[1]) * t, x[2] + (y[2] - x[2]) * t);
    }

    /** Black-body-ish tint for a star of the given temperature in [0,1] (0 = red dwarf, 1 = blue giant). */
    public static int starTint(double t) {
        int[] ramp = {0xFFB46B, 0xFFD2A1, 0xFFF4EA, 0xF8F7FF, 0xCAD8FF, 0x9BB0FF};
        double p = Math.max(0, Math.min(0.9999, t)) * (ramp.length - 1);
        int i = (int) p;
        return mix(ramp[i], ramp[i + 1], p - i);
    }
}
