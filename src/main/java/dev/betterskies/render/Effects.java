package dev.betterskies.render;

import dev.betterskies.config.Grade;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import dev.betterskies.util.Colors;

import java.util.List;

/**
 * Builds the animated parts of the sky as geometry each frame: aurora, the
 * sunset glow, meteors, comets and twinkling stars. Pure Java.
 */
public final class Effects {
    private Effects() {
    }

    private static final Noise NOISE = new Noise(2024);

    // ================================================================== sunset glow

    /** A soft colored band hugging the horizon around the sun (vertex colors, white texture). */
    public static void sunsetGlow(Geometry g, Profile p, SkyState s, Grade grade, double r) {
        double sunUp = s.sunHeight;
        double presence = Math.exp(-Math.pow((sunUp - 0.02) / 0.2, 2));
        double weather = 1 - 0.85 * s.rain;
        double strength = Settings.GLOW_STRENGTH.v(p) * presence * weather;
        if (strength < 0.004) {
            return;
        }
        double width = Settings.GLOW_WIDTH.v(p);
        double azSpread = 0.45 + 1.6 * width;
        double elSpread = 9 + 22 * width;
        int inner = grade.apply(Settings.GLOW_COLOR.rgb(p));
        int outer = grade.apply(Settings.GLOW_COLOR2.rgb(p));
        double sunAz = Math.atan2(s.sun[0], -s.sun[2]);
        double horizon = Settings.HORIZON_LINE.v(p);
        int cols = 72;
        double[] els = {horizon - 6, horizon - 1, horizon + 3, horizon + 9, horizon + 18, horizon + 30, horizon + 48};
        for (int j = 0; j < els.length - 1; j++) {
            for (int i = 0; i < cols; i++) {
                double a0 = (double) i / cols * 360, a1 = (double) (i + 1) / cols * 360;
                int c00 = glowColor(a0, els[j], sunAz, azSpread, elSpread, horizon, strength, inner, outer);
                int c10 = glowColor(a1, els[j], sunAz, azSpread, elSpread, horizon, strength, inner, outer);
                int c11 = glowColor(a1, els[j + 1], sunAz, azSpread, elSpread, horizon, strength, inner, outer);
                int c01 = glowColor(a0, els[j + 1], sunAz, azSpread, elSpread, horizon, strength, inner, outer);
                if (((c00 | c10 | c11 | c01) >>> 24) == 0) {
                    continue;
                }
                g.quad(SkyMath.scale(SkyMath.dirFromAngles(a0, els[j]), r), 0.5, 0.5, c00,
                        SkyMath.scale(SkyMath.dirFromAngles(a1, els[j]), r), 0.5, 0.5, c10,
                        SkyMath.scale(SkyMath.dirFromAngles(a1, els[j + 1]), r), 0.5, 0.5, c11,
                        SkyMath.scale(SkyMath.dirFromAngles(a0, els[j + 1]), r), 0.5, 0.5, c01);
            }
        }
    }

    private static int glowColor(double azDeg, double el, double sunAz, double azSpread, double elSpread,
                                 double horizon, double strength, int inner, int outer) {
        double d = Math.toRadians(azDeg) - sunAz;
        d = Math.atan2(Math.sin(d), Math.cos(d));
        double fa = Math.exp(-Math.pow(d / azSpread, 2));
        double fe = el < horizon ? SkyMath.smooth(horizon - 6, horizon, el) : Math.exp(-Math.pow((el - horizon - 2) / elSpread, 2));
        double a = strength * (0.25 + 0.75 * fa) * fe * (0.3 + 0.7 * fa);
        int c = Colors.mixOklab(outer, inner, fa * SkyMath.smooth(elSpread * 1.5, 0, el - horizon));
        return Colors.argbf(Math.min(0.9, a), c);
    }

    // ================================================================== aurora

