package dev.betterskies.config;

import dev.betterskies.BetterSkies;
import dev.betterskies.util.Colors;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Brings skies over from BetterSkies 3.x (smoothsky-*.properties in the config
 * folder). Runs once: the current sky becomes the active sky and old presets
 * become saved skies named "… (v3)".
 */
public final class Migration {
    private Migration() {
    }

    public static Profile run(Path configDir, Store store) {
        Path marker = store.root().resolve(".migrated-v3");
        if (Files.exists(marker)) {
            return null;
        }
        Profile current = null;
        try {
            List<Profile> old = read(configDir.resolve("smoothsky-current.properties"));
            if (!old.isEmpty()) {
                current = old.get(0);
                current.name = "My v3 sky";
            }
            int imported = 0;
            for (Profile p : read(configDir.resolve("smoothsky-presets.properties"))) {
                store.saveSky(p.name + " (v3)", p);
                imported++;
            }
            if (current != null || imported > 0) {
                BetterSkies.LOG.info("[BetterSkies] Imported {} sky preset(s){} from BetterSkies 3.",
                        imported, current != null ? " and your current sky" : "");
            }
            Files.writeString(marker, "BetterSkies 3 settings were imported (delete this file to import again).\n");
        } catch (Exception e) {
            BetterSkies.LOG.warn("[BetterSkies] Could not import BetterSkies 3 settings: {}", e.toString());
        }
        return current;
    }

    private static List<Profile> read(Path file) throws IOException {
        List<Profile> out = new ArrayList<>();
        if (!Files.isRegularFile(file)) {
            return out;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }
        int count = parseInt(props.getProperty("count"), 0);
        for (int i = 0; i < count; i++) {
            Profile p = convert(props, "preset." + i + ".");
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    private static Profile convert(Properties props, String pre) {
        int stops = parseInt(props.getProperty(pre + "stops"), 0);
        List<Gradient.Stop> list = new ArrayList<>();
        for (int j = 0; j < stops; j++) {
            String v = props.getProperty(pre + "stop." + j, "");
            int colon = v.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            int c = Colors.parse(v.substring(colon + 1));
            if (c >= 0) {
                list.add(new Gradient.Stop(parseDouble(v.substring(0, colon), 0), c));
            }
        }
        if (list.size() < 2) {
            return null;
        }
        Profile p = new Profile();
        p.name = Store.cleanName(props.getProperty(pre + "name", "Imported"));
        Settings.GRAD_DAY.set(p, new Gradient(list));
        boolean cycle = bool(props, pre + "dayNightEnabled", false);
        Settings.DAY_CYCLE.set(p, cycle);
        if (cycle) {
            int sunrise = color(props, pre + "sunriseColor", 0xFF9E9C);
            int day = color(props, pre + "dayColor", 0x75BFFF);
            int sunset = color(props, pre + "sunsetColor", 0xFF70B4);
            int night = color(props, pre + "nightColor", 0x200E48);
            Settings.GRAD_DAY.set(p, Gradient.of(Colors.mix(day, 0xFFFFFF, 0.35), day, Colors.scale(day, 0.6)));
            Settings.GRAD_DUSK.set(p, Gradient.of(sunset, Colors.mix(sunset, night, 0.5), night));
            Settings.SEPARATE_DAWN.set(p, true);
            Settings.GRAD_DAWN.set(p, Gradient.of(sunrise, Colors.mix(sunrise, day, 0.5), Colors.scale(day, 0.7)));
            Settings.GRAD_NIGHT.set(p, Gradient.of(Colors.mix(night, 0xFFFFFF, 0.1), night, Colors.scale(night, 0.4)));
        }
        Settings.CLOUDS.set(p, bool(props, pre + "cloudsVisible", true));
        Settings.CLOUD_OPACITY.set(p, parseDouble(props.getProperty(pre + "cloudOpacity"), 1));
        Settings.FOG_TINT.set(p, !bool(props, pre + "fogEnabled", true) ? "OFF"
                : bool(props, pre + "matchHorizonFog", true) ? "HORIZON" : "CUSTOM");
        Settings.FOG_COLOR.set(p, color(props, pre + "fogColor", 0xFF78C8));
        Settings.FOG_STRENGTH.set(p, parseDouble(props.getProperty(pre + "fogStrength"), 0.6));
        Settings.STARS.set(p, bool(props, pre + "starsVisible", true));
        Settings.STAR_DENSITY.set(p, parseDouble(props.getProperty(pre + "starDensity"), 0.5));
        Settings.STAR_COLOR.set(p, color(props, pre + "starColor", 0xFFFFFF));
        Settings.SUN_STYLE.set(p, bool(props, pre + "sunVisible", true) ? "SOFT" : "HIDDEN");
        Settings.MOON_STYLE.set(p, bool(props, pre + "moonVisible", true) ? "REALISTIC" : "HIDDEN");
        Settings.SUN_SIZE.set(p, parseDouble(props.getProperty(pre + "sunSize"), 1));
        Settings.MOON_SIZE.set(p, parseDouble(props.getProperty(pre + "moonSize"), 1));
        Settings.AURORA.set(p, bool(props, pre + "aurora", false));
        Settings.AURORA_AUTO.set(p, false);
        Settings.AURORA_COLOR.set(p, color(props, pre + "auroraColor", 0x52FFB3));
        Settings.AURORA_COLOR2.set(p, color(props, pre + "auroraSecondaryColor", 0x9C58FF));
        Settings.METEORS.set(p, bool(props, pre + "shootingStars", false));
        Settings.NEBULA.set(p, bool(props, pre + "nebula", false));
        Settings.NEB_COLOR.set(p, color(props, pre + "nebulaColor", 0x7664FF));
        Settings.NEB_COLOR2.set(p, color(props, pre + "nebulaSecondaryColor", 0xEC6CBF));
        Settings.MILKY_WAY.set(p, bool(props, pre + "milkyWay", false));
        Settings.COMETS.set(p, bool(props, pre + "comets", false));
        Settings.DUST.set(p, bool(props, pre + "cosmicDust", false));
        return p;
    }

    private static boolean bool(Properties props, String key, boolean def) {
        String v = props.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    private static int color(Properties props, String key, int def) {
        return Colors.parseOr(props.getProperty(key), def);
    }

    private static int parseInt(String s, int def) {
        try {
            return s == null ? def : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static double parseDouble(String s, double def) {
        try {
            return s == null ? def : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
