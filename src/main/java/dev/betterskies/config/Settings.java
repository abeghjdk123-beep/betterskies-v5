package dev.betterskies.config;

import dev.betterskies.config.Setting.Choice;
import dev.betterskies.config.Setting.Choice.Option;
import dev.betterskies.config.Setting.Color;
import dev.betterskies.config.Setting.Grad;
import dev.betterskies.config.Setting.Slider;
import dev.betterskies.config.Setting.Text;
import dev.betterskies.config.Setting.Toggle;
import dev.betterskies.config.Setting.Unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Every BetterSkies setting. Declaration order is display order. Each one carries
 * its own label, help text, range and section, so the editor, search, favorites
 * and save files all stay in sync automatically.
 */
public final class Settings {
    private Settings() {
    }

    private static final List<Setting<?>> ALL = new ArrayList<>();
    private static final Map<String, Setting<?>> BY_ID = new LinkedHashMap<>();
    private static Category cat = Category.HOME;
    private static String sec = "";

    // ================================================================== master (studio)
    static {
        page(Category.HOME, "Sky");
    }

    public static final Toggle ENABLED = global(toggle("enabled", "BetterSkies sky", true,
            "Master switch. Off = the normal Minecraft sky (your settings are kept)."));

    // ================================================================== colors
    static {
        page(Category.COLORS, "Day cycle");
    }

    public static final Toggle DAY_CYCLE = toggle("day_cycle", "Day cycle", true,
            "Blend sunrise, day, sunset and night gradients as the sun moves. Off = the Day gradient all the time.");
    public static final Grad GRAD_DAY = grad("grad_day", "Day",
            Gradient.at(0.0, 0xBFE3FF, 0.35, 0x7DB8F5, 1.0, 0x2F66C9),
            "Sky colors at midday, from the horizon (left) to straight up (right).");
    public static final Grad GRAD_DUSK = when(grad("grad_dusk", "Sunset",
            Gradient.at(0.0, 0xFF9A5C, 0.22, 0xFF6F91, 0.55, 0x8A3FB0, 1.0, 0x24164F),
            "Sky colors while the sun sets."), p -> DAY_CYCLE.on(p));
    public static final Toggle SEPARATE_DAWN = adv(when(toggle("separate_dawn", "Separate sunrise", false,
            "Give sunrise its own gradient. Off = sunrise reuses the Sunset colors."), p -> DAY_CYCLE.on(p)));
    public static final Grad GRAD_DAWN = when(grad("grad_dawn", "Sunrise",
            Gradient.at(0.0, 0xFFC08A, 0.3, 0xE79BC8, 0.65, 0x7C8FE0, 1.0, 0x33468F),
            "Sky colors while the sun rises."), p -> DAY_CYCLE.on(p) && SEPARATE_DAWN.on(p));
    public static final Grad GRAD_NIGHT = when(grad("grad_night", "Night",
            Gradient.at(0.0, 0x2B1D55, 0.4, 0x120C2E, 1.0, 0x05040E),
            "Sky colors at night."), p -> DAY_CYCLE.on(p));
    public static final Slider CYCLE_SOFTNESS = adv(when(slider("cycle_softness", "Transition length", 0.5, 0.1, 1, 0.01, Unit.PERCENT,
            "How long the sky takes to fade between day, sunset and night."), p -> DAY_CYCLE.on(p)));

    static {
        section("Blending");
    }

    public static final Choice BLEND = choice("blend", "Color blending", "SMOOTH",
            "How neighbouring colors of a gradient mix.",
            opt("SMOOTH", Gradient.Blend.SMOOTH.label, Gradient.Blend.SMOOTH.help),
            opt("PERCEPTUAL", Gradient.Blend.PERCEPTUAL.label, Gradient.Blend.PERCEPTUAL.help),
            opt("LINEAR", Gradient.Blend.LINEAR.label, Gradient.Blend.LINEAR.help),
            opt("BANDS", Gradient.Blend.BANDS.label, Gradient.Blend.BANDS.help));
    public static final Slider BIAS = adv(slider("bias", "Blend midpoint", 0.5, 0, 1, 0.01, Unit.PERCENT,
            "Moves every color transition toward the horizon (lower) or the zenith (higher)."));

    static {
        section("Horizon");
    }

    public static final Slider HORIZON_LINE = slider("horizon_line", "Horizon line", 0, -20, 30, 1, Unit.DEGREES,
            "Where the bottom color of the gradient sits. Raise it for a taller band of horizon color.");
    public static final Slider GRADIENT_SPAN = adv(slider("gradient_span", "Gradient height", 90, 20, 90, 1, Unit.DEGREES,
            "Angle above the horizon where the top color is reached."));
    public static final Slider HAZE = slider("haze", "Horizon haze", 0.25, 0, 1, 0.01, Unit.PERCENT,
            "A soft bright band hugging the horizon, like real atmosphere.");
    public static final Choice BELOW = choice("below", "Below the horizon", "FADE",
            "What you see when you look below the horizon (e.g. from a high tower or over the void).",
            opt("FADE", "Fade to ground", "Blend from the horizon color into the ground color."),
            opt("EXTEND", "Extend horizon", "Keep the horizon color all the way down."),
            opt("MIRROR", "Mirror", "Reflect the sky gradient, like a calm sea."));
    public static final Color GROUND = when(color("ground", "Ground color", 0x0F0B1C,
            "Color far below the horizon."), p -> BELOW.is(p, "FADE"));