    public static void aurora(Geometry g, Profile p, SkyState s, Grade grade, double time, double vis, double r, boolean low) {
        String style = Settings.AURORA_STYLE.get(p);
        boolean diffuse = "DIFFUSE".equals(style), ribbons = "RIBBONS".equals(style);
        int bands = diffuse ? 1 : (int) Settings.AURORA_BANDS.v(p);
        double spread = Settings.AURORA_SPREAD.v(p);
        double dir = Settings.AURORA_DIRECTION.v(p);
        double height = Settings.AURORA_HEIGHT.v(p);
        double thick = Settings.AURORA_THICKNESS.v(p);
        double wave = Settings.AURORA_WAVE.v(p) * (ribbons ? 1.5 : 1);
        double rays = ribbons || diffuse ? 0 : Settings.AURORA_RAYS.v(p);
        double speed = Settings.EFFECTS.on(p) ? 0.15 + Settings.AURORA_SPEED.v(p) * 1.2 : 0;
        double alpha = Settings.AURORA_INTENSITY.v(p) * vis * (diffuse ? 0.55 : 0.85);
        int ca = grade.apply(Settings.AURORA_COLOR.rgb(p));
        int cb = grade.apply(Settings.AURORA_COLOR2.rgb(p));
        int cols = low ? 64 : 140;
        boolean full = spread >= 359;
        double t = time * speed;
        for (int b = 0; b < bands; b++) {
            double bandSeed = b * 17.31;
            double centre = dir + (bands == 1 ? 0 : (b - (bands - 1) / 2.0) * spread * 0.18) + Math.sin(t * 0.05 + b) * 6;
            double bandSpread = spread * (bands == 1 ? 1 : 0.7 + 0.3 * SkyMath.hash(b, 9));
            double base = 6 + height * 38 + b * (diffuse ? 0 : 3.5);
            double hgt = (diffuse ? 22 : ribbons ? 7 : 12) + thick * (diffuse ? 30 : 34);
            double bandAlpha = alpha * (bands == 1 ? 1 : 0.65 + 0.35 * SkyMath.hash(b, 3));
            double[] prevBottom = null, prevMid = null, prevTop = null;
            int pcb = 0, pcm = 0, pct = 0;
            for (int i = 0; i <= cols; i++) {
                double x = (double) i / cols;
                double az = centre + (x - 0.5) * bandSpread;
                double fold = wave * (Math.sin(x * 9.0 + t * 0.6 + bandSeed) * 0.55
                        + NOISE.noise(x * 4.0 + bandSeed, t * 0.15, b * 3.7) * 0.9);
                double el0 = base + fold * 9;
                double h = hgt * (0.75 + 0.35 * NOISE.fbm(x * 3 + bandSeed, t * 0.1, 5, 2, 0.5));
                double env = full ? 1 : Math.pow(Math.sin(Math.PI * x), 0.7);
                double ray = 1;
                if (rays > 0) {
                    double rr = NOISE.noise(x * 70 + bandSeed, t * 0.9, b) * 0.5 + 0.5;
                    double rr2 = NOISE.noise(x * 23 + bandSeed * 2, t * 0.4, b + 5) * 0.5 + 0.5;
                    ray = 1 - rays * 0.85 * (1 - Math.pow(rr * 0.6 + rr2 * 0.4, 1.6));
                }
                double flicker = 0.85 + 0.15 * NOISE.noise(x * 2 + bandSeed, t * 0.5, 9);
                double a = bandAlpha * env * ray * flicker;
                double[] bottom = SkyMath.scale(SkyMath.dirFromAngles(az, el0), r);
                double[] mid = SkyMath.scale(SkyMath.dirFromAngles(az, el0 + h * (diffuse ? 0.35 : 0.14)), r);
                double[] top = SkyMath.scale(SkyMath.dirFromAngles(az + fold * 2, el0 + h), r);
                int colBottom = Colors.argbf(0, ca);
                int colMid = Colors.argbf(Math.min(1, a), Colors.mixOklab(ca, 0xFFFFFF, rays * 0.12 * ray));
                int colTop = Colors.argbf(0, cb);
                if (prevBottom != null) {
                    g.quad(prevBottom, 0.5, 0.5, pcb, bottom, 0.5, 0.5, colBottom, mid, 0.5, 0.5, colMid, prevMid, 0.5, 0.5, pcm);
                    // upper part fades through the upper color
                    int pUpper = Colors.argbf(Colors.a(pcm) / 255.0 * 0.55, Colors.mixOklab(ca, cb, 0.6));
                    int upper = Colors.argbf(Colors.a(colMid) / 255.0 * 0.55, Colors.mixOklab(ca, cb, 0.6));
                    double[] pUp = SkyMath.add(SkyMath.scale(prevMid, 0.45), SkyMath.scale(prevTop, 0.55));
                    double[] up = SkyMath.add(SkyMath.scale(mid, 0.45), SkyMath.scale(top, 0.55));
                    g.quad(prevMid, 0.5, 0.5, pcm, mid, 0.5, 0.5, colMid, up, 0.5, 0.5, upper, pUp, 0.5, 0.5, pUpper);
                    g.quad(pUp, 0.5, 0.5, pUpper, up, 0.5, 0.5, upper, top, 0.5, 0.5, colTop, prevTop, 0.5, 0.5, pct);
                }
                prevBottom = bottom;
                prevMid = mid;
                prevTop = top;
                pcb = colBottom;
                pcm = colMid;
                pct = colTop;
            }
        }
    }

