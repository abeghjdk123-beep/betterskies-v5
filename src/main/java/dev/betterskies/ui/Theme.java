package dev.betterskies.ui;

import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import dev.betterskies.util.Colors;

/** Editor colors. The accent comes from the Studio settings; the rest is a dark glassy palette. */
public final class Theme {
    public final int accent;
    public final int accent2;
    public final int panel;
    public final int panelEdge;
    public final int card;
    public final int cardHover;
    public final int cardEdge;
    public final int field;
    public final int fieldHover;
    public final int text;
    public final int textDim;
    public final int textFaint;
    public final int good;
    public final int warn;
    public final int bad;
    public final double opacity;

    public Theme(Profile p) {
        int[] a = switch (Settings.ACCENT.get(p)) {
            case "OCEAN" -> new int[]{0x3FA9FF, 0x35E0E0};
            case "SUNSET" -> new int[]{0xFF7A59, 0xFFB347};
            case "MINT" -> new int[]{0x3BE3A4, 0x7AF0D0};
            case "ROSE" -> new int[]{0xFF5C9A, 0xFF9AC8};
            case "GOLD" -> new int[]{0xFFC14D, 0xFFE08A};
            case "FROST" -> new int[]{0x8FD3FF, 0xD6ECFF};
            default -> new int[]{0xA45CFF, 0xE07BFF};
        };
        accent = a[0];
        accent2 = a[1];
        opacity = Settings.UI_OPACITY.v(p);
        int base = Colors.mix(0x0C0A12, a[0], 0.05);
        panel = Colors.argbf(opacity, base);
        panelEdge = Colors.argbf(0.9, Colors.mix(0x2A2438, a[0], 0.35));
        card = Colors.argbf(Math.min(1, opacity + 0.05), Colors.mix(0x16131E, a[0], 0.04));
        cardHover = Colors.argbf(Math.min(1, opacity + 0.08), Colors.mix(0x1E1A28, a[0], 0.08));
        cardEdge = Colors.argbf(0.85, Colors.mix(0x2C2638, a[0], 0.12));
        field = 0xFF241F2E;
        fieldHover = 0xFF2E283A;
        text = 0xFFF3EFFA;
        textDim = 0xFFADA5C0;
        textFaint = 0xFF6F6782;
        good = 0xFF5BE39B;
        warn = 0xFFFFC857;
        bad = 0xFFFF6B7A;
    }

    public int accent(double alpha) {
        return Colors.argbf(alpha, accent);
    }

    public int accentText() {
        return 0xFF000000 | accent;
    }
}