    // ================================================================== sun & moon
    static {
        page(Category.SUN_MOON, "Sun");
    }

    public static final Choice SUN_STYLE = choice("sun_style", "Sun style", "SOFT", "Look of the sun.",
            opt("SOFT", "Soft disc", "A round sun with a gentle limb and glow."),
            opt("ORB", "Glowing orb", "A brighter, bloomy orb with a strong halo."),
            opt("PIXEL", "Pixel", "A square sun that fits Minecraft's look, recolorable."),
            opt("VANILLA", "Vanilla", "Minecraft's own sun texture."),
            opt("HIDDEN", "Hidden", "No sun."));
    private static final Predicate<Profile> CUSTOM_SUN = p -> !SUN_STYLE.is(p, "VANILLA") && !SUN_STYLE.is(p, "HIDDEN");
    public static final Slider SUN_SIZE = when(slider("sun_size", "Sun size", 1, 0.25, 4, 0.05, Unit.MULTIPLIER,
            "Size compared to the vanilla sun."), CUSTOM_SUN);
    public static final Color SUN_COLOR = when(color("sun_color", "Sun color", 0xFFF1C9, "Color of the sun disc."), CUSTOM_SUN);
    public static final Slider SUN_GLOW = when(slider("sun_glow", "Sun glow", 0.45, 0, 1, 0.01, Unit.PERCENT,
            "Soft light around the sun."), CUSTOM_SUN);
    public static final Color SUN_GLOW_COLOR = adv(when(color("sun_glow_color", "Glow color", 0xFFB46B,
            "Color of the light around the sun."), CUSTOM_SUN));
    public static final Slider SUN_CORONA = adv(when(slider("sun_corona", "Corona rays", 0.2, 0, 1, 0.01, Unit.PERCENT,
            "Faint rays streaming out of the sun."), CUSTOM_SUN));

    static {
        section("Moon");
    }

    public static final Choice MOON_STYLE = choice("moon_style", "Moon style", "REALISTIC", "Look of the moon.",
            opt("REALISTIC", "Realistic", "Craters, maria and true phase shading."),
            opt("SOFT", "Soft disc", "A smooth glowing disc with phases."),
            opt("PIXEL", "Pixel", "A blocky moon with phases, recolorable."),
            opt("VANILLA", "Vanilla", "Minecraft's own moon texture."),
            opt("HIDDEN", "Hidden", "No moon."));
    private static final Predicate<Profile> CUSTOM_MOON = p -> !MOON_STYLE.is(p, "VANILLA") && !MOON_STYLE.is(p, "HIDDEN");
    public static final Slider MOON_SIZE = when(slider("moon_size", "Moon size", 1, 0.25, 4, 0.05, Unit.MULTIPLIER,
            "Size compared to the vanilla moon."), CUSTOM_MOON);
    public static final Color MOON_COLOR = when(color("moon_color", "Moon color", 0xDDE6FF, "Color of the lit part of the moon."), CUSTOM_MOON);
    public static final Slider MOON_GLOW = when(slider("moon_glow", "Moon glow", 0.25, 0, 1, 0.01, Unit.PERCENT,
            "Soft light around the moon."), CUSTOM_MOON);
    public static final Choice MOON_PHASE = when(choice("moon_phase", "Moon phase", "AUTO", "Which phase the moon shows.",
            opt("AUTO", "Follow the game", "Same phase as vanilla Minecraft (changes every night)."),
            opt("0", "Full moon", ""), opt("1", "Waning gibbous", ""), opt("2", "Last quarter", ""),
            opt("3", "Waning crescent", ""), opt("4", "New moon", ""), opt("5", "Waxing crescent", ""),
            opt("6", "First quarter", ""), opt("7", "Waxing gibbous", "")), CUSTOM_MOON);
    public static final Toggle MOON_HALO = when(toggle("moon_halo", "Moon halo", false,
            "A thin icy ring around the moon, like on cold hazy nights."), CUSTOM_MOON);
    public static final Slider EARTHSHINE = adv(when(slider("earthshine", "Earthshine", 0.15, 0, 1, 0.01, Unit.PERCENT,
            "Faint light on the dark part of the moon."), CUSTOM_MOON));

    static {
        section("Sky path");
    }

    public static final Slider ORBIT_TILT = slider("orbit_tilt", "Orbit tilt", 0, -45, 45, 1, Unit.DEGREES,
            "Tilts the path of the sun, moon and stars so they arc across the sky instead of straight overhead.");

    // ================================================================== stars & space
    static {
        page(Category.STARS, "Stars");
    }