    // ================================================================== twinkling stars

    public static void twinkle(Geometry g, Profile p, List<SpaceGen.Star> stars, double time, double vis, double r) {
        double amount = Settings.TWINKLE.v(p);
        double speed = 0.6 + Settings.TWINKLE_SPEED.v(p) * 4;
        for (SpaceGen.Star st : stars) {
            double tw = 1 - amount * 0.7 * (0.5 + 0.5 * Math.sin(time * speed * (0.7 + (st.phase() % 1.0)) + st.phase()))
                    * (0.6 + 0.4 * Math.sin(time * speed * 1.7 + st.phase() * 3));
            double a = Math.min(1, st.brightness() * tw * vis);
            if (a < 0.02) {
                continue;
            }
            g.billboard(st.dir(), r, st.size(), 0, Colors.argbf(a, st.rgb()));
        }
    }

    // ================================================================== meteors

    /** One shooting star at a moment: head direction, travel axis and how far along it is. */
    private record Meteor(double[] start, double[] axis, double travel, double progress, double size) {
    }

    public static void meteors(Geometry heads, Geometry trails, Profile p, SkyState s, Grade grade, double time, double vis, double r) {
        boolean normal = Settings.METEORS.on(p);
        long night = Math.floorDiv(s.dayTime + 6000, 24000L);
        boolean showerNight = Settings.SHOWERS.on(p) && SkyMath.hash(night, 77) < Settings.SHOWER_FREQUENCY.v(p);
        double perMinute = (normal ? 0.6 + Settings.METEOR_RATE.v(p) * 9 : 0)
                + (showerNight ? 6 + Settings.SHOWER_INTENSITY.v(p) * 40 : 0);
        if (perMinute <= 0) {
            return;
        }
        double speed = 0.5 + Settings.METEOR_SPEED.v(p) * 1.8;
        double duration = 1.6 / speed;
        double period = 60.0 / perMinute;
        double[] radiant = SkyMath.dirFromAngles(SkyMath.hash(night, 5) * 360, 35 + SkyMath.hash(night, 6) * 40);
        int head = grade.apply(Settings.METEOR_COLOR.rgb(p));
        int tail = grade.apply(Settings.METEOR_TRAIL.rgb(p));
        double bright = Settings.METEOR_BRIGHTNESS.v(p) * vis;
        double lenK = 0.25 + Settings.METEOR_LENGTH.v(p) * 1.1;
        double width = 0.0016 + Settings.METEOR_SIZE.v(p) * 0.004;
        long first = (long) Math.floor((time - duration) / period) - 1;
        long last = (long) Math.floor(time / period);
        for (long k = first; k <= last; k++) {
            double start = k * period + SkyMath.hash(k, 1) * period;
            double prog = (time - start) / duration;
            if (prog < 0 || prog > 1) {
                continue;
            }
            boolean fromRadiant = showerNight && (!normal || SkyMath.hash(k, 2) < 0.8);
            double[] s0;
            double[] axis;
            if (fromRadiant) {
                double ang = SkyMath.hash(k, 3) * SkyMath.TAU;
                double[] t1 = SkyMath.tangent(radiant), t2 = SkyMath.cross(radiant, t1);
                double off = Math.toRadians(4 + SkyMath.hash(k, 4) * 30);
                double[] tdir = SkyMath.add(SkyMath.scale(t1, Math.cos(ang)), SkyMath.scale(t2, Math.sin(ang)));
                s0 = SkyMath.norm(SkyMath.add(SkyMath.scale(radiant, Math.cos(off)), SkyMath.scale(tdir, Math.sin(off))));
                axis = SkyMath.norm(SkyMath.cross(radiant, s0));
            } else {
                s0 = SkyMath.dirFromAngles(SkyMath.hash(k, 3) * 360, 25 + SkyMath.hash(k, 4) * 50);
                double[] down = SkyMath.norm(SkyMath.sub(new double[]{0, -1, 0}, SkyMath.scale(s0, -s0[1])));
                double[] side = SkyMath.cross(s0, down);
                double tilt = (SkyMath.hash(k, 5) - 0.5) * 2.2;
                double[] motion = SkyMath.norm(SkyMath.add(SkyMath.scale(down, 0.55), SkyMath.scale(side, tilt)));
                axis = SkyMath.norm(SkyMath.cross(s0, motion));
            }
            if (s0[1] < 0.05) {
                continue;
            }
            double travel = Math.toRadians(10 + SkyMath.hash(k, 6) * 22) * (fromRadiant ? 0.8 : 1);
            double sizeK = 0.6 + SkyMath.hash(k, 7) * 0.8;
            drawMeteor(heads, trails, new Meteor(s0, axis, travel, prog, sizeK), head, tail, bright, lenK, width, r);
        }
    }

