package dev.betterskies.render;

import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;

/**
 * Everything about "now" the sky needs: time, sun position, how much of day,
 * dusk and night is showing, and weather. Pure Java.
 */
public final class SkyState {
    public final long dayTime;
    public final double angle;
    public final double[] sun;
    public final double sunHeight;
    public final boolean morning;
    public final double day;
    public final double dusk;
    public final double night;
    /** 0 at day, 1 in full darkness: how visible stars and space are. */
    public final double darkness;
    public final double rain;
    public final double thunder;
    public final int moonPhase;

    public SkyState(Profile p, long worldDayTime, double rain, double thunder) {
        long t = worldDayTime;
        if (Settings.CLOCK.is(p, "FIXED")) {
            t = (worldDayTime / 24000L) * 24000L + (long) Settings.FIXED_TIME.v(p);
        }
        this.dayTime = t;
        this.angle = SkyMath.celestialAngle(t);
        this.sun = SkyMath.sunDir(angle, Settings.ORBIT_TILT.v(p));
        this.sunHeight = sun[1];
        this.morning = sun[0] > 0;
        double w = 0.10 + 0.34 * Settings.CYCLE_SOFTNESS.v(p);
        double d = SkyMath.smooth(-0.02, w, sunHeight);
        double n = SkyMath.smooth(0.02, w + 0.06, -sunHeight);
        if (!Settings.DAY_CYCLE.on(p)) {
            d = 1;
            n = 0;
        }
        this.day = d;
        this.night = n;
        this.dusk = Math.max(0, 1 - d - n);
        this.darkness = SkyMath.smooth(0.04, -0.2, sunHeight);
        this.rain = rain;
        this.thunder = thunder;
        int phase = SkyMath.moonPhase(t);
        String fixed = Settings.MOON_PHASE.get(p);
        if (!"AUTO".equals(fixed)) {
            try {
                phase = Integer.parseInt(fixed);
            } catch (NumberFormatException ignored) {
                // keep game phase
            }
        }
        this.moonPhase = phase;
    }

    /** How much space effects show (darkness, daytime stars, rain). */
    public double spaceVisibility(Profile p) {
        double v = Math.max(darkness, Settings.DAY_STARS.v(p));
        // stars coming out in the deep blue after sunset
        double tw = Settings.STAR_TWILIGHT.v(p);
        if (tw > 0) {
            v = Math.max(v, Math.min(1, tw * 1.3) * SkyMath.smooth(0.05, -0.09, sunHeight));
        }
        if (Settings.HIDE_IN_RAIN.on(p)) {
            v *= 1 - rain;
        }
        return v;
    }

    /** Time of day in ticks within the current day (0 = 6:00). */
    public int tickOfDay() {
        return (int) Math.floorMod(dayTime, 24000L);
    }
}