    public static final Toggle STARS = toggle("stars", "Stars", true, "BetterSkies' star field (replaces vanilla stars).");
    private static final Predicate<Profile> STARS_ON = p -> STARS.on(p);
    public static final Slider STAR_DENSITY = when(slider("star_density", "Star count", 0.5, 0, 1, 0.01, Unit.PERCENT,
            "How many stars fill the sky."), STARS_ON);
    public static final Slider STAR_BRIGHTNESS = when(slider("star_brightness", "Brightness", 1, 0.1, 2.5, 0.05, Unit.MULTIPLIER,
            "Overall star brightness."), STARS_ON);
    public static final Slider STAR_SIZE = when(slider("star_size", "Star size", 1, 0.5, 3, 0.05, Unit.MULTIPLIER,
            "Size of each star."), STARS_ON);
    public static final Choice STAR_COLORS = when(choice("star_colors", "Star colors", "REALISTIC", "How stars are colored.",
            opt("REALISTIC", "Realistic", "Real star temperatures: mostly white, some blue, orange and red."),
            opt("TINTED", "Two-tone", "Mix of your two star colors."),
            opt("WHITE", "Single color", "Every star uses the main star color.")), STARS_ON);
    public static final Color STAR_COLOR = when(color("star_color", "Main star color", 0xFFFFFF, "Star color for Two-tone and Single color."),
            p -> STARS.on(p) && !STAR_COLORS.is(p, "REALISTIC"));
    public static final Color STAR_COLOR2 = when(color("star_color2", "Second star color", 0xB9A4FF, "Second star color for Two-tone."),
            p -> STARS.on(p) && STAR_COLORS.is(p, "TINTED"));
    public static final Slider TWINKLE = when(slider("twinkle", "Twinkle", 0.4, 0, 1, 0.01, Unit.PERCENT,
            "How much bright stars shimmer."), STARS_ON);
    public static final Slider TWINKLE_SPEED = adv(when(slider("twinkle_speed", "Twinkle speed", 0.4, 0, 1, 0.01, Unit.PERCENT,
            "How fast stars shimmer."), STARS_ON));
    public static final Slider DAY_STARS = when(slider("day_stars", "Daytime stars", 0, 0, 1, 0.01, Unit.PERCENT,
            "Keep stars visible during the day (great for alien or fantasy skies)."), STARS_ON);
    public static final Slider STAR_SEED = adv(when(slider("star_seed", "Star pattern", 42, 1, 999, 1, Unit.INTEGER,
            "Pick a different arrangement of stars and space."), STARS_ON));

    static {
        section("Milky Way");
    }

    public static final Toggle MILKY_WAY = toggle("milky_way", "Milky Way", true, "A glowing band of distant stars across the night sky.");
    private static final Predicate<Profile> MW_ON = p -> MILKY_WAY.on(p);
    public static final Slider MW_BRIGHTNESS = when(slider("mw_brightness", "Brightness", 0.5, 0, 1, 0.01, Unit.PERCENT, "Milky Way brightness."), MW_ON);
    public static final Slider MW_WIDTH = when(slider("mw_width", "Width", 0.45, 0, 1, 0.01, Unit.PERCENT, "How wide the band is."), MW_ON);
    public static final Slider MW_TILT = when(slider("mw_tilt", "Angle", 30, -90, 90, 1, Unit.DEGREES, "Angle of the band across the sky."), MW_ON);
    public static final Color MW_COLOR = when(color("mw_color", "Color", 0x9FB4FF, "Color of the band."), MW_ON);
    public static final Color MW_CORE = adv(when(color("mw_core", "Core color", 0xFFD9A8, "Warm color of the galactic core."), MW_ON));
    public static final Slider MW_DUST = adv(when(slider("mw_dust", "Dust lanes", 0.6, 0, 1, 0.01, Unit.PERCENT,
            "Dark clouds of dust that split the band."), MW_ON));

    static {
        section("Nebulae");
    }

    public static final Toggle NEBULA = toggle("nebula", "Nebulae", false, "Colorful gas clouds drifting among the stars.");
    private static final Predicate<Profile> NEB_ON = p -> NEBULA.on(p);
    public static final Color NEB_COLOR = when(color("neb_color", "Color A", 0x7664FF, "Main nebula color."), NEB_ON);
    public static final Color NEB_COLOR2 = when(color("neb_color2", "Color B", 0xEC6CBF, "Second nebula color."), NEB_ON);
    public static final Slider NEB_INTENSITY = when(slider("neb_intensity", "Intensity", 0.5, 0, 1, 0.01, Unit.PERCENT, "How bright the clouds are."), NEB_ON);
    public static final Slider NEB_COVERAGE = when(slider("neb_coverage", "Coverage", 0.45, 0, 1, 0.01, Unit.PERCENT, "How much of the sky they cover."), NEB_ON);
    public static final Slider NEB_SCALE = when(slider("neb_scale", "Cloud size", 0.5, 0, 1, 0.01, Unit.PERCENT, "Size of the cloud shapes."), NEB_ON);
    public static final Slider NEB_DETAIL = adv(when(slider("neb_detail", "Detail", 0.6, 0, 1, 0.01, Unit.PERCENT, "Amount of fine wispy detail."), NEB_ON));

    static {
        section("Cosmic dust");
    }

