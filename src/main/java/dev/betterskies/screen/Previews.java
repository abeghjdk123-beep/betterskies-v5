package dev.betterskies.screen;

import dev.betterskies.config.Grade;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import dev.betterskies.render.SkyColors;
import dev.betterskies.render.SkyMath;
import dev.betterskies.render.SkyState;
import dev.betterskies.ui.Ui;
import dev.betterskies.util.Colors;

import java.util.HashMap;
import java.util.Map;

/** Little painted skies for cards and the timeline, cached per sky. */
final class Previews {
    private record Key(int hash, int h) {
    }

    private final Map<Key, int[][]> cache = new HashMap<>();
    private int[] timeline;
    private int[] timelineTop;
    private long timelineRev = -1;
    private int timelineW;

    static final int[] TICKS = {6000, 12400, 18000};

    /** Columns of a sky at day, dusk and night (rows top to bottom), from 70° above to 15° below the horizon. */
    int[][] slices(Profile p, int h) {
        Key k = new Key(p.hashCode() * 31 + p.name.hashCode(), h);
        int[][] v = cache.get(k);
        if (v == null) {
            if (cache.size() > 120) {
                cache.clear();
            }
            Grade grade = new Grade(p);
            v = new int[TICKS.length][h];
            for (int i = 0; i < TICKS.length; i++) {
                SkyState s = new SkyState(p, TICKS[i], 0, 0);
                for (int y = 0; y < h; y++) {
                    double el = 70 - 85.0 * y / Math.max(1, h - 1);
                    v[i][y] = grade.apply(SkyColors.skyColorAt(p, s, el));
                }
            }
            cache.put(k, v);
        }
        return v;
    }

    /** Draws a sky card preview: three time slices with stars, sun and moon hints. */
    void sky(Ui ui, Profile p, int x, int y, int w, int h) {
        int[][] sl = slices(p, h);
        int third = w / 3;
        for (int i = 0; i < 3; i++) {
            int x0 = x + i * third, x1 = i == 2 ? x + w : x0 + third;
            int[] col = sl[i];
            for (int yy = 0; yy < h; yy++) {
                ui.rect(x0, y + yy, x1 - x0, 1, 0xFF000000 | col[yy]);
            }
        }
        int horizonY = y + (int) Math.round(h * 70 / 85.0);
        // night: a few stars, aurora band
        int nx = x + 2 * third, nw = w - 2 * third;
        if (Settings.STARS.on(p)) {
            int n = (int) (6 + 18 * Settings.STAR_DENSITY.v(p));
            for (int i = 0; i < n; i++) {
                int sx = nx + (int) (SkyMath.hash(i, 1) * nw), sy = y + (int) (SkyMath.hash(i, 2) * (horizonY - y - 3));
                ui.rect(sx, sy, 1, 1, SkyMath.hash(i, 3) > 0.8 ? 0xFFFFFFFF : 0xB0FFFFFF);
            }
        }
        if (Settings.AURORA.on(p)) {
            int ac = Settings.AURORA_COLOR.rgb(p);
            for (int i = 0; i < nw; i++) {
                int ay = horizonY - 8 - (int) (Math.sin(i * 0.35) * 2) - (int) (Settings.AURORA_HEIGHT.v(p) * 6);
                ui.gradientV(nx + i, ay - 6, 1, 6, Colors.argbf(0, ac), Colors.argbf(0.7, ac));
            }
        }
        if (!Settings.MOON_STYLE.is(p, "HIDDEN")) {
            int mr = (int) Math.max(2, Math.min(6, 2.5 * Settings.MOON_SIZE.v(p)));
            ui.circle(nx + nw - 8, y + 7, mr, 0xFF000000 | Settings.MOON_COLOR.rgb(p));
        }
        if (!Settings.SUN_STYLE.is(p, "HIDDEN")) {
            int sr = (int) Math.max(2, Math.min(6, 3 * Settings.SUN_SIZE.v(p)));
            int sc = 0xFF000000 | Settings.SUN_COLOR.rgb(p);
            ui.circle(x + third / 2, y + 8, sr + 2, Colors.argbf(0.25, sc));
            ui.circle(x + third / 2, y + 8, sr, sc);
            ui.circle(x + third + third / 2, horizonY - 2, sr, Colors.opaque(Colors.mix(sc, 0xFF8040, 0.4)));
        }
        // ground line
        ui.rect(x, horizonY, w, 1, 0x30FFFFFF);
    }

    /** 24h strip of sky colors (top = zenith, bottom = horizon) for the time scrubber. */
    void timeline(Ui ui, Profile p, long rev, int x, int y, int w, int h) {
        if (timeline == null || timelineRev != rev || timelineW != w) {
            timelineRev = rev;
            timelineW = w;
            timeline = new int[w];
            timelineTop = new int[w];
            Grade grade = new Grade(p);
            for (int i = 0; i < w; i++) {
                int tick = (int) ((i + 0.5) / w * 24000);
                SkyState s = new SkyState(p, tick, 0, 0);
                timeline[i] = grade.apply(SkyColors.skyColorAt(p, s, Settings.HORIZON_LINE.v(p) + 2));
                timelineTop[i] = grade.apply(SkyColors.skyColorAt(p, s, 75));
            }
        }
        for (int i = 0; i < w; i++) {
            ui.gradientV(x + i, y, 1, h, 0xFF000000 | timelineTop[i], 0xFF000000 | timeline[i]);
        }
    }

    void invalidate() {
        cache.clear();
        timelineRev = -1;
    }
}
