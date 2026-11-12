package dev.betterskies.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static dev.betterskies.config.Settings.*;

/** Hand-made skies that ship with the mod, and quick color-grade looks. */
public final class BuiltinSkies {
    private BuiltinSkies() {
    }

    public record Entry(String name, String blurb, Profile profile) {
    }

    public record Look(String name, Consumer<Profile> apply) {
    }

    private static List<Entry> skies;

    public static List<Entry> all() {
        if (skies == null) {
            skies = build();
        }
        return skies;
    }

    private static Entry sky(List<Entry> out, String name, String blurb, Consumer<Profile> edit) {
        Profile p = new Profile();
        p.name = name;
        edit.accept(p);
        Entry e = new Entry(name, blurb, p);
        out.add(e);
        return e;
    }

    /** A blank canvas: every effect off and a plain, vanilla-like sky to build on. */
    public static Profile blank() {
        Profile p = new Profile();
        p.name = "Blank sky";
        SKY_MODEL.set(p, "GRADIENT");
        DAY_CYCLE.set(p, true);
        GRAD_DAY.set(p, Gradient.at(0.0, 0xC2D6FF, 1.0, 0x7BA4FF));
        GRAD_DUSK.set(p, Gradient.at(0.0, 0xE8A07A, 1.0, 0x3B5590));
        GRAD_NIGHT.set(p, Gradient.at(0.0, 0x0C1220, 1.0, 0x03050A));
        HAZE.set(p, 0.0);
        GLOW_MODE.set(p, "OFF");
        FOG_TINT.set(p, "OFF");
        CLOUD_TINT.set(p, "VANILLA");
        SUN_STYLE.set(p, "VANILLA");
        MOON_STYLE.set(p, "VANILLA");
        for (Setting.Toggle t : List.of(SUN_RAYS, MOON_HALO, STARS, MILKY_WAY, NEBULA, DUST, AURORA, METEORS, SHOWERS, COMETS, SKY_CLOUDS)) {
            t.set(p, false);
        }
        SC_HIDE_VANILLA.set(p, false);
        CLOUDS.set(p, true);
        IMAGE.set(p, "");
        return p;
    }

    /** Clean Horizon: a real-atmosphere sky with a calm mirror sea, soft far clouds and early stars. Nothing extra. */
    public static Profile clean() {
        Profile p = blank();
        p.name = "Clean Horizon";
        SKY_MODEL.set(p, "PHYSICAL");
        PHYS_HAZE.set(p, 0.12);
        PHYS_NIGHT.set(p, true);
        GRAD_NIGHT.set(p, Gradient.of(0x203C6C, 0x172E58, 0x102244, 0x0A1832, 0x060F22));
        SUN_STYLE.set(p, "SOFT");
        SUN_GLOW.set(p, 0.3);
        MOON_STYLE.set(p, "REALISTIC");
        FOG_TINT.set(p, "HORIZON");
        CLOUD_TINT.set(p, "SKY");
        STARS.set(p, true);
        STAR_DENSITY.set(p, 0.55);
        STAR_BRIGHTNESS.set(p, 1.1);
        TWINKLE.set(p, 0.35);
        STAR_TWILIGHT.set(p, 0.65);
        METEORS.set(p, true);
        METEOR_RATE.set(p, 0.1);
        SKY_CLOUDS.set(p, true);
        SC_TYPE.set(p, "MIXED");
        SC_COVERAGE.set(p, 0.4);
        SC_SOFTNESS.set(p, 0.55);
        SC_WIND.set(p, 0.12);
        SC_DISTANCE.set(p, 0.85);
        SC_HORIZON_FADE.set(p, 0.25);
        SC_AFTERGLOW.set(p, 0.75);
        SC_HIDE_VANILLA.set(p, true);
        REFLECT.set(p, true);
        REFLECT_CLARITY.set(p, 0.75);
        REFLECT_RIPPLE.set(p, 0.2);
        REFLECT_COLOR.set(p, 0x0A1628);
        return p;
    }