    public static final Toggle DUST = toggle("dust", "Cosmic dust", false, "A faint glittering haze over the whole night sky.");
    public static final Slider DUST_DENSITY = when(slider("dust_density", "Density", 0.35, 0, 1, 0.01, Unit.PERCENT, "How much dust."), p -> DUST.on(p));
    public static final Color DUST_COLOR = adv(when(color("dust_color", "Color", 0x8C7BFF, "Dust color."), p -> DUST.on(p)));

    static {
        section("Motion");
    }

    public static final Slider SKY_DRIFT = slider("sky_drift", "Space drift", 0, 0, 1, 0.01, Unit.PERCENT,
            "Slowly turns the stars and nebulae on their own, independent of time.");

    // ================================================================== aurora
    static {
        page(Category.AURORA, "Aurora");
    }

    public static final Toggle AURORA = toggle("aurora", "Northern lights", false, "Glowing aurora curtains in the night sky.");
    private static final Predicate<Profile> AU_ON = p -> AURORA.on(p);
    public static final Choice AURORA_STYLE = when(choice("aurora_style", "Style", "CURTAINS", "Shape of the aurora.",
            opt("CURTAINS", "Curtains", "Tall folded curtains with fine vertical rays (realistic)."),
            opt("RIBBONS", "Ribbons", "Smooth flowing ribbons of light."),
            opt("DIFFUSE", "Diffuse glow", "A broad soft glow low in the sky.")), AU_ON);
    public static final Color AURORA_COLOR = when(color("aurora_color", "Lower color", 0x52FFB3, "Color at the bottom edge (brightest part)."), AU_ON);
    public static final Color AURORA_COLOR2 = when(color("aurora_color2", "Upper color", 0x9C58FF, "Color the curtains fade into at the top."), AU_ON);
    public static final Slider AURORA_INTENSITY = when(slider("aurora_intensity", "Intensity", 0.6, 0, 1, 0.01, Unit.PERCENT, "Brightness of the aurora."), AU_ON);
    public static final Slider AURORA_BANDS = when(slider("aurora_bands", "Bands", 3, 1, 6, 1, Unit.INTEGER, "Number of overlapping curtains."), AU_ON);
    public static final Slider AURORA_HEIGHT = when(slider("aurora_height", "Height in sky", 0.4, 0, 1, 0.01, Unit.PERCENT, "How high above the horizon it starts."), AU_ON);
    public static final Slider AURORA_THICKNESS = when(slider("aurora_thickness", "Curtain height", 0.45, 0, 1, 0.01, Unit.PERCENT, "How tall the curtains are."), AU_ON);
    public static final Slider AURORA_SPREAD = when(slider("aurora_spread", "Spread", 150, 30, 360, 5, Unit.DEGREES, "How much of the sky the aurora spans."), AU_ON);
    public static final Slider AURORA_SPEED = when(slider("aurora_speed", "Motion speed", 0.3, 0, 1, 0.01, Unit.PERCENT, "How fast it ripples."), AU_ON);
    public static final Slider AURORA_WAVE = adv(when(slider("aurora_wave", "Folds", 0.6, 0, 1, 0.01, Unit.PERCENT, "How strongly the curtains fold and wave."), AU_ON));
    public static final Slider AURORA_RAYS = adv(when(slider("aurora_rays", "Ray detail", 0.6, 0, 1, 0.01, Unit.PERCENT, "Fine vertical rays inside the curtains."), AU_ON));
    public static final Slider AURORA_DIRECTION = adv(when(slider("aurora_direction", "Direction", 0, 0, 359, 1, Unit.DEGREES, "Compass direction it sits in (0° = north)."), AU_ON));
    public static final Toggle AURORA_NIGHT_ONLY = when(toggle("aurora_night_only", "Night only", true, "Fade the aurora out during the day."), AU_ON);

    // ================================================================== meteors & comets
    static {
        page(Category.METEORS, "Shooting stars");
    }

    public static final Toggle METEORS = toggle("meteors", "Shooting stars", true, "Occasional meteors streaking across the night sky.");
    private static final Predicate<Profile> MET_ON = p -> METEORS.on(p) || SHOWERS_ON(p);
    public static final Slider METEOR_RATE = when(slider("meteor_rate", "How often", 0.3, 0, 1, 0.01, Unit.PERCENT, "How many shooting stars appear."), p -> METEORS.on(p));
    public static final Slider METEOR_SPEED = when(slider("meteor_speed", "Speed", 0.5, 0, 1, 0.01, Unit.PERCENT, "How fast they move."), MET_ON);
    public static final Slider METEOR_LENGTH = when(slider("meteor_length", "Trail length", 0.5, 0, 1, 0.01, Unit.PERCENT, "Length of the glowing trail."), MET_ON);
    public static final Slider METEOR_BRIGHTNESS = when(slider("meteor_brightness", "Brightness", 0.7, 0, 1, 0.01, Unit.PERCENT, "How bright they are."), MET_ON);
    public static final Slider METEOR_SIZE = adv(when(slider("meteor_size", "Size", 0.4, 0, 1, 0.01, Unit.PERCENT, "Thickness of the meteor head."), MET_ON));
    public static final Color METEOR_COLOR = when(color("meteor_color", "Head color", 0xF2F6FF, "Color of the meteor head."), MET_ON);
    public static final Color METEOR_TRAIL = adv(when(color("meteor_trail", "Trail color", 0x9FC2FF, "Color the trail fades into."), MET_ON));

