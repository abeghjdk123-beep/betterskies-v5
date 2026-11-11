package dev.betterskies.render;

import dev.betterskies.BetterSkies;
import dev.betterskies.config.Grade;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import dev.betterskies.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Objects;

/**
 * Draws the BetterSkies sky from the sky-disc hook, on the render thread.
 * Heavy pixel work runs on the background worker through {@link Async}.
 */
public final class SkyRenderer {
    private SkyRenderer() {
    }

    private static final double R = 60;
    private static final int STRIP = 512;

    private static Gpu.Mesh sphereAlpha;
    private static Gpu.Mesh sphereAdd;
    private static Gpu.Tex white;
    private static Gpu.Tex dot;
    private static Gpu.Tex glow;
    private static Gpu.Tex ring;

    private static Gpu.Tex gradientTex;
    private static final int[] strip = new int[STRIP];
    private static long stripRevision = -1;
    private static long stripAt;

    private static final Async<KeyedInput, SpaceGen.Result> space = new Async<>("Star field");
    private static Gpu.Tex spaceTex;
    private static SpaceGen.Result spaceShown;

    private record ImageKey(String name, int maxWidth, double fade, int tint, int grade, long folderStamp) {
    }

    private static final Async<ImageKey, ImageLibrary.Picture> image = new Async<>("Sky image");
    private static Gpu.Tex imageTex;

    private record SunKey(String style, int color, double glow, int glowColor, double corona) {
    }

    private record MoonKey(String style, int phase, int color, double earthshine) {
    }

    private static final Async<SunKey, int[]> sun = new Async<>("Sun");
    private static final Async<MoonKey, int[]> moon = new Async<>("Moon");
    private static Gpu.Tex sunTex;
    private static Gpu.Tex moonTex;

    private static final Async<CloudGen.Input, CloudGen.Result> clouds = new Async<>("Clouds");
    private static Gpu.Tex cloudTex;
    private static double cloudWind;
    private static long cloudLastNanos;

    private static final Geometry fx = new Geometry();
    private static final Geometry fx2 = new Geometry();

    private static volatile int horizonColor = 0x9FC6F0;
    private static volatile String status = "";
    private static volatile long lastFrameNanos;
    private static long imageFolderStamp;

    /** Graded horizon color of the sky right now (for fog and clouds). */
    public static int horizonColor() {
        return horizonColor;
    }

    /** Short status for the editor ("Generating stars...", errors). */
    public static String status() {
        return status;
    }

    public static double lastFrameMillis() {
        return lastFrameNanos / 1e6;
    }

    /** Re-read the images folder on the next frame (after importing pictures). */
    public static void imagesChanged() {
        imageFolderStamp++;
    }

    /** Draws the sky. Returns false when BetterSkies is off or can't draw (vanilla then draws). */
    public static boolean render() {
        Profile p = BetterSkies.profile();
        if (!Settings.ENABLED.on(p)) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return false;
        }
        long start = System.nanoTime();
        long dayTime = BetterSkies.previewTime() >= 0
                ? Math.floorDiv(level.getDayTime(), 24000L) * 24000L + BetterSkies.previewTime()
                : level.getDayTime();
        SkyState s = new SkyState(p, dayTime, level.getRainLevel(1f), level.getThunderLevel(1f));
        Grade grade = new Grade(p);
        boolean low = Settings.QUALITY.is(p, "LOW");
        boolean effects = Settings.EFFECTS.on(p);
        double time = System.nanoTime() / 1e9;

        ensureBasics();

        Matrix4f camera = Gpu.camera();
        double tilt = Math.toRadians(Settings.ORBIT_TILT.v(p));
        double angle = s.angle * SkyMath.TAU;
        Matrix4f celestial = new Matrix4f(camera).rotateX((float) tilt).rotateZ((float) angle);
        double drift = Settings.SKY_DRIFT.v(p) * time * 0.004;
        Matrix4f spaceM = new Matrix4f(camera).rotateX((float) tilt).rotateZ((float) (angle + drift % SkyMath.TAU));

        // ---- 1. gradient
        updateGradient(p, s, grade);
        horizonColor = SkyColors.horizon(p, s, grade);
        Gpu.draw(sphereAlpha, camera, new Vector4f(1, 1, 1, 1), gradientTex.view(), Gpu.sampler(true));