    private static List<Entry> build() {
        List<Entry> out = new ArrayList<>();

        Entry blankEntry = new Entry("Blank sky", "Everything off and a plain sky: a clean canvas to build your own.", blank());
        out.add(blankEntry);
        out.add(new Entry("Clean Horizon", "Real sky over a calm mirror sea: soft clouds to the horizon, early stars, nothing extra.", clean()));

        sky(out, "BetterSkies", "The default: a soft day, a rosy sunset and a deep violet night.", p -> {
        });

        sky(out, "Cotton Candy", "The classic BetterSkies pink-to-violet sky, all day long.", p -> {
            DAY_CYCLE.set(p, false);
            GRAD_DAY.set(p, Gradient.at(0.0, 0xFF78C8, 0.5, 0x6A2FA0, 1.0, 0x14081F));
            STAR_COLORS.set(p, "TINTED");
            DAY_STARS.set(p, 0.35);
            FOG_COLOR.set(p, 0xFF78C8);
            CLOUD_TINT.set(p, "SKY");
            NEBULA.set(p, true);
            NEB_INTENSITY.set(p, 0.3);
        });

        sky(out, "Boreal Night", "Crisp arctic skies with tall green and violet northern lights.", p -> {
            GRAD_NIGHT.set(p, Gradient.at(0.0, 0x163A4A, 0.35, 0x0B1B2E, 1.0, 0x02060E));
            GRAD_DUSK.set(p, Gradient.at(0.0, 0xF7A26B, 0.25, 0xB7708F, 0.6, 0x3D4A86, 1.0, 0x101A3A));
            AURORA.set(p, true);
            AURORA_INTENSITY.set(p, 0.75);
            AURORA_BANDS.set(p, 4.0);
            MOON_HALO.set(p, true);
            TEMPERATURE.set(p, -0.15);
            MILKY_WAY.set(p, true);
            MW_BRIGHTNESS.set(p, 0.35);
        });

        sky(out, "Deep Space", "Dark skies full of stars, a blazing Milky Way and nebulae.", p -> {
            GRAD_NIGHT.set(p, Gradient.at(0.0, 0x10121F, 0.4, 0x06070F, 1.0, 0x010103));
            STAR_DENSITY.set(p, 0.85);
            STAR_BRIGHTNESS.set(p, 1.3);
            MW_BRIGHTNESS.set(p, 0.85);
            MW_WIDTH.set(p, 0.6);
            NEBULA.set(p, true);
            NEB_INTENSITY.set(p, 0.55);
            DUST.set(p, true);
            COMETS.set(p, true);
            HAZE.set(p, 0.1);
            SKY_CLOUDS.set(p, false);
        });

        sky(out, "Golden Hour", "A permanent warm sunset glow over the world.", p -> {
            DAY_CYCLE.set(p, false);
            GRAD_DAY.set(p, Gradient.at(0.0, 0xFFC27A, 0.18, 0xFF9A62, 0.5, 0xD97A8E, 1.0, 0x5A5FA8));
            GLOW_STRENGTH.set(p, 0.9);
            SUN_SIZE.set(p, 1.6);
            SUN_GLOW.set(p, 0.8);
            CLOUD_TINT.set(p, "CUSTOM");
            CLOUD_COLOR.set(p, 0xFFD2A8);
            CLOUD_TINT_STRENGTH.set(p, 0.6);
            TEMPERATURE.set(p, 0.2);
            SC_TYPE.set(p, "MIXED");
            SC_SUNSET.set(p, 0xFF8A55);
        });

        sky(out, "Blood Moon", "An ominous red night with a huge crimson moon.", p -> {
            GRAD_NIGHT.set(p, Gradient.at(0.0, 0x5A0E14, 0.35, 0x2A060B, 1.0, 0x0A0103));
            GRAD_DUSK.set(p, Gradient.at(0.0, 0xFF5A3C, 0.3, 0xB0203A, 0.7, 0x4A0C1E, 1.0, 0x16040A));
            MOON_COLOR.set(p, 0xFF5A48);
            MOON_SIZE.set(p, 2.4);
            MOON_GLOW.set(p, 0.8);
            MOON_PHASE.set(p, "0");
            STAR_COLORS.set(p, "TINTED");
            STAR_COLOR.set(p, 0xFFD6CF);
            STAR_COLOR2.set(p, 0xFF7A6B);
            FOG_TINT.set(p, "HORIZON");
            MW_COLOR.set(p, 0xFF8A80);
        });

        sky(out, "Synthwave", "Neon magenta horizon, grid-ready bands and a striped sun.", p -> {
            DAY_CYCLE.set(p, false);
            GRAD_DAY.set(p, Gradient.at(0.0, 0xFF2E97, 0.15, 0xFF6EC7, 0.35, 0x8A2BE2, 0.7, 0x2B0F54, 1.0, 0x0A0220));
            BLEND.set(p, "BANDS");
            SUN_STYLE.set(p, "ORB");
            SUN_COLOR.set(p, 0xFFD23F);
            SUN_SIZE.set(p, 3.0);
            SUN_GLOW_COLOR.set(p, 0xFF2E97);
            DAY_STARS.set(p, 0.8);
            STAR_COLORS.set(p, "TINTED");
            STAR_COLOR.set(p, 0x7DF9FF);
            STAR_COLOR2.set(p, 0xFF6EC7);
            FOG_TINT.set(p, "CUSTOM");
            FOG_COLOR.set(p, 0xFF2E97);
            SATURATION.set(p, 0.15);
        });

        sky(out, "Overcast Calm", "Soft grey-blue skies for a cozy, rainy-day mood.", p -> {
            GRAD_DAY.set(p, Gradient.at(0.0, 0xD9DEE6, 0.5, 0xB4BDCB, 1.0, 0x8E9AAE));
            GRAD_DUSK.set(p, Gradient.at(0.0, 0xD8B4A6, 0.5, 0x8E8EA4, 1.0, 0x4E5468));
            GRAD_NIGHT.set(p, Gradient.at(0.0, 0x2A2E38, 1.0, 0x101218));
            SUN_GLOW.set(p, 0.15);
            STAR_DENSITY.set(p, 0.2);
            MILKY_WAY.set(p, false);
            HAZE.set(p, 0.6);
            SATURATION.set(p, -0.3);
            METEORS.set(p, false);
            CLOUD_TINT.set(p, "VANILLA");
            SC_TYPE.set(p, "STRATUS");
            SC_COVERAGE.set(p, 0.8);
            SC_SHADOW.set(p, 0x8A92A3);
        });

        sky(out, "Alien World", "Teal skies, two-tone stars and a giant violet moon.", p -> {
            GRAD_DAY.set(p, Gradient.at(0.0, 0xB8FFE4, 0.4, 0x3FD1B5, 1.0, 0x0F6C78));
            GRAD_DUSK.set(p, Gradient.at(0.0, 0xFFE07A, 0.3, 0x7CE0A2, 0.65, 0x2B7A9C, 1.0, 0x162A5A));
            GRAD_NIGHT.set(p, Gradient.at(0.0, 0x1B4B4F, 0.5, 0x0A1F2B, 1.0, 0x030A10));
            MOON_COLOR.set(p, 0xC59BFF);
            MOON_SIZE.set(p, 3.4);
            ORBIT_TILT.set(p, 25.0);
            DAY_STARS.set(p, 0.25);
            NEBULA.set(p, true);
            NEB_COLOR.set(p, 0x3FD1B5);
            NEB_COLOR2.set(p, 0xC59BFF);
            AURORA.set(p, true);
            AURORA_STYLE.set(p, "RIBBONS");
            AURORA_NIGHT_ONLY.set(p, false);
            AURORA_AUTO.set(p, false);
            AURORA_COLOR.set(p, 0xFF7AD9);
            AURORA_COLOR2.set(p, 0x7A5CFF);
        });

        sky(out, "Ocean Dream", "Pastel aqua days and calm, starry blue nights.", p -> {
            GRAD_DAY.set(p, Gradient.at(0.0, 0xE3FAFF, 0.4, 0x9BE2F2, 1.0, 0x3A8FD1));
            GRAD_DUSK.set(p, Gradient.at(0.0, 0xFFC3A0, 0.3, 0xF59AB8, 0.65, 0x7E8BD6, 1.0, 0x2A3F7A));
            GRAD_NIGHT.set(p, Gradient.at(0.0, 0x173A6B, 0.5, 0x0A1A38, 1.0, 0x030817));
            BELOW.set(p, "MIRROR");
            MOON_HALO.set(p, true);
            CLOUD_TINT.set(p, "SKY");
        });

        sky(out, "Midnight Violet", "Endless purple night with nebulae and slow comets.", p -> {
            CLOCK.set(p, "FIXED");
            FIXED_TIME.set(p, 18000.0);
            GRAD_NIGHT.set(p, Gradient.at(0.0, 0x4B2A7A, 0.3, 0x2A1652, 0.7, 0x120A2C, 1.0, 0x05030F));
            NEBULA.set(p, true);
            NEB_INTENSITY.set(p, 0.7);
            NEB_COVERAGE.set(p, 0.6);
            COMETS.set(p, true);
            COMET_RATE.set(p, 0.6);
            DUST.set(p, true);
            STAR_COLORS.set(p, "TINTED");
            SKY_CLOUDS.set(p, false);
        });

        sky(out, "Vanilla+", "Minecraft's look, just smoother: nicer stars and gradients.", p -> {
            GRAD_DAY.set(p, Gradient.at(0.0, 0xC0D8FF, 0.5, 0x8FB4FF, 1.0, 0x78A7FF));
            GRAD_DUSK.set(p, Gradient.at(0.0, 0xFFA060, 0.35, 0xD88A9A, 1.0, 0x3A4A86));
            GRAD_NIGHT.set(p, Gradient.at(0.0, 0x0E1220, 1.0, 0x000000));
            SUN_STYLE.set(p, "VANILLA");
            MOON_STYLE.set(p, "VANILLA");
            SKY_CLOUDS.set(p, false);
            GLOW_MODE.set(p, "VANILLA");
            MILKY_WAY.set(p, false);
            STAR_COLORS.set(p, "WHITE");
            FOG_TINT.set(p, "OFF");
            CLOUD_TINT.set(p, "VANILLA");
            HAZE.set(p, 0.1);
        });

        return out;
    }