    static {
        section("Meteor showers");
    }

    public static final Toggle SHOWERS = toggle("showers", "Meteor showers", true,
            "Some nights bring a shower: many meteors radiating from one point in the sky.");
    public static final Slider SHOWER_FREQUENCY = when(slider("shower_frequency", "Shower nights", 0.25, 0, 1, 0.01, Unit.PERCENT,
            "Share of nights that have a meteor shower."), p -> SHOWERS.on(p));
    public static final Slider SHOWER_INTENSITY = when(slider("shower_intensity", "Shower strength", 0.6, 0, 1, 0.01, Unit.PERCENT,
            "How many meteors fall during a shower."), p -> SHOWERS.on(p));

    static {
        section("Comets");
    }

    public static final Toggle COMETS = toggle("comets", "Comets", false, "Rare comets that slowly cross the sky with long glowing tails.");
    private static final Predicate<Profile> COM_ON = p -> COMETS.on(p);
    public static final Slider COMET_RATE = when(slider("comet_rate", "How often", 0.25, 0, 1, 0.01, Unit.PERCENT, "How often a comet appears."), COM_ON);
    public static final Slider COMET_BRIGHTNESS = when(slider("comet_brightness", "Brightness", 0.65, 0, 1, 0.01, Unit.PERCENT, "Comet brightness."), COM_ON);
    public static final Slider COMET_TAIL = when(slider("comet_tail", "Tail length", 0.55, 0, 1, 0.01, Unit.PERCENT, "Length of the tail."), COM_ON);
    public static final Color COMET_COLOR = when(color("comet_color", "Head color", 0xD8E8FF, "Color of the comet head."), COM_ON);
    public static final Color COMET_TAIL_COLOR = adv(when(color("comet_tail_color", "Tail color", 0x6FA8FF, "Color of the tail."), COM_ON));

    // ================================================================== clouds & weather
    static {
        page(Category.ATMOSPHERE, "Sky clouds");
    }

    public static final Toggle SKY_CLOUDS = toggle("sky_clouds", "Realistic clouds", true,
            "Soft, natural clouds painted by BetterSkies: choose their type, coverage, wind and colors.");
    private static final Predicate<Profile> SC_ON = p -> SKY_CLOUDS.on(p);
    public static final Choice SC_TYPE = when(choice("sc_type", "Cloud type", "CUMULUS", "What kind of clouds fill the sky.",
            opt("CUMULUS", "Puffy (cumulus)", "Fluffy fair-weather clouds with soft rounded tops."),
            opt("CIRRUS", "Wispy (cirrus)", "Thin, streaky high clouds stretched by the wind."),
            opt("MIXED", "Mixed", "Puffy clouds with wispy streaks above them."),
            opt("STRATUS", "Layered (stratus)", "A soft, wide blanket of cloud, great for gloomy days."),
            opt("SCATTERED", "Scattered", "Small separate puffs dotted across the sky.")), SC_ON);
    public static final Slider SC_COVERAGE = when(slider("sc_coverage", "Coverage", 0.45, 0, 1, 0.01, Unit.PERCENT,
            "How much of the sky is covered: a few wisps (low) to fully overcast (high)."), SC_ON);
    public static final Slider SC_DENSITY = when(slider("sc_density", "Thickness", 0.6, 0, 1, 0.01, Unit.PERCENT,
            "Thin and see-through (low) to thick and solid (high)."), SC_ON);
    public static final Slider SC_SIZE = when(slider("sc_size", "Cloud size", 0.5, 0, 1, 0.01, Unit.PERCENT,
            "Small patches (low) to huge rolling cloud masses (high)."), SC_ON);
    public static final Slider SC_SOFTNESS = when(slider("sc_softness", "Edge softness", 0.5, 0, 1, 0.01, Unit.PERCENT,
            "Crisp, defined edges (low) to soft, misty edges (high)."), SC_ON);
    public static final Slider SC_DETAIL = adv(when(slider("sc_detail", "Detail", 0.6, 0, 1, 0.01, Unit.PERCENT,
            "Amount of small wisps and texture inside the clouds."), SC_ON));
    public static final Slider SC_SEED = when(slider("sc_seed", "Cloud pattern", 7, 1, 999, 1, Unit.INTEGER,
            "Pick a different random arrangement of clouds."), SC_ON);
    public static final Slider SC_WIND = when(slider("sc_wind", "Wind speed", 0.25, 0, 1, 0.01, Unit.PERCENT,
            "How fast the clouds drift across the sky (0 = still)."), SC_ON);
    public static final Slider SC_WIND_DIR = when(slider("sc_wind_dir", "Wind direction", 60, 0, 359, 1, Unit.DEGREES,
            "Compass direction the clouds drift toward (0° = north, 90° = east)."), SC_ON);
    public static final Slider SC_HEIGHT = when(slider("sc_height", "Altitude", 0.5, 0, 1, 0.01, Unit.PERCENT,
            "Low clouds stretch to the horizon; high clouds sit more overhead."), SC_ON);
    public static final Slider SC_HORIZON_FADE = adv(when(slider("sc_horizon_fade", "Horizon fade", 0.5, 0, 1, 0.01, Unit.PERCENT,
            "How early clouds fade out toward the horizon."), SC_ON));
    public static final Color SC_LIT = when(color("sc_lit", "Sunlit color", 0xFFFFFF, "Color of the bright, sunlit parts."), SC_ON);
    public static final Color SC_SHADOW = when(color("sc_shadow", "Shadow color", 0x9AA5BE, "Color of the thick, shaded cores."), SC_ON);
    public static final Color SC_SUNSET = when(color("sc_sunset", "Sunset color", 0xFF9A6E,
            "Glow the clouds take on at sunrise and sunset (strongest toward the sun)."), SC_ON);
    public static final Slider SC_NIGHT = adv(when(slider("sc_night", "Night brightness", 0.12, 0, 1, 0.01, Unit.PERCENT,
            "How visible clouds stay at night (moonlight)."), SC_ON));
    public static final Toggle SC_PARALLAX = adv(when(toggle("sc_parallax", "Move as you walk", true,
            "Clouds slowly slide past as you travel, like real distant clouds."), SC_ON));
    public static final Toggle SC_HIDE_VANILLA = when(toggle("sc_hide_vanilla", "Hide blocky clouds", true,
            "Hide Minecraft's blocky clouds while realistic clouds are on."), SC_ON);