    private static void drawMeteor(Geometry heads, Geometry trails, Meteor m, int head, int tail, double bright,
                                   double lenK, double width, double r) {
        double env = Math.sin(Math.PI * Math.min(1, m.progress * 1.15)) ;
        env = Math.pow(Math.max(0, env), 0.6);
        double a = bright * env;
        if (a < 0.01) {
            return;
        }
        double headAngle = m.travel * m.progress;
        double trailLen = Math.min(headAngle, m.travel * 0.55 * lenK);
        int n = 14;
        double[][] pts = new double[n][];
        double[] w = new double[n];
        int[] cols = new int[n];
        for (int i = 0; i < n; i++) {
            double f = (double) i / (n - 1);
            pts[i] = SkyMath.rotate(m.start, m.axis, headAngle - trailLen * (1 - f));
            w[i] = width * m.size * (0.15 + 0.85 * f);
            cols[i] = Colors.argbf(a * f * f, Colors.mixOklab(tail, head, f * f));
        }
        trails.ribbon(pts, w, cols, r);
        heads.billboard(pts[n - 1], r, width * m.size * 4.5, 0, Colors.argbf(Math.min(1, a * 1.2), head));
    }

    // ================================================================== comets

    public static void comets(Geometry heads, Geometry trails, Profile p, SkyState s, Grade grade, double time, double vis, double r) {
        double rate = Settings.COMET_RATE.v(p);
        double period = 900 - rate * 720;
        double duration = 150;
        long k = (long) Math.floor(time / period);
        double start = k * period + SkyMath.hash(k, 11) * (period - duration);
        double prog = (time - start) / duration;
        if (prog < 0 || prog > 1) {
            return;
        }
        double env = Math.pow(Math.sin(Math.PI * prog), 0.5);
        double a = Settings.COMET_BRIGHTNESS.v(p) * vis * env;
        if (a < 0.01) {
            return;
        }
        double[] s0 = SkyMath.dirFromAngles(SkyMath.hash(k, 12) * 360, 20 + SkyMath.hash(k, 13) * 45);
        double[] axis = SkyMath.norm(SkyMath.cross(s0, SkyMath.dirFromAngles(SkyMath.hash(k, 14) * 360, 10)));
        double[] headDir = SkyMath.rotate(s0, axis, Math.toRadians(25) * prog);
        // tail points away from the sun
        double[] away = SkyMath.scale(s.sun, -1);
        double tailLen = 0.15 + Settings.COMET_TAIL.v(p) * 0.45;
        int headC = grade.apply(Settings.COMET_COLOR.rgb(p));
        int tailC = grade.apply(Settings.COMET_TAIL_COLOR.rgb(p));
        int n = 22;
        double[][] pts = new double[n][];
        double[] w = new double[n];
        int[] cols = new int[n];
        double[] bend = SkyMath.tangent(headDir);
        for (int i = 0; i < n; i++) {
            double f = (double) i / (n - 1);
            double[] along = SkyMath.slerp(headDir, away, tailLen * (1 - f));
            pts[i] = SkyMath.norm(SkyMath.add(along, SkyMath.scale(bend, 0.03 * (1 - f) * (1 - f))));
            w[i] = 0.004 + 0.022 * (1 - f) * (0.4 + 0.6 * f) + 0.006 * f;
            cols[i] = Colors.argbf(a * Math.pow(f, 1.4) * 0.8, Colors.mixOklab(tailC, headC, f));
        }
        trails.ribbon(pts, w, cols, r);
        heads.billboard(headDir, r, 0.022, 0, Colors.argbf(Math.min(1, a), headC));
        heads.billboard(headDir, r, 0.006, 0, Colors.argbf(Math.min(1, a * 1.3), 0xFFFFFF));
    }