    // ---------------------------------------------------------------- color grade looks

    private static void neutral(Profile p) {
        for (Setting<?> s : Settings.in(Category.GRADE)) {
            if (s instanceof Setting.Slider) {
                s.reset(p);
            }
        }
    }

    public static final List<Look> LOOKS = List.of(
            new Look("Neutral", BuiltinSkies::neutral),
            new Look("Cinematic", p -> {
                neutral(p);
                CONTRAST.set(p, 0.18);
                SATURATION.set(p, -0.12);
                TEMPERATURE.set(p, 0.08);
                SHADOWS.set(p, -0.15);
            }),
            new Look("Dreamy", p -> {
                neutral(p);
                EXPOSURE.set(p, 0.3);
                CONTRAST.set(p, -0.2);
                TINT.set(p, 0.25);
                VIBRANCE.set(p, 0.3);
            }),
            new Look("Vivid", p -> {
                neutral(p);
                SATURATION.set(p, 0.3);
                VIBRANCE.set(p, 0.45);
                CONTRAST.set(p, 0.1);
            }),
            new Look("Golden", p -> {
                neutral(p);
                TEMPERATURE.set(p, 0.45);
                TINT.set(p, 0.1);
                HIGHLIGHTS.set(p, 0.2);
            }),
            new Look("Arctic", p -> {
                neutral(p);
                TEMPERATURE.set(p, -0.5);
                SATURATION.set(p, -0.15);
                EXPOSURE.set(p, 0.2);
            }),
            new Look("Faded", p -> {
                neutral(p);
                CONTRAST.set(p, -0.3);
                SATURATION.set(p, -0.35);
                SHADOWS.set(p, 0.35);
            }),
            new Look("Noir", p -> {
                neutral(p);
                SATURATION.set(p, -1.0);
                CONTRAST.set(p, 0.3);
            }),
            new Look("Neon", p -> {
                neutral(p);
                HUE_SHIFT.set(p, -25.0);
                SATURATION.set(p, 0.5);
                CONTRAST.set(p, 0.15);
                TINT.set(p, 0.35);
            }));