    static {
        section("Blocky clouds");
    }

    public static final Toggle CLOUDS = toggle("clouds", "Show blocky clouds", true,
            "Draw Minecraft's own blocky clouds (also needs Clouds turned on in Video Settings).");
    public static final Slider CLOUD_OPACITY = when(slider("cloud_opacity", "Opacity", 1, 0, 1, 0.01, Unit.PERCENT, "Cloud transparency."), p -> CLOUDS.on(p));
    public static final Choice CLOUD_TINT = when(choice("cloud_tint", "Cloud color", "SKY", "How clouds are colored.",
            opt("VANILLA", "Vanilla", "Minecraft's own cloud color."),
            opt("SKY", "Match the sky", "Tint clouds with the current horizon color."),
            opt("CUSTOM", "Custom", "Tint clouds with your own color.")), p -> CLOUDS.on(p));
    public static final Color CLOUD_COLOR = when(color("cloud_color", "Custom color", 0xFFE6F4, "Cloud tint for Custom."),
            p -> CLOUDS.on(p) && CLOUD_TINT.is(p, "CUSTOM"));
    public static final Slider CLOUD_TINT_STRENGTH = when(slider("cloud_tint_strength", "Tint strength", 0.45, 0, 1, 0.01, Unit.PERCENT,
            "How strongly the tint colors the clouds."), p -> CLOUDS.on(p) && !CLOUD_TINT.is(p, "VANILLA"));
    public static final Slider CLOUD_HEIGHT = when(slider("cloud_height", "Height", 0, -64, 256, 1, Unit.BLOCKS,
            "Raise or lower the cloud layer."), p -> CLOUDS.on(p));

    static {
        section("Fog");
    }

    public static final Choice FOG_TINT = choice("fog_tint", "Fog color", "HORIZON", "Tints distance fog so the world blends into your sky.",
            opt("OFF", "Vanilla", "Minecraft's own fog color."),
            opt("HORIZON", "Match the horizon", "Fog takes the horizon color of your sky."),
            opt("CUSTOM", "Custom", "Fog takes your own color."));
    public static final Color FOG_COLOR = when(color("fog_color", "Custom color", 0xFF8FC8, "Fog color for Custom."), p -> FOG_TINT.is(p, "CUSTOM"));
    public static final Slider FOG_STRENGTH = when(slider("fog_strength", "Strength", 0.6, 0, 1, 0.01, Unit.PERCENT,
            "How much of the fog color is replaced."), p -> !FOG_TINT.is(p, "OFF"));

    static {
        section("Sunrise & sunset glow");
    }

    public static final Choice GLOW_MODE = choice("glow_mode", "Sunset glow", "BETTERSKIES", "The colored glow around the sun at sunrise and sunset.",
            opt("BETTERSKIES", "BetterSkies", "A wide soft glow that wraps around the horizon."),
            opt("VANILLA", "Vanilla", "Minecraft's own sunset fan."),
            opt("OFF", "Off", "No glow."));
    private static final Predicate<Profile> GLOW_ON = p -> GLOW_MODE.is(p, "BETTERSKIES");
    public static final Slider GLOW_STRENGTH = when(slider("glow_strength", "Strength", 0.6, 0, 1, 0.01, Unit.PERCENT, "How bright the glow is."), GLOW_ON);
    public static final Slider GLOW_WIDTH = when(slider("glow_width", "Width", 0.5, 0, 1, 0.01, Unit.PERCENT, "How far it spreads along the horizon."), GLOW_ON);
    public static final Color GLOW_COLOR = when(color("glow_color", "Inner color", 0xFFA45C, "Color closest to the sun."), GLOW_ON);
    public static final Color GLOW_COLOR2 = adv(when(color("glow_color2", "Outer color", 0xFF4F8B, "Color at the edge of the glow."), GLOW_ON));

