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

    private static List<Entry> build() {
        List<Entry> out = new ArrayList<>();

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
        });

        sky(out, "Vanilla+", "Minecraft's look, just smoother: nicer stars and gradients.", p -> {
            GRAD_DAY.set(p, Gradient.at(0.0, 0xC0D8FF, 0.5, 0x8FB4FF, 1.0, 0x78A7FF));
            GRAD_DUSK.set(p, Gradient.at(0.0, 0xFFA060, 0.35, 0xD88A9A, 1.0, 0x3A4A86));
            GRAD_NIGHT.set(p, Gradient.at(0.0, 0x0E1220, 1.0, 0x000000));
            SUN_STYLE.set(p, "VANILLA");
            MOON_STYLE.set(p, "VANILLA");
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
}
