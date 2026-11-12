package dev.betterskies.render;

import dev.betterskies.config.Grade;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import dev.betterskies.util.Colors;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Realistic clouds (two passes: clouds, then sunlit silver edges), lightning, and the sun rays that shine through them. */
final class CloudLayer {
    private CloudLayer() {
    }

    private static final Async<CloudGen.Input, CloudGen.Result> job = new Async<>("Clouds");
    private static Gpu.Tex tex;
    private static Gpu.Tex edgeTex;
    private static Gpu.Tex midTex;
    private static Gpu.Tex farTex;
    private static CloudGen.Result shown;
    private static double wind;
    private static long lastNanos;
    // scroll state of the last frame, used to look up clouds in a direction (sun rays)
    private static double offA, offB, height, tile, dirX, dirZ, fadeDeg, minDeg;
    // lightning
    private static double nextFlash = -1;
    private static double flashStart = -100;
    private static double[] flashDir = {0, 1, 0};

    private static final Geometry base = new Geometry();
    private static final Geometry mid = new Geometry();
    private static final Geometry far = new Geometry();
    private static final Geometry mirror = new Geometry();
    private static final Geometry shine = new Geometry();
    private static final Geometry rays = new Geometry();

    static boolean busy() {
        return job.busy();
    }

    static void draw(Profile p, SkyState s, Grade grade, Matrix4f camera, boolean low, boolean effects, int sunTint, double time) {
        CloudGen.Input in = CloudGen.Input.of(p);
        CloudGen.Result res = job.get(in, () -> CloudGen.generate(in));
        if (job.takeFresh() && res != null) {
            if (tex != null && tex.width != res.size()) {
                closeTextures();
            }
            int n = res.size();
            int[] m = down(res.argb(), n, 4);
            int[] f = down(m, n / 4, 4);
            if (tex == null) {
                tex = Gpu.texture("clouds", res.argb(), n, n);
                edgeTex = Gpu.texture("cloud edges", res.edge(), n, n);
                midTex = Gpu.texture("clouds (mid)", m, n / 4, n / 4);
                farTex = Gpu.texture("clouds (far)", f, n / 16, n / 16);
            } else {
                tex.update(res.argb());
                edgeTex.update(res.edge());
                midTex.update(m);
                farTex.update(f);
            }
            shown = res;
        }
        if (tex == null) {
            return;
        }
        double h = Effects.cloudHeight(p);
        double t = h * 9;
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.5, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (effects) {
            wind = (wind + dt * Settings.SC_WIND.v(p) * h * 0.06) % t;
        }
        double az = Math.toRadians(Settings.SC_WIND_DIR.v(p));
        double dx = Math.sin(az), dz = -Math.cos(az);
        double a0 = wind, b0 = 0;
        Minecraft mc = Minecraft.getInstance();
        if (Settings.SC_PARALLAX.on(p) && mc.gameRenderer != null && mc.gameRenderer.getMainCamera() != null) {
            var cam = mc.gameRenderer.getMainCamera().position();
            double k = h / 700.0;
            double a = cam.x * dx + cam.z * dz, b = -cam.x * dz + cam.z * dx;
            a0 -= (a * k) % t;
            b0 -= (b * k) % t;
        }
        offA = a0;
        offB = b0;
        height = h;
        tile = t;
        dirX = dx;
        dirZ = dz;
        fadeDeg = Effects.cloudFadeWidth(p);
        minDeg = Math.toDegrees(Math.atan2(h, Effects.cloudReach(p)));

        Effects.Flash flash = lightning(p, s, effects, time);
        base.clear();
        mid.clear();
        far.clear();
        shine.clear();
        Effects.clouds(base, mid, far, shine, p, s, grade, a0, b0, low, sunTint, flash, SkyRenderer.horizonColor());
        drawAll(camera, false);
    }

    /** Draws this frame's clouds again, mirrored below the horizon (water reflection). */
    static void drawMirrored(Matrix4f camera) {
        if (tex != null && !base.isEmpty()) {
            drawAll(camera, true);
        }
    }

    private static void drawAll(Matrix4f camera, boolean mirrored) {
        Vector4f one = new Vector4f(1, 1, 1, 1);
        Geometry[] parts = {far, mid, base, shine};
        Gpu.Tex[] texes = {farTex, midTex, tex, edgeTex};
        for (int i = 0; i < parts.length; i++) {
            Geometry g = parts[i];
            if (mirrored) {
                mirror.clear();
                parts[i].mirrorInto(mirror, null);
                g = mirror;
            }
            Gpu.drawOnce(g, Gpu.alpha(), camera, one, texes[i].view(), Gpu.tiled(), "clouds");
        }
    }