    static {
        section("Weather");
    }

    public static final Slider RAIN_DIM = slider("rain_dim", "Rain darkening", 0.55, 0, 1, 0.01, Unit.PERCENT, "How much rain darkens the sky.");
    public static final Color STORM_TINT = adv(color("storm_tint", "Storm color", 0x4E5A70, "Color the sky shifts toward in rain and thunder."));
    public static final Slider STORM_STRENGTH = adv(slider("storm_strength", "Storm tint", 0.5, 0, 1, 0.01, Unit.PERCENT, "How strongly storms tint the sky."));
    public static final Toggle HIDE_IN_RAIN = toggle("hide_in_rain", "Hide space in rain", true,
            "Fade out stars, aurora and meteors while it rains.");

    static {
        section("Sky clock");
    }

    public static final Choice CLOCK = choice("clock", "Sky time", "WORLD", "Which time of day the sky shows. Only the sky changes; the world's light is untouched.",
            opt("WORLD", "Follow the world", "Normal day/night cycle."),
            opt("FIXED", "Fixed time", "Always show the same moment."));
    public static final Slider FIXED_TIME = when(slider("fixed_time", "Fixed at", 13000, 0, 23950, 50, Unit.TIME_OF_DAY,
            "The time the sky is frozen at."), p -> CLOCK.is(p, "FIXED"));

    // ================================================================== images
    static {
        page(Category.IMAGES, "Sky image");
    }

    public static final Text IMAGE = text("image", "Sky image", "", "Picture shown as a sky layer (choose one in the list below).");
    private static final Predicate<Profile> IMG_ON = p -> !IMAGE.get(p).isEmpty();
    public static final Slider IMAGE_OPACITY = when(slider("image_opacity", "Opacity", 1, 0, 1, 0.01, Unit.PERCENT, "Image transparency."), IMG_ON);
    public static final Choice IMAGE_BLEND = when(choice("image_blend", "Blending", "NORMAL", "How the image combines with the sky behind it.",
            opt("NORMAL", "Normal", "Covers the gradient (use transparency in the picture to let it through)."),
            opt("ADD", "Glow (add)", "Adds light on top of the sky. Great for star or nebula pictures.")), IMG_ON);
    public static final Choice IMAGE_SHOW = when(choice("image_show", "Visible", "ALWAYS", "When the image appears.",
            opt("ALWAYS", "Always", ""), opt("NIGHT", "At night", "Fades in as the sun sets."), opt("DAY", "During the day", "Fades out at night.")), IMG_ON);
    public static final Slider IMAGE_ROTATION = when(slider("image_rotation", "Rotation", 0, 0, 359, 1, Unit.DEGREES, "Turns the picture around you."), IMG_ON);
    public static final Slider IMAGE_SPIN = when(slider("image_spin", "Spin", 0, -1, 1, 0.01, Unit.SIGNED_PERCENT, "Slowly turns the picture over time."), IMG_ON);
    public static final Toggle IMAGE_FOLLOW_SUN = when(toggle("image_follow_sun", "Turn with the sun", false, "Rotate the picture with the sun, like vanilla stars."), IMG_ON);
    public static final Slider IMAGE_HORIZON_FADE = adv(when(slider("image_horizon_fade", "Horizon fade", 0, 0, 1, 0.01, Unit.PERCENT,
            "Fade the picture out toward the horizon so it blends with the gradient."), IMG_ON));
    public static final Color IMAGE_TINT = adv(when(color("image_tint", "Tint", 0xFFFFFF, "Multiplies the picture's colors."), IMG_ON));

    // ================================================================== color grade
    static {
        page(Category.GRADE, "Light");
    }

    public static final Slider EXPOSURE = slider("exposure", "Exposure", 0, -2, 2, 0.1, Unit.EV, "Overall brightness of the sky.");
    public static final Slider CONTRAST = slider("contrast", "Contrast", 0, -0.5, 0.5, 0.01, Unit.SIGNED_PERCENT, "Difference between light and dark.");
    public static final Slider HIGHLIGHTS = adv(slider("highlights", "Highlights", 0, -1, 1, 0.01, Unit.SIGNED_PERCENT, "Brighten or darken the bright parts."));
    public static final Slider SHADOWS = adv(slider("shadows", "Shadows", 0, -1, 1, 0.01, Unit.SIGNED_PERCENT, "Brighten or darken the dark parts."));
    public static final Slider GAMMA = adv(slider("gamma", "Gamma", 1, 0.5, 2, 0.01, Unit.DECIMAL, "Midtone brightness curve."));

    static {
        section("Color");
    }

    public static final Slider SATURATION = slider("saturation", "Saturation", 0, -1, 1, 0.01, Unit.SIGNED_PERCENT, "Color strength.");
    public static final Slider VIBRANCE = slider("vibrance", "Vibrance", 0, -1, 1, 0.01, Unit.SIGNED_PERCENT, "Boosts dull colors more than strong ones.");
    public static final Slider TEMPERATURE = slider("temperature", "Temperature", 0, -1, 1, 0.01, Unit.SIGNED_PERCENT, "Cool blue (left) to warm orange (right).");
    public static final Slider TINT = slider("tint", "Tint", 0, -1, 1, 0.01, Unit.SIGNED_PERCENT, "Green (left) to magenta (right).");
    public static final Slider HUE_SHIFT = slider("hue_shift", "Hue shift", 0, -180, 180, 1, Unit.DEGREES, "Rotates every color around the color wheel.");

