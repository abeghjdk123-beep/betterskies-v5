package dev.betterskies.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
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


    private record SunKey(String style, int color, double glow, int glowColor, double corona) {
    }

    private record MoonKey(String style, int phase, int color, double earthshine) {
    }

    private static final Async<SunKey, int[]> sun = new Async<>("Sun");
    private static final Async<MoonKey, int[]> moon = new Async<>("Moon");
    private static Gpu.Tex sunTex;
    private static Gpu.Tex moonTex;


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
        if (level == null || mc.player == null || mc.screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen) {
            // don't start painting textures while the world is still loading
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
        int horizon = SkyColors.horizon(p, s, grade);
        Gpu.draw(sphereAlpha, camera, new Vector4f(1, 1, 1, 1), gradientTex.view(), Gpu.sampler(true));
        double phys = AtmosphereLayer.draw(sphereAlpha, p, s, grade, camera);
        if (phys > 0) {
            float yaw = mc.player != null ? mc.player.getYRot() : 0;
            // Minecraft yaw 0 = south; our compass 0 = north
            int toward = AtmosphereLayer.horizonToward(yaw + 180);
            if (toward >= 0) {
                horizon = Colors.mix(horizon, toward, phys);
            }
        }
        horizonColor = horizon;
        PhysicalSky.Params params = PhysicalSky.Params.of(p);
        int sunTint = Settings.SKY_MODEL.is(p, "PHYSICAL") ? PhysicalSky.sunlight(s.sun, params)
                : Colors.mixOklab(0xFFF4E0, grade.apply(Settings.SC_SUNSET.rgb(p)), 1 - SkyMath.smooth(0.0, 0.45, s.sunHeight));

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
        ImageLayer.draw(sphereAlpha, p, s, grade, camera, celestial, time, imageFolderStamp, R * 0.99);

        // ---- 4. sunset glow
        // the real atmosphere already makes its own sunset glow
        if (Settings.GLOW_MODE.is(p, "BETTERSKIES") && phys < 0.5) {
            fx.clear();
            Effects.sunsetGlow(fx, p, s, grade, R * 0.97);
            Gpu.drawOnce(fx, Gpu.alpha(), camera, new Vector4f(1, 1, 1, 1), white.view(), Gpu.sampler(false), "glow");
        }

        // ---- 5. aurora
        auroraG.clear();
        twinkleG.clear();
        if (Settings.AURORA.on(p)) {
            double vis = (Settings.AURORA_NIGHT_ONLY.on(p) ? s.darkness : 1) * (Settings.HIDE_IN_RAIN.on(p) ? 1 - s.rain : 1);
            if (vis > 0.003) {
                auroraG.clear();
                Effects.aurora(auroraG, p, s, grade, time, vis, R * 0.93, low);
                Gpu.drawOnce(auroraG, Gpu.alpha(), camera, new Vector4f(1, 1, 1, 1), white.view(), Gpu.sampler(false), "aurora");
            }
        }

        // ---- 6. bright twinkling stars
        if (spaceShown != null && !spaceShown.bright().isEmpty() && spaceVis > 0.003) {
            Effects.twinkle(twinkleG, p, spaceShown.bright(), effects ? time : 0, spaceVis, R * 0.95, angle + drift % SkyMath.TAU, tilt);
            Gpu.drawOnce(twinkleG, Gpu.alpha(), spaceM, new Vector4f(1, 1, 1, 1), dot.view(), Gpu.sampler(false), "stars");
        }

        // ---- 7. sun and moon
        double bodyVis = 1 - s.rain;
        if (bodyVis > 0.003) {
            drawSun(p, grade, celestial, bodyVis, null);
            drawMoon(p, s, grade, celestial, bodyVis, null);
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

        // ---- 9. realistic clouds (in front of everything else in the sky), then sun rays through them
        if (Settings.SKY_CLOUDS.on(p)) {
            CloudLayer.draw(p, s, grade, camera, low, effects, sunTint, time);
        }
        if (Settings.SUN_RAYS.on(p) && !Settings.SUN_STYLE.is(p, "HIDDEN")) {
            CloudLayer.drawRays(p, s, camera, white, sunTint, time, R * 0.85);
        }

        // ---- 10. mirror water below the horizon
        if (Settings.REFLECT.on(p)) {
            reflect(p, s, grade, camera, phys, spaceVis, angle + drift % SkyMath.TAU, angle, tilt, bodyVis, low, effects ? time : 0);
        }

        status = AtmosphereLayer.busy() ? "Scattering sunlight…" : CloudLayer.busy() ? "Shaping clouds…"
                : space.busy() ? "Generating stars…" : ImageLayer.busy() ? "Loading sky image…"
                : ImageLayer.error() != null ? "Image problem: " + ImageLayer.error() : "";
        lastFrameNanos = System.nanoTime() - start;
        return true;
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

    private static final Geometry auroraG = new Geometry();
    private static final Geometry twinkleG = new Geometry();
    private static final Geometry mirrorG = new Geometry();

    /** Row-major world rotation of a frame turned by {@code tilt} about X, then {@code spin} about Z (like the celestial matrix). */
    private static double[] rotation(double tilt, double spin) {
        org.joml.Matrix3f m = new org.joml.Matrix3f().rotateX((float) tilt).rotateZ((float) spin);
        double[] r = new double[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                r[row * 3 + col] = m.get(col, row);
            }
        }
        return r;
    }

    private static double[] apply(double[] m, double[] v) {
        return new double[]{m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
                m[6] * v[0] + m[7] * v[1] + m[8] * v[2]};
    }

    /** Transposed (inverse) rotation applied to v. */
    private static double[] applyT(double[] m, double[] v) {
        return new double[]{m[0] * v[0] + m[3] * v[1] + m[6] * v[2], m[1] * v[0] + m[4] * v[1] + m[7] * v[2],
                m[2] * v[0] + m[5] * v[1] + m[8] * v[2]};
    }

    /** Draws the sky reflected in calm water over the whole lower half of the sky. */
    private static void reflect(Profile p, SkyState s, Grade grade, Matrix4f camera, double phys, double spaceVis,
                                double spaceSpin, double sunSpin, double tilt, double bodyVis, boolean low, double time) {
        double ripple = Settings.REFLECT_RIPPLE.v(p);
        Vector4f one = new Vector4f(1, 1, 1, 1);
        // sky colors (opaque: covers whatever was below the horizon)
        mirrorG.clear();
        Reflection.sphere(mirrorG, R * 0.995, SkyMath::uvFromDir, ripple, time, low);
        Gpu.drawOnce(mirrorG, Gpu.alpha(), camera, one, gradientTex.view(), Gpu.sampler(true), "reflection");
        Gpu.Tex atmo = AtmosphereLayer.texture();
        if (phys > 0 && atmo != null) {
            Gpu.drawOnce(mirrorG, Gpu.alpha(), camera, new Vector4f(1, 1, 1, (float) phys), atmo.view(), Gpu.sampler(true), "reflection");
        }
        // stars and space
        double[] spaceRot = rotation(tilt, spaceSpin);
        if (spaceTex != null && spaceVis > 0.003) {
            mirrorG.clear();
            Reflection.sphere(mirrorG, R * 0.99, d -> SkyMath.uvFromDir(applyT(spaceRot, d)), ripple, time, low);
            float v = (float) spaceVis;
            Gpu.drawOnce(mirrorG, Gpu.additive(), camera, new Vector4f(v, v, v, 1), spaceTex.view(), Gpu.sampler(true), "reflection");
        }
        if (!twinkleG.isEmpty()) {
            mirrorG.clear();
            twinkleG.mirrorInto(mirrorG, spaceRot);
            Gpu.drawOnce(mirrorG, Gpu.alpha(), camera, one, dot.view(), Gpu.sampler(false), "reflection");
        }
        if (!auroraG.isEmpty()) {
            mirrorG.clear();
            auroraG.mirrorInto(mirrorG, null);
            Gpu.drawOnce(mirrorG, Gpu.alpha(), camera, one, white.view(), Gpu.sampler(false), "reflection");
        }
        // sun and moon
        if (bodyVis > 0.003) {
            double[] bodyRot = rotation(tilt, sunSpin);
            drawSun(p, grade, camera, bodyVis, bodyRot);
            drawMoon(p, s, grade, camera, bodyVis, bodyRot);
        }
        if (Settings.SKY_CLOUDS.on(p) && Settings.REFLECT_CLOUDS.on(p)) {
            CloudLayer.drawMirrored(camera);
        }
        // the water itself, stronger looking down
        mirrorG.clear();
        Reflection.water(mirrorG, p, s, grade, horizonColor, R * 0.98);
        Gpu.drawOnce(mirrorG, Gpu.alpha(), camera, one, white.view(), Gpu.sampler(false), "water");
    }

    /** Draws {@code g} with {@code matrix}, or (mirror != null) reflected into world space and drawn with the camera. */
    private static void drawBody(Geometry g, RenderPipeline pipeline, Matrix4f matrix, Vector4f tint, Gpu.Tex tex, String label,
                                 double[] mirror) {
        if (mirror != null) {
            mirrorG.clear();
            g.mirrorInto(mirrorG, mirror);
            g = mirrorG;
        }
        Gpu.drawOnce(g, pipeline, matrix, tint, tex.view(), Gpu.sampler(false), label);
    }

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

    private static void drawSun(Profile p, Grade grade, Matrix4f celestial, double vis, double[] mirror) {
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
        drawBody(fx, Gpu.additive(), celestial, new Vector4f(f, f, f, 1), sunTex, "sun", mirror);
    }

    private static void drawMoon(Profile p, SkyState s, Grade grade, Matrix4f celestial, double vis, double[] mirror) {
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
            drawBody(fx, Gpu.additive(), celestial,
                    new Vector4f(Colors.r(color) / 255f * a, Colors.g(color) / 255f * a, Colors.b(color) / 255f * a, 1),
                    glow, "moon glow", mirror);
        }
        if (Settings.MOON_HALO.on(p)) {
            fx.clear();
            fx.billboard(dir, R * 0.91, 0.05 * size * 9, 0, -1);
            float a = (float) (0.35 * vis * (0.3 + 0.7 * lit));
            drawBody(fx, Gpu.additive(), celestial, new Vector4f(a, a, a, 1), ring, "moon halo", mirror);
        }
        if (moonTex != null) {
            fx.clear();
            fx.billboard(dir, R * 0.9, 0.05 * size / Bodies.MOON_DISC, 0, -1);
            drawBody(fx, Gpu.alpha(), celestial, new Vector4f(1, 1, 1, (float) vis), moonTex, "moon", mirror);
        }
    }

    /** Frees GPU resources (e.g. when leaving a world isn't needed; kept for resource reloads). */
    public static void release() {
        List<AutoCloseable> all = new java.util.ArrayList<>();
        java.util.Collections.addAll(all, sphereAlpha, sphereAdd, white, dot, glow, ring, gradientTex, spaceTex, sunTex, moonTex);
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
        white = dot = glow = ring = gradientTex = spaceTex = sunTex = moonTex = null;
        CloudLayer.release();
        AtmosphereLayer.release();
        ImageLayer.forget();
        spaceShown = null;
        space.clear();
        sun.clear();
        moon.clear();
        stripRevision = -1;
    }
}