    // ================================================================== realistic clouds

    /** Where on the cloud layer a sky direction lands, and how much cloud is there. */
    public interface CloudProbe {
        /** Cloud opacity (0..1) seen in a direction, including the horizon fade. */
        double alpha(double[] dir);
    }

    /** A lightning flash inside the clouds: direction and brightness (0 = none). */
    public record Flash(double[] dir, double intensity) {
        public static final Flash NONE = new Flash(new double[]{0, 1, 0}, 0);
    }

    /**
     * The cloud layer above the camera as a polar grid (fades smoothly toward the
     * horizon). Fills {@code base} (the clouds) and {@code shine} (sun-colored
     * glow for the silver-lining texture). offA/offB scroll the texture (wind and
     * travel); sunTint is the color of sunlight right now.
     */
    public static void clouds(Geometry base, Geometry shine, Profile p, SkyState s, Grade grade,
                              double offA, double offB, boolean low, int sunTint, Flash flash) {
        double h = cloudHeight(p);
        double maxR = 110;
        double tile = h * 9;
        double windAz = Math.toRadians(Settings.SC_WIND_DIR.v(p));
        double dx = Math.sin(windAz), dz = -Math.cos(windAz);
        double fadeDeg = 2 + Settings.SC_HORIZON_FADE.v(p) * 22;
        double minDeg = Math.toDegrees(Math.atan2(h, maxR));
        double light = SkyMath.lerp(Settings.SC_NIGHT.v(p), 1, 1 - s.darkness) * (1 - 0.35 * s.rain);
        boolean physical = Settings.SKY_MODEL.is(p, "PHYSICAL");
        int sunset = physical ? sunTint : grade.apply(Settings.SC_SUNSET.rgb(p));
        double[] sunFlat = SkyMath.norm(new double[]{s.sun[0], 0, s.sun[2]});
        double sunsetAmount = physical ? 1 - SkyMath.smooth(0.0, 0.5, s.sunHeight) * 0.85
                : Math.max(s.dusk, Math.exp(-Math.pow(s.sunHeight / 0.2, 2)) * (s.sunHeight > -0.3 ? 1 : 0));
        double shineK = Settings.SC_SUN_SHINE.v(p) * SkyMath.smooth(-0.1, 0.04, s.sunHeight) * (1 - 0.7 * s.rain);
        double spread = 0.22 + 0.6 * Settings.SC_SUN_SPREAD.v(p);
        int shineColor = Colors.mixOklab(0xFFFFFF, sunTint, 0.55);
        int rings = low ? 16 : 26, segs = low ? 32 : 56;
        double[][] pos = new double[segs + 1][];
        double[][] uv = new double[segs + 1][];
        int[] col = new int[segs + 1];
        int[] glow = new int[segs + 1];
        double[][] ppos = null, puv = null;
        int[] pcol = null, pglow = null;
        for (int i = 0; i <= rings; i++) {
            double t = (double) i / rings;
            double r = maxR * t * t;
            for (int j = 0; j <= segs; j++) {
                double ang = SkyMath.TAU * j / segs;
                double x = Math.sin(ang) * r, z = -Math.cos(ang) * r;
                pos[j] = new double[]{x, h, z};
                double a = x * dx + z * dz, b = -x * dz + z * dx;
                uv[j] = new double[]{(a - offA) / tile, (b - offB) / tile};
                double el = Math.toDegrees(Math.atan2(h, Math.max(1e-6, r)));
                double fade = SkyMath.smooth(minDeg + 0.5, minDeg + fadeDeg, el);
                double[] dir = SkyMath.norm(pos[j]);
                double[] flat = r < 1e-6 ? sunFlat : SkyMath.norm(new double[]{x, 0, z});
                double toward = Math.max(0, SkyMath.dot(flat, sunFlat));
                double warm = sunsetAmount * (0.3 + 0.7 * toward * toward) * (0.55 + 0.45 * (1 - el / 90));
                int c = Colors.mixOklab(0xFFFFFF, sunset, Math.min(1, warm * (physical ? 0.85 : 1.15)));
                // forward scattering: clouds near the sun get brighter and take on the sunlight's color
                double angle = Math.acos(Math.max(-1, Math.min(1, SkyMath.dot(dir, s.sun))));
                double fs = Math.exp(-Math.pow(angle / spread, 2));
                c = Colors.mix(c, shineColor, fs * shineK * 0.6);
                double bright = light * (1 + fs * shineK * 0.25);
                c = Colors.mix(c, 0x8C9AC0, s.darkness * 0.45);
                c = Colors.mix(c, 0xA6ABB4, s.rain * 0.5);
                if (flash.intensity() > 0) {
                    double fa = Math.acos(Math.max(-1, Math.min(1, SkyMath.dot(dir, flash.dir()))));
                    double f = flash.intensity() * Math.exp(-fa * fa / 0.35);
                    c = Colors.mix(c, 0xEEF2FF, Math.min(1, f));
                    bright = Math.max(bright, Math.min(1, bright + f * 1.2));
                }
                col[j] = Colors.argbf(fade * Math.min(1, 1 + 0.5 * s.rain), Colors.scale(c, Math.min(1, bright)));
                glow[j] = Colors.argbf(fade * Math.min(1, fs * shineK * 1.1), shineColor);
            }
            if (ppos != null) {
                for (int j = 0; j < segs; j++) {
                    base.quad(ppos[j], puv[j][0], puv[j][1], pcol[j],
                            ppos[j + 1], puv[j + 1][0], puv[j + 1][1], pcol[j + 1],
                            pos[j + 1], uv[j + 1][0], uv[j + 1][1], col[j + 1],
                            pos[j], uv[j][0], uv[j][1], col[j]);
                    if (shineK > 0.003 && ((pglow[j] | pglow[j + 1] | glow[j] | glow[j + 1]) >>> 24) > 1) {
                        shine.quad(ppos[j], puv[j][0], puv[j][1], pglow[j],
                                ppos[j + 1], puv[j + 1][0], puv[j + 1][1], pglow[j + 1],
                                pos[j + 1], uv[j + 1][0], uv[j + 1][1], glow[j + 1],
                                pos[j], uv[j][0], uv[j][1], glow[j]);
                    }
                }
            }
            ppos = pos.clone();
            puv = uv.clone();
            pcol = col.clone();
            pglow = glow.clone();
        }
    }