    static {
        section("Apply to");
    }

    public static final Toggle GRADE_SPACE = adv(toggle("grade_space", "Stars & space", true, "Grade stars, Milky Way and nebulae too."));
    public static final Toggle GRADE_BODIES = adv(toggle("grade_bodies", "Sun & moon", true, "Grade the sun and moon too."));
    public static final Toggle GRADE_IMAGES = adv(toggle("grade_images", "Sky images", true, "Grade sky images too."));

    // ================================================================== studio
    static {
        page(Category.STUDIO, "Appearance");
    }

    public static final Choice ACCENT = global(choice("accent", "Accent color", "AMETHYST", "Highlight color of the editor.",
            opt("AMETHYST", "Amethyst", ""), opt("OCEAN", "Ocean", ""), opt("SUNSET", "Sunset", ""),
            opt("MINT", "Mint", ""), opt("ROSE", "Rose", ""), opt("GOLD", "Gold", ""), opt("FROST", "Frost", "")));
    public static final Slider UI_OPACITY = global(slider("ui_opacity", "Panel opacity", 0.86, 0.5, 1, 0.01, Unit.PERCENT,
            "Lower it to see more of the live sky behind the editor."));
    public static final Toggle ANIMATIONS = global(toggle("animations", "Animations", true, "Smooth transitions in the editor."));
    public static final Toggle UI_SOUNDS = global(toggle("ui_sounds", "Click sounds", true, "Play a soft click when using controls."));
    public static final Toggle ADVANCED = global(toggle("advanced", "Advanced settings", false,
            "Show every fine-tuning option. Off keeps pages short and simple."));
    public static final Toggle HELP_BAR = global(toggle("help_bar", "Help bar", true, "Describe the control under your mouse at the bottom of the editor."));

    static {
        section("Performance");
    }

    public static final Choice QUALITY = global(choice("quality", "Sky quality", "HIGH", "Resolution of stars, space and images.",
            opt("LOW", "Low", "1024 px space texture, fewer effects. Fastest."),
            opt("BALANCED", "Balanced", "2048 px space texture."),
            opt("HIGH", "High", "2048 px space texture with extra star detail."),
            opt("ULTRA", "Ultra", "4096 px space texture. Sharpest stars; takes a few seconds to generate.")));
    public static final Toggle EFFECTS = global(toggle("effects", "Animated effects", true,
            "Meteors, comets, twinkling and aurora motion. Turn off for the lightest sky."));

    // ================================================================== builders

    private static void page(Category c, String section) {
        cat = c;
        sec = section;
    }

    private static void section(String s) {
        sec = s;
    }

    private static <S extends Setting<?>> S add(S s) {
        if (BY_ID.put(s.id, s) != null) {
            throw new IllegalStateException("Duplicate setting id " + s.id);
        }
        ALL.add(s);
        return s;
    }

    private static Toggle toggle(String id, String label, boolean def, String help) {
        return add(new Toggle(ALL.size(), id, label, help, cat, sec, def));
    }

    private static Slider slider(String id, String label, double def, double min, double max, double step, Unit unit, String help) {
        return add(new Slider(ALL.size(), id, label, help, cat, sec, def, min, max, step, unit));
    }

    private static Color color(String id, String label, int def, String help) {
        return add(new Color(ALL.size(), id, label, help, cat, sec, def));
    }

    private static Grad grad(String id, String label, Gradient def, String help) {
        return add(new Grad(ALL.size(), id, label, help, cat, sec, def));
    }

    private static Text text(String id, String label, String def, String help) {
        return add(new Text(ALL.size(), id, label, help, cat, sec, def));
    }

    private static Option opt(String id, String label, String help) {
        return new Option(id, label, help);
    }

    private static Choice choice(String id, String label, String def, String help, Option... options) {
        return add(new Choice(ALL.size(), id, label, help, cat, sec, List.of(options), def));
    }

    private static <S extends Setting<?>> S adv(S s) {
        s.advanced = true;
        return s;
    }

    private static <S extends Setting<?>> S global(S s) {
        s.global = true;
        return s;
    }

    private static <S extends Setting<?>> S when(S s, Predicate<Profile> p) {
        s.activeWhen = p;
        return s;
    }

    private static boolean SHOWERS_ON(Profile p) {
        return SHOWERS.on(p);
    }

    // ================================================================== access

    public static List<Setting<?>> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static int count() {
        return ALL.size();
    }

    public static Setting<?> byId(String id) {
        return BY_ID.get(id);
    }

    public static List<Setting<?>> in(Category c) {
        List<Setting<?>> out = new ArrayList<>();
        for (Setting<?> s : ALL) {
            if (s.category == c) {
                out.add(s);
            }
        }
        return out;
    }
}