    // ---------------------------------------------------------------- weather presets

    /** Puts every weather-related setting back to normal before a preset changes some. */
    private static void clearWeather(Profile p) {
        for (Setting<?> s : List.of(SKY_CLOUDS, SC_TYPE, SC_COVERAGE, SC_DENSITY, SC_SIZE, SC_SOFTNESS, SC_DETAIL, SC_WIND,
                SC_HEIGHT, SC_LIT, SC_SHADOW, SC_SUNSET, SC_SUN_SHINE, SC_LIGHTNING, HAZE, FOG_TINT, FOG_STRENGTH,
                STORM_STRENGTH, RAIN_DIM, SUN_RAYS, RAYS_STRENGTH, RAYS_CLOUDS_ONLY, PHYS_HAZE, EXPOSURE, SATURATION, TEMPERATURE)) {
            s.reset(p);
        }
    }

    public static final List<Look> WEATHER = List.of(
            new Look("Clear", p -> {
                clearWeather(p);
                SC_TYPE.set(p, "CIRRUS");
                SC_COVERAGE.set(p, 0.12);
                HAZE.set(p, 0.12);
                PHYS_HAZE.set(p, 0.1);
                RAYS_STRENGTH.set(p, 0.35);
            }),
            new Look("Fair weather", p -> {
                clearWeather(p);
                SC_TYPE.set(p, "CUMULUS");
                SC_COVERAGE.set(p, 0.38);
                SC_DENSITY.set(p, 0.65);
            }),
            new Look("Partly cloudy", p -> {
                clearWeather(p);
                SC_TYPE.set(p, "MIXED");
                SC_COVERAGE.set(p, 0.58);
                SC_DENSITY.set(p, 0.72);
                SC_SIZE.set(p, 0.65);
                RAYS_CLOUDS_ONLY.set(p, true);
                RAYS_STRENGTH.set(p, 0.75);
            }),
            new Look("Overcast", p -> {
                clearWeather(p);
                SC_TYPE.set(p, "STRATUS");
                SC_COVERAGE.set(p, 0.88);
                SC_DENSITY.set(p, 0.85);
                SC_SHADOW.set(p, 0x858D9E);
                SC_LIT.set(p, 0xE4E7EC);
                HAZE.set(p, 0.55);
                PHYS_HAZE.set(p, 0.65);
                SATURATION.set(p, -0.25);
                SUN_RAYS.set(p, false);
            }),
            new Look("Thunderstorm", p -> {
                clearWeather(p);
                SC_TYPE.set(p, "STRATUS");
                SC_COVERAGE.set(p, 0.97);
                SC_DENSITY.set(p, 1.0);
                SC_SOFTNESS.set(p, 0.75);
                SC_LIT.set(p, 0x9EA4AE);
                SC_SHADOW.set(p, 0x3E434E);
                SC_WIND.set(p, 0.7);
                SC_HEIGHT.set(p, 0.25);
                SC_LIGHTNING.set(p, true);
                STORM_STRENGTH.set(p, 0.9);
                RAIN_DIM.set(p, 0.75);
                FOG_STRENGTH.set(p, 0.85);
                EXPOSURE.set(p, -0.6);
                SATURATION.set(p, -0.35);
                SUN_RAYS.set(p, false);
            }),
            new Look("Foggy morning", p -> {
                clearWeather(p);
                SC_TYPE.set(p, "STRATUS");
                SC_COVERAGE.set(p, 0.62);
                SC_DENSITY.set(p, 0.45);
                SC_SOFTNESS.set(p, 1.0);
                SC_HEIGHT.set(p, 0.05);
                HAZE.set(p, 1.0);
                PHYS_HAZE.set(p, 0.95);
                FOG_TINT.set(p, "HORIZON");
                FOG_STRENGTH.set(p, 0.95);
                RAYS_STRENGTH.set(p, 0.8);
                SATURATION.set(p, -0.15);
            }),
            new Look("Hazy summer", p -> {
                clearWeather(p);
                SC_TYPE.set(p, "SCATTERED");
                SC_COVERAGE.set(p, 0.35);
                HAZE.set(p, 0.75);
                PHYS_HAZE.set(p, 0.75);
                TEMPERATURE.set(p, 0.15);
                RAYS_STRENGTH.set(p, 0.7);
            }),
            new Look("Golden evening", p -> {
                clearWeather(p);
                SC_TYPE.set(p, "MIXED");
                SC_COVERAGE.set(p, 0.45);
                SC_SUNSET.set(p, 0xFF9450);
                SC_SUN_SHINE.set(p, 1.0);
                RAYS_STRENGTH.set(p, 0.85);
                TEMPERATURE.set(p, 0.18);
            }),
            new Look("Winter night", p -> {
                clearWeather(p);
                SC_TYPE.set(p, "CIRRUS");
                SC_COVERAGE.set(p, 0.15);
                HAZE.set(p, 0.08);
                PHYS_HAZE.set(p, 0.05);
                TEMPERATURE.set(p, -0.3);
            }));
}