        // ---- 2. stars and space
        double spaceVis = s.spaceVisibility(p);
        SpaceGen.Input in = SpaceGen.Input.of(p);
        SpaceGen.Result res = space.get(new KeyedInput(in), () -> SpaceGen.generate(in));
        if (space.takeFresh() && res != null) {
            if (spaceTex != null && (spaceTex.width != res.width() || spaceTex.height != res.height())) {
                spaceTex.close();
                spaceTex = null;
            }
            if (spaceTex == null) {
                spaceTex = Gpu.texture("space", res.argb(), res.width(), res.height());
            } else {
                spaceTex.update(res.argb());
            }
            spaceShown = res;
        }
        if (spaceTex != null && spaceVis > 0.003) {
            float v = (float) spaceVis;
            Gpu.draw(sphereAdd, spaceM, new Vector4f(v, v, v, 1), spaceTex.view(), Gpu.sampler(true));
        }

        // ---- 3. sky image
        drawImage(p, s, grade, camera, celestial, time);

        // ---- 4. sunset glow
        if (Settings.GLOW_MODE.is(p, "BETTERSKIES")) {
            fx.clear();
            Effects.sunsetGlow(fx, p, s, grade, R * 0.97);
            Gpu.drawOnce(fx, Gpu.alpha(), camera, new Vector4f(1, 1, 1, 1), white.view(), Gpu.sampler(false), "glow");
        }

        // ---- 5. aurora
        if (Settings.AURORA.on(p)) {
            double vis = (Settings.AURORA_NIGHT_ONLY.on(p) ? s.darkness : 1) * (Settings.HIDE_IN_RAIN.on(p) ? 1 - s.rain : 1);
            if (vis > 0.003) {
                fx.clear();
                Effects.aurora(fx, p, s, grade, time, vis, R * 0.93, low);
                Gpu.drawOnce(fx, Gpu.alpha(), camera, new Vector4f(1, 1, 1, 1), white.view(), Gpu.sampler(false), "aurora");
            }
        }

        // ---- 6. bright twinkling stars
        if (spaceShown != null && !spaceShown.bright().isEmpty() && spaceVis > 0.003) {
            fx.clear();
            Effects.twinkle(fx, p, spaceShown.bright(), effects ? time : 0, spaceVis, R * 0.95);
            Gpu.drawOnce(fx, Gpu.alpha(), spaceM, new Vector4f(1, 1, 1, 1), dot.view(), Gpu.sampler(false), "stars");
        }

        // ---- 7. sun and moon
        double bodyVis = 1 - s.rain;
        if (bodyVis > 0.003) {
            drawSun(p, grade, celestial, bodyVis);
            drawMoon(p, s, grade, celestial, bodyVis);
        }

        // ---- 8. meteors and comets
        if (effects) {
            double vis = s.darkness * (Settings.HIDE_IN_RAIN.on(p) ? 1 - s.rain : 1);
            if (vis > 0.02) {
                fx.clear();
                fx2.clear();
                if (Settings.METEORS.on(p) || Settings.SHOWERS.on(p)) {
                    Effects.meteors(fx2, fx, p, s, grade, time, vis, R * 0.9);
                }
                if (Settings.COMETS.on(p)) {
                    Effects.comets(fx2, fx, p, s, grade, time, vis, R * 0.9);
                }
                Gpu.drawOnce(fx, Gpu.alpha(), camera, new Vector4f(1, 1, 1, 1), white.view(), Gpu.sampler(false), "trails");
                Gpu.drawOnce(fx2, Gpu.alpha(), camera, new Vector4f(1, 1, 1, 1), dot.view(), Gpu.sampler(false), "heads");
            }
        }

        // ---- 9. realistic clouds (in front of everything else in the sky)
        if (Settings.SKY_CLOUDS.on(p)) {
            drawClouds(p, s, grade, camera, low, effects);
        }