    public static double cloudHeight(Profile p) {
        return 4 + 8 * Settings.SC_HEIGHT.v(p);
    }

    // ================================================================== sun rays

    /**
     * Crepuscular rays: beams fanning out from the sun. With realistic clouds, each
     * beam is as bright as the gap it shines through, so rays appear between clouds.
     */
    public static void sunRays(Geometry g, Profile p, SkyState s, double time, CloudProbe clouds, int color, double r) {
        double sunVis = SkyMath.smooth(-0.06, 0.04, s.sunHeight) * (1 - 0.8 * s.rain);
        if (sunVis < 0.003) {
            return;
        }
        double strength = Settings.RAYS_STRENGTH.v(p) * sunVis * (0.55 + 0.75 * Math.exp(-Math.pow(s.sunHeight / 0.3, 2)));
        int count = 10 + (int) Math.round(Settings.RAYS_COUNT.v(p) * 38);
        double length = Math.toRadians(16 + Settings.RAYS_LENGTH.v(p) * 60);
        boolean cloudsOnly = Settings.RAYS_CLOUDS_ONLY.on(p);
        double[] sun = s.sun;
        double[] t1 = SkyMath.tangent(sun), t2 = SkyMath.cross(sun, t1);
        double start = Math.toRadians(2.2 * Math.max(0.6, Settings.SUN_SIZE.v(p)));
        // how cloudy it is right around the sun
        double presence = 0;
        if (clouds != null) {
            int n = 0;
            for (int k = 0; k < 16; k++) {
                double th = SkyMath.TAU * k / 16;
                for (double d : new double[]{0.06, 0.12, 0.2}) {
                    presence += clouds.alpha(along(sun, t1, t2, th, d));
                    n++;
                }
            }
            presence /= n;
        }
        double cloudFactor = SkyMath.smooth(0.04, 0.35, presence);
        if (cloudsOnly && cloudFactor < 0.01) {
            return;
        }
        double width = 0.018 + 0.05 * (1 - Settings.RAYS_COUNT.v(p));
        int segs = 10;
        for (int k = 0; k < count; k++) {
            double th = SkyMath.TAU * (k + 0.5 * SkyMath.hash(k, 3)) / count + time * 0.004;
            double shimmer = 0.55 + 0.45 * Math.sin(time * (0.15 + 0.2 * SkyMath.hash(k, 9)) + SkyMath.hash(k, 4) * 6.28);
            double intensity;
            if (clouds != null && presence > 0.02) {
                double open = 0;
                for (int q = 1; q <= 5; q++) {
                    open += 1 - clouds.alpha(along(sun, t1, t2, th, start + q * 0.035));
                }
                open /= 5;
                intensity = Math.pow(open, 1.5) * cloudFactor * 1.4 + (cloudsOnly ? 0 : (1 - cloudFactor) * 0.3 * shimmer);
            } else {
                intensity = cloudsOnly ? 0 : 0.3 * shimmer;
            }
            intensity *= strength * (0.6 + 0.4 * SkyMath.hash(k, 7));
            if (intensity < 0.01) {
                continue;
            }
            double len = length * (0.6 + 0.4 * SkyMath.hash(k, 5));
            double[][] pts = new double[segs][];
            double[] hw = new double[segs];
            int[] cols = new int[segs];
            for (int i = 0; i < segs; i++) {
                double f = (double) i / (segs - 1);
                double d = start + (len - start) * f;
                pts[i] = along(sun, t1, t2, th, d);
                hw[i] = Math.max(0.002, d * width);
                double a = intensity * Math.pow(1 - f, 1.6) * SkyMath.smooth(0, 0.12, f);
                cols[i] = Colors.argbf(Math.min(0.85, a), color);
            }
            g.ribbon(pts, hw, cols, r);
        }
    }

    private static double[] along(double[] sun, double[] t1, double[] t2, double th, double d) {
        double c = Math.cos(d), sn = Math.sin(d);
        double ct = Math.cos(th), st = Math.sin(th);
        return new double[]{sun[0] * c + (t1[0] * ct + t2[0] * st) * sn,
                sun[1] * c + (t1[1] * ct + t2[1] * st) * sn,
                sun[2] * c + (t1[2] * ct + t2[2] * st) * sn};
    }
}
