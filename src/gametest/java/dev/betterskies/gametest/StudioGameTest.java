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