        status = clouds.busy() ? "Shaping clouds…" : space.busy() ? "Generating stars…" : image.busy() ? "Loading sky image…"
                : image.error() != null ? "Image problem: " + image.error() : "";
        lastFrameNanos = System.nanoTime() - start;
        return true;
    }

    private static void drawClouds(Profile p, SkyState s, Grade grade, Matrix4f camera, boolean low, boolean effects) {
        CloudGen.Input in = CloudGen.Input.of(p);
        CloudGen.Result res = clouds.get(in, () -> CloudGen.generate(in));
        if (clouds.takeFresh() && res != null) {
            if (cloudTex != null && cloudTex.width != res.size()) {
                cloudTex.close();
                cloudTex = null;
            }
            if (cloudTex == null) {
                cloudTex = Gpu.texture("clouds", res.argb(), res.size(), res.size());
            } else {
                cloudTex.update(res.argb());
            }
        }
        if (cloudTex == null) {
            return;
        }
        double h = 4 + 8 * Settings.SC_HEIGHT.v(p);
        double tile = h * 9;
        // wind: accumulate so changing the speed never makes clouds jump
        long now = System.nanoTime();
        double dt = cloudLastNanos == 0 ? 0 : Math.min(0.5, (now - cloudLastNanos) / 1e9);
        cloudLastNanos = now;
        if (effects) {
            cloudWind = (cloudWind + dt * Settings.SC_WIND.v(p) * h * 0.06) % tile;
        }
        double offA = cloudWind, offB = 0;
        Minecraft mc = Minecraft.getInstance();
        if (Settings.SC_PARALLAX.on(p) && mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null) {
            var cam = mc.gameRenderer.getMainCamera().position();
            double k = h / 700.0;
            double az = Math.toRadians(Settings.SC_WIND_DIR.v(p));
            double dx = Math.sin(az), dz = -Math.cos(az);
            double a = cam.x * dx + cam.z * dz, b = -cam.x * dz + cam.z * dx;
            offA -= (a * k) % tile;
            offB -= (b * k) % tile;
        }
        fx.clear();
        Effects.clouds(fx, p, s, grade, offA, offB, low);
        Gpu.drawOnce(fx, Gpu.alpha(), camera, new Vector4f(1, 1, 1, 1), cloudTex.view(), Gpu.tiled(), "clouds");
    }

    /** Space input compared through {@link SpaceGen.Input#same}. */
    private record KeyedInput(SpaceGen.Input in) {
        @Override
        public boolean equals(Object o) {
            return o instanceof KeyedInput k && in.same(k.in);
        }

        @Override
        public int hashCode() {
            return in.width();
        }
    }

    // ================================================================== pieces

    private static void ensureBasics() {
        if (sphereAlpha == null) {
            Geometry sphere = Geometry.sphere(R, 64, 32);
            sphereAlpha = Gpu.upload(sphere, Gpu.alpha(), "sky sphere");
            sphereAdd = Gpu.upload(sphere, Gpu.additive(), "space sphere");
        }
        if (white == null) {
            white = Gpu.texture("white", new int[]{-1, -1, -1, -1}, 2, 2);
            dot = Gpu.texture("dot", Bodies.dot(64), 64, 64);
            glow = Gpu.texture("glow", Bodies.glow(128, 4.0), 128, 128);
            ring = Gpu.texture("halo", Bodies.ring(256), 256, 256);
        }
        if (gradientTex == null) {
            gradientTex = Gpu.texture("gradient", new int[2 * STRIP], 2, STRIP);
        }
    }

    private static void updateGradient(Profile p, SkyState s, Grade grade) {
        long now = System.currentTimeMillis();
        long rev = BetterSkies.revision();
        if (rev == stripRevision && now - stripAt < 120) {
            return;
        }
        stripRevision = rev;
        stripAt = now;
        SkyColors.strip(p, s, grade, strip);
        int[] px = new int[2 * STRIP];
        for (int y = 0; y < STRIP; y++) {
            px[y * 2] = strip[y];
            px[y * 2 + 1] = strip[y];
        }
        gradientTex.update(px);
    }

    private static void drawImage(Profile p, SkyState s, Grade grade, Matrix4f camera, Matrix4f celestial, double time) {
        String name = Settings.IMAGE.get(p);
        if (name.isEmpty()) {
            if (imageTex != null) {
                imageTex.close();
                imageTex = null;
                image.clear();
            }
            return;
        }
        int maxW = Settings.QUALITY.is(p, "LOW") ? 2048 : 4096;
        boolean gr = Settings.GRADE_IMAGES.on(p);
        double fade = Settings.IMAGE_HORIZON_FADE.v(p);
        int tint = Settings.IMAGE_TINT.rgb(p);
        Grade g = gr ? grade : Grade.NONE;
        ImageKey key = new ImageKey(name, maxW, fade, tint, gr ? Objects.hash(grade.apply(0x808080), grade.apply(0xFF4020),
                grade.apply(0x2040FF), grade.apply(0x202020), grade.apply(0xE0E0E0)) : 0, imageFolderStamp);
        ImageLibrary lib = BetterSkies.images();
        ImageLibrary.Picture pic = image.get(key, () -> {
            ImageLibrary.Entry e = lib.find(name);
            if (e == null) {
                throw new IllegalStateException("\"" + name + "\" is no longer in the images folder");
            }
            try {
                return process(lib.load(e, maxW), fade, tint, g);
            } catch (java.io.IOException ex) {
                throw new IllegalStateException(ex.getMessage(), ex);
            }
        });
        if (image.takeFresh()) {
            if (imageTex != null) {
                imageTex.close();
                imageTex = null;
            }
            if (pic != null) {
                imageTex = Gpu.texture("image", pic.argb(), pic.width(), pic.height());
            }
        }
        if (imageTex == null) {
            return;
        }
        double vis = switch (Settings.IMAGE_SHOW.get(p)) {
            case "NIGHT" -> s.darkness;
            case "DAY" -> 1 - s.darkness;
            default -> 1;
        } * Settings.IMAGE_OPACITY.v(p);
        if (vis < 0.003) {
            return;
        }
        Matrix4f m = new Matrix4f(Settings.IMAGE_FOLLOW_SUN.on(p) ? celestial : camera);
        double spin = Settings.IMAGE_SPIN.v(p) * time * 0.02;
        m.rotateY((float) (-Math.toRadians(Settings.IMAGE_ROTATION.v(p)) - spin % SkyMath.TAU));
        float f = (float) vis;
        if (Settings.IMAGE_BLEND.is(p, "ADD")) {
            Gpu.draw(sphereAdd, m, new Vector4f(f, f, f, 1), imageTex.view(), Gpu.sampler(true));
        } else {
            Gpu.draw(sphereAlpha, m, new Vector4f(1, 1, 1, f), imageTex.view(), Gpu.sampler(true));
        }
    }

    private static ImageLibrary.Picture process(ImageLibrary.Picture pic, double fade, int tint, Grade grade) {
        int w = pic.width(), h = pic.height();
        int[] out = new int[w * h];
        boolean tinted = (tint & 0xFFFFFF) != 0xFFFFFF;
        for (int y = 0; y < h; y++) {
            double el = (0.5 - (y + 0.5) / h) * 180;
            double a = fade <= 0 ? 1 : SkyMath.smooth(-2, 4 + fade * 40, el);
            for (int x = 0; x < w; x++) {
                int c = pic.argb()[y * w + x];
                int rgb = c & 0xFFFFFF;
                if (tinted) {
                    rgb = Colors.rgb(Colors.r(rgb) * Colors.r(tint) / 255, Colors.g(rgb) * Colors.g(tint) / 255, Colors.b(rgb) * Colors.b(tint) / 255);
                }
                if (!grade.neutral()) {
                    rgb = grade.apply(rgb);
                }
                int alpha = (int) Math.round(((c >>> 24) & 255) * a);
                out[y * w + x] = (alpha << 24) | rgb;
            }
        }
        return new ImageLibrary.Picture(w, h, out);
    }

    private static void drawSun(Profile p, Grade grade, Matrix4f celestial, double vis) {
        String style = Settings.SUN_STYLE.get(p);
        if ("VANILLA".equals(style) || "HIDDEN".equals(style)) {
            return;
        }
        boolean gr = Settings.GRADE_BODIES.on(p);
        SunKey key = new SunKey(style, gr ? grade.apply(Settings.SUN_COLOR.rgb(p)) : Settings.SUN_COLOR.rgb(p),
                Settings.SUN_GLOW.v(p), gr ? grade.apply(Settings.SUN_GLOW_COLOR.rgb(p)) : Settings.SUN_GLOW_COLOR.rgb(p),
                Settings.SUN_CORONA.v(p));
        int[] px = sun.get(key, () -> Bodies.sun(key.style(), key.color(), key.glow(), key.glowColor(), key.corona()));
        if (sun.takeFresh() && px != null) {
            if (sunTex == null) {
                sunTex = Gpu.texture("sun", px, Bodies.SIZE, Bodies.SIZE);
            } else {
                sunTex.update(px);
            }
        }
        if (sunTex == null) {
            return;
        }
        fx.clear();
        double half = 0.065 * Settings.SUN_SIZE.v(p) / Bodies.SUN_DISC;
        fx.billboard(new double[]{0, 1, 0}, R * 0.9, half, 0, -1);
        float f = (float) vis;
        Gpu.drawOnce(fx, Gpu.additive(), celestial, new Vector4f(f, f, f, 1), sunTex.view(), Gpu.sampler(false), "sun");
    }

    private static void drawMoon(Profile p, SkyState s, Grade grade, Matrix4f celestial, double vis) {
        String style = Settings.MOON_STYLE.get(p);
        if ("VANILLA".equals(style) || "HIDDEN".equals(style)) {
            return;
        }
        boolean gr = Settings.GRADE_BODIES.on(p);
        int color = gr ? grade.apply(Settings.MOON_COLOR.rgb(p)) : Settings.MOON_COLOR.rgb(p);
        MoonKey key = new MoonKey(style, s.moonPhase, color, Settings.EARTHSHINE.v(p));
        int[] px = moon.get(key, () -> Bodies.moon(key.style(), key.phase(), key.color(), key.earthshine()));
        if (moon.takeFresh() && px != null) {
            if (moonTex == null) {
                moonTex = Gpu.texture("moon", px, Bodies.SIZE, Bodies.SIZE);
            } else {
                moonTex.update(px);
            }
        }
        double size = Settings.MOON_SIZE.v(p);
        double[] dir = {0, -1, 0};
        // glow (scaled by how much of the moon is lit)
        double lit = 0.5 + 0.5 * Math.cos(s.moonPhase * Math.PI / 4);
        double g = Settings.MOON_GLOW.v(p) * (0.25 + 0.75 * lit) * vis;
        if (g > 0.003) {
            fx.clear();
            fx.billboard(dir, R * 0.91, 0.05 * size * 5.5, 0, -1);
            float a = (float) (g * 0.55);
            Gpu.drawOnce(fx, Gpu.additive(), celestial,
                    new Vector4f(Colors.r(color) / 255f * a, Colors.g(color) / 255f * a, Colors.b(color) / 255f * a, 1),
                    glow.view(), Gpu.sampler(false), "moon glow");
        }
        if (Settings.MOON_HALO.on(p)) {
            fx.clear();
            fx.billboard(dir, R * 0.91, 0.05 * size * 9, 0, -1);
            float a = (float) (0.35 * vis * (0.3 + 0.7 * lit));
            Gpu.drawOnce(fx, Gpu.additive(), celestial, new Vector4f(a, a, a, 1), ring.view(), Gpu.sampler(false), "moon halo");
        }
        if (moonTex != null) {
            fx.clear();
            fx.billboard(dir, R * 0.9, 0.05 * size / Bodies.MOON_DISC, 0, -1);
            Gpu.drawOnce(fx, Gpu.alpha(), celestial, new Vector4f(1, 1, 1, (float) vis), moonTex.view(), Gpu.sampler(false), "moon");
        }
    }

    /** Frees GPU resources (e.g. when leaving a world isn't needed; kept for resource reloads). */
    public static void release() {
        List<AutoCloseable> all = new java.util.ArrayList<>();
        java.util.Collections.addAll(all, sphereAlpha, sphereAdd, white, dot, glow, ring, gradientTex, spaceTex, imageTex, sunTex, moonTex, cloudTex);
        for (AutoCloseable c : all) {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignored) {
                    // already gone
                }
            }
        }
        sphereAlpha = sphereAdd = null;
        white = dot = glow = ring = gradientTex = spaceTex = imageTex = sunTex = moonTex = cloudTex = null;
        clouds.clear();
        spaceShown = null;
        space.clear();
        image.clear();
        sun.clear();
        moon.clear();
        stripRevision = -1;
    }
}
