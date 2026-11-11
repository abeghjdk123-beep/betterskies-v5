package dev.betterskies.gametest;

import dev.betterskies.BetterSkies;
import dev.betterskies.config.Category;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import dev.betterskies.screen.StudioScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

/**
 * CI smoke test: boots the game, makes a world, photographs the sky at several
 * times of day and every Studio page. Screenshots are checked by a human.
 */
public final class StudioGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        ctx.getInput().resizeWindow(1920, 1080);
        ctx.waitTicks(5);

        // the Studio from the title screen (no world: painted backdrop)
        ctx.setScreen(() -> new StudioScreen(null));
        ctx.waitTicks(20);
        shot(ctx, "00-title-studio");
        ctx.setScreen(() -> null);

        // software rendering in CI is slow: load the world small and close, then go big for screenshots
        ctx.runOnClient(mc -> {
            mc.options.renderDistance().set(2);
            mc.options.simulationDistance().set(5);
        });
        ctx.getInput().resizeWindow(854, 480);
        ctx.waitTicks(5);
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getClientWorld().waitForChunksRender();
            ctx.getInput().resizeWindow(1920, 1080);
            ctx.waitTicks(10);
            sp.getServer().runCommand("weather clear");
            ctx.runOnClient(mc -> {
                Profile p = BetterSkies.profile();
                Settings.AURORA.set(p, true);
                Settings.NEBULA.set(p, true);
                Settings.COMETS.set(p, true);
                Settings.SHOWERS.set(p, true);
                Settings.SHOWER_FREQUENCY.set(p, 1.0);
                BetterSkies.changed();
            });
            int[] times = {1000, 6000, 12400, 13200, 18000, 22900};
            String[] names = {"morning", "noon", "sunset", "dusk", "midnight", "sunrise"};
            for (int i = 0; i < times.length; i++) {
                sp.getServer().runCommand("time set " + times[i]);
                // yaw: 90 = west (sunset), 270 = east (sunrise), 0 = south
                float yaw = switch (i) {
                    case 2, 3 -> 90f;
                    case 0, 5 -> 270f;
                    default -> 0f;
                };
                look(ctx, i == 1 ? -60f : -12f, yaw);
                ctx.waitTicks(i == 0 ? 120 : 40);
                shot(ctx, String.format("1%d-sky-%s", i, names[i]));
            }
            // straight up at night (stars, Milky Way, moon path)
            sp.getServer().runCommand("time set 18000");
            look(ctx, -89f, 0f);
            ctx.waitTicks(30);
            shot(ctx, "20-sky-zenith-night");
            look(ctx, -18f, 180f);
            ctx.waitTicks(20);
            shot(ctx, "21-sky-north-aurora");

            // each realistic cloud type at midday, then puffy clouds at sunset
            sp.getServer().runCommand("time set 5000");
            for (String type : new String[]{"CUMULUS", "CIRRUS", "MIXED", "STRATUS", "SCATTERED"}) {
                ctx.runOnClient(mc -> {
                    Settings.SC_TYPE.set(BetterSkies.profile(), type);
                    BetterSkies.changed();
                });
                look(ctx, -22f, 0f);
                ctx.waitTicks(60);
                shot(ctx, "25-clouds-" + type.toLowerCase());
            }
            ctx.runOnClient(mc -> {
                Settings.SC_TYPE.set(BetterSkies.profile(), "CUMULUS");
                BetterSkies.changed();
            });
            sp.getServer().runCommand("time set 12300");
            look(ctx, -10f, 90f);
            ctx.waitTicks(60);
            shot(ctx, "26-clouds-sunset");
            look(ctx, -75f, 90f);
            ctx.waitTicks(20);
            shot(ctx, "27-clouds-overhead");

            // real atmosphere: noon, sunset toward the sun, dusk away from it (Earth's shadow, Belt of Venus)
            ctx.runOnClient(mc -> {
                Settings.SKY_MODEL.set(BetterSkies.profile(), "PHYSICAL");
                Settings.SC_COVERAGE.set(BetterSkies.profile(), 0.35);
                BetterSkies.changed();
            });
            Object[][] phys = {{6000, -50f, 0f, "noon"}, {11800, -8f, 90f, "afternoon-sun"}, {12500, -6f, 90f, "sunset"},
                    {13000, -6f, 270f, "dusk-east"}, {13400, -15f, 90f, "twilight"}};
            for (Object[] ph : phys) {
                sp.getServer().runCommand("time set " + ph[0]);
                look(ctx, (Float) ph[1], (Float) ph[2]);
                ctx.waitTicks(70);
                shot(ctx, "6-phys-" + ph[3]);
            }
            // weather presets
            for (String preset : new String[]{"Partly cloudy", "Thunderstorm", "Foggy morning"}) {
                ctx.runOnClient(mc -> {
                    for (var lk : dev.betterskies.config.BuiltinSkies.WEATHER) {
                        if (lk.name().equals(preset)) {
                            lk.apply().accept(BetterSkies.profile());
                        }
                    }
                    BetterSkies.changed();
                });
                if (preset.equals("Thunderstorm")) {
                    sp.getServer().runCommand("weather thunder");
                }
                sp.getServer().runCommand("time set " + (preset.equals("Foggy morning") ? 400 : 10500));
                look(ctx, -12f, 90f);
                ctx.waitTicks(70);
                shot(ctx, "7-weather-" + preset.replace(' ', '_'));
                sp.getServer().runCommand("weather clear");
            }
            // built-in moving panoramas
            for (String img : new String[]{dev.betterskies.render.ImageLibrary.BUILTIN_CLOUDS, dev.betterskies.render.ImageLibrary.BUILTIN_AURORA}) {
                boolean aurora = img.contains("Aurora");
                ctx.runOnClient(mc -> {
                    var p = BetterSkies.profile();
                    p.loadLook(new Profile());
                    Settings.SKY_CLOUDS.set(p, false);
                    Settings.IMAGE.set(p, img);
                    Settings.IMAGE_FLOW.set(p, 0.6);
                    Settings.IMAGE_SHIMMER.set(p, aurora ? 0.6 : 0);
                    Settings.IMAGE_BLEND.set(p, aurora ? "ADD" : "NORMAL");
                    BetterSkies.changed();
                });
                sp.getServer().runCommand("time set " + (aurora ? 18000 : 5000));
                look(ctx, -20f, aurora ? 180f : 0f);
                ctx.waitTicks(120);
                shot(ctx, "8-panorama-" + (aurora ? "aurora" : "clouds"));
            }
            ctx.runOnClient(mc -> {
                BetterSkies.profile().loadLook(new Profile());
                BetterSkies.changed();
            });

            // a few built-in looks
            for (String sky : new String[]{"Synthwave", "Blood Moon", "Boreal Night"}) {
                ctx.runOnClient(mc -> {
                    for (var e : dev.betterskies.config.BuiltinSkies.all()) {
                        if (e.name().equals(sky)) {
                            BetterSkies.profile().loadLook(e.profile());
                            BetterSkies.changed();
                        }
                    }
                });
                look(ctx, -15f, sky.equals("Boreal Night") ? 180f : 0f);
                ctx.waitTicks(80);
                shot(ctx, "3-preset-" + sky.replace(' ', '_'));
            }
            ctx.runOnClient(mc -> {
                BetterSkies.profile().loadLook(new Profile());
                BetterSkies.changed();
            });

            // every Studio page, evening light
            sp.getServer().runCommand("time set 12600");
            look(ctx, -10f, 270f);
            for (Category c : Category.values()) {
                ctx.setScreen(() -> StudioScreen.openAt(c));
                ctx.waitTicks(15);
                shot(ctx, "4" + String.format("%02d", c.ordinal()) + "-page-" + c.name().toLowerCase());
            }
            // advanced mode + search
            ctx.runOnClient(mc -> {
                Settings.ADVANCED.set(BetterSkies.profile(), true);
                BetterSkies.changed();
            });
            ctx.setScreen(() -> StudioScreen.openAt(Category.STARS));
            ctx.waitTicks(15);
            shot(ctx, "50-stars-advanced");
            ctx.setScreen(() -> StudioScreen.openAt(Category.ATMOSPHERE));
            ctx.waitTicks(15);
            shot(ctx, "53-clouds-page");
            ctx.runOnClient(mc -> {
                Settings.SKY_MODEL.set(BetterSkies.profile(), "PHYSICAL");
                BetterSkies.changed();
            });
            ctx.setScreen(() -> StudioScreen.openAt(Category.COLORS));
            ctx.waitTicks(15);
            shot(ctx, "54-colors-physical");
            ctx.setScreen(() -> StudioScreen.openAt(Category.IMAGES));
            ctx.waitTicks(200);
            shot(ctx, "55-images-page");
            ctx.setScreen(() -> StudioScreen.openSearch("color"));
            ctx.waitTicks(15);
            shot(ctx, "51-search");
            ctx.setScreen(() -> StudioScreen.openColorPickerDemo());
            ctx.waitTicks(15);
            shot(ctx, "52-color-picker");
            ctx.setScreen(() -> null);
        }
    }

    private static void look(ClientGameTestContext ctx, float pitch, float yaw) {
        ctx.runOnClient(mc -> {
            if (mc.player != null) {
                mc.player.setXRot(pitch);
                mc.player.setYRot(yaw);
            }
        });
    }

    private static void shot(ClientGameTestContext ctx, String name) {
        System.out.println("[BetterSkies test] screenshot " + ctx.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix()));
    }
}