    /** Box-filters a square ARGB texture by {@code f} (color weighted by alpha): a soft copy for far clouds. */
    static int[] down(int[] src, int n, int f) {
        int m = Math.max(1, n / f);
        int[] out = new int[m * m];
        for (int y = 0; y < m; y++) {
            for (int x = 0; x < m; x++) {
                long a = 0, r = 0, g = 0, b = 0;
                for (int j = 0; j < f; j++) {
                    for (int i = 0; i < f; i++) {
                        int c = src[(y * f + j) * n + x * f + i];
                        int ca = c >>> 24;
                        a += ca;
                        r += (long) ((c >> 16) & 255) * ca;
                        g += (long) ((c >> 8) & 255) * ca;
                        b += (long) (c & 255) * ca;
                    }
                }
                int k = f * f;
                int aa = (int) (a / k);
                out[y * m + x] = a == 0 ? 0x00FFFFFF
                        : (aa << 24) | (int) (r / a) << 16 | (int) (g / a) << 8 | (int) (b / a);
            }
        }
        return out;
    }

    private static void closeTextures() {
        for (Gpu.Tex t : new Gpu.Tex[]{tex, edgeTex, midTex, farTex}) {
            if (t != null) {
                t.close();
            }
        }
        tex = edgeTex = midTex = farTex = null;
    }

    /** Thunderstorms: occasional flickering flashes inside the clouds. */
    private static Effects.Flash lightning(Profile p, SkyState s, boolean effects, double time) {
        if (!effects || !Settings.SC_LIGHTNING.on(p) || s.thunder < 0.15) {
            nextFlash = -1;
            return Effects.Flash.NONE;
        }
        if (nextFlash < 0) {
            nextFlash = time + 2 + Math.random() * 6;
        }
        if (time >= nextFlash) {
            flashStart = time;
            flashDir = SkyMath.dirFromAngles(Math.random() * 360, 15 + Math.random() * 55);
            nextFlash = time + (2 + Math.random() * 9) / Math.max(0.3, s.thunder);
        }
        double age = time - flashStart;
        if (age > 0.9) {
            return Effects.Flash.NONE;
        }
        // a bright strike with one or two after-flickers
        double f = Math.exp(-age * 9) + 0.6 * Math.exp(-Math.pow((age - 0.22) * 18, 2)) + 0.35 * Math.exp(-Math.pow((age - 0.45) * 20, 2));
        return new Effects.Flash(flashDir, Math.min(1, f) * s.thunder);
    }

    /** Cloud opacity seen in a direction (0 when there are no clouds there). */
    static double alpha(double[] dir) {
        CloudGen.Result r = shown;
        if (r == null || dir[1] <= 0.01) {
            return 0;
        }
        double el = Math.toDegrees(Math.asin(Math.min(1, dir[1])));
        double fade = SkyMath.smooth(minDeg + 0.5, minDeg + fadeDeg, el);
        double tt = height / dir[1];
        double x = dir[0] * tt, z = dir[2] * tt;
        double a = x * dirX + z * dirZ, b = -x * dirZ + z * dirX;
        int n = r.size();
        int u = (int) Math.floorMod((long) Math.floor((a - offA) / tile * n), n);
        int v = (int) Math.floorMod((long) Math.floor((b - offB) / tile * n), n);
        return ((r.argb()[v * n + u] >>> 24) & 255) / 255.0 * fade;
    }

    /** Sun rays: drawn after the clouds so beams shine through the gaps. */
    static void drawRays(Profile p, SkyState s, Matrix4f camera, Gpu.Tex white, int sunTint, double time, double r) {
        boolean cloudsOn = Settings.SKY_CLOUDS.on(p) && shown != null;
        int color = Settings.SKY_MODEL.is(p, "PHYSICAL")
                ? Colors.mixOklab(Settings.RAYS_COLOR.rgb(p), sunTint, 0.65)
                : Colors.mixOklab(Settings.RAYS_COLOR.rgb(p), sunTint, 0.35);
        rays.clear();
        Effects.sunRays(rays, p, s, time, cloudsOn ? CloudLayer::alpha : null, color, r);
        Gpu.drawOnce(rays, Gpu.alpha(), camera, new Vector4f(1, 1, 1, 1), white.view(), Gpu.sampler(false), "sun rays");
    }

    static void release() {
        closeTextures();
        shown = null;
        job.clear();
    }
}
