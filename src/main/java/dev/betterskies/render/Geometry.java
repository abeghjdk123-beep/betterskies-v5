package dev.betterskies.render;

/**
 * Sky geometry as plain arrays: quads of 4 vertices, each vertex x, y, z, u, v and
 * a packed ARGB color. Quads are automatically wound to face the viewer at the
 * origin, so back-face culling never hides them. Pure Java (testable off-game).
 */
public final class Geometry {
    private float[] pos = new float[5 * 4 * 128];
    private int[] col = new int[4 * 128];
    private int verts;

    public int vertexCount() {
        return verts;
    }

    public boolean isEmpty() {
        return verts == 0;
    }

    public void clear() {
        verts = 0;
    }

    public float x(int i) {
        return pos[i * 5];
    }

    public float y(int i) {
        return pos[i * 5 + 1];
    }

    public float z(int i) {
        return pos[i * 5 + 2];
    }

    public float u(int i) {
        return pos[i * 5 + 3];
    }

    public float v(int i) {
        return pos[i * 5 + 4];
    }

    public int argb(int i) {
        return col[i];
    }

    private void ensure(int more) {
        if (verts + more > col.length) {
            int n = Math.max(col.length * 2, verts + more);
            float[] p = new float[n * 5];
            System.arraycopy(pos, 0, p, 0, verts * 5);
            pos = p;
            int[] c = new int[n];
            System.arraycopy(col, 0, c, 0, verts);
            col = c;
        }
    }

    private void vertex(double[] p, double u, double v, int argb) {
        int o = verts * 5;
        pos[o] = (float) p[0];
        pos[o + 1] = (float) p[1];
        pos[o + 2] = (float) p[2];
        pos[o + 3] = (float) u;
        pos[o + 4] = (float) v;
        col[verts++] = argb;
    }

    /** Adds a quad (corners in order around the edge), flipped if needed to face the origin. */
    public void quad(double[] p0, double u0, double v0, int c0,
                     double[] p1, double u1, double v1, int c1,
                     double[] p2, double u2, double v2, int c2,
                     double[] p3, double u3, double v3, int c3) {
        ensure(4);
        double ax = p1[0] - p0[0], ay = p1[1] - p0[1], az = p1[2] - p0[2];
        double bx = p2[0] - p0[0], by = p2[1] - p0[1], bz = p2[2] - p0[2];
        double nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        if (nx * nx + ny * ny + nz * nz < 1e-12) {
            // first edge collapsed (e.g. the centre of a disc): use the other triangle
            double cx = p3[0] - p0[0], cy = p3[1] - p0[1], cz = p3[2] - p0[2];
            nx = by * cz - bz * cy;
            ny = bz * cx - bx * cz;
            nz = bx * cy - by * cx;
        }
        double dot = nx * (p0[0] + p2[0]) + ny * (p0[1] + p2[1]) + nz * (p0[2] + p2[2]);
        if (dot <= 0) {
            vertex(p0, u0, v0, c0);
            vertex(p1, u1, v1, c1);
            vertex(p2, u2, v2, c2);
            vertex(p3, u3, v3, c3);
        } else {
            vertex(p0, u0, v0, c0);
            vertex(p3, u3, v3, c3);
            vertex(p2, u2, v2, c2);
            vertex(p1, u1, v1, c1);
        }
    }

    // ================================================================== shapes

    /**
     * Unit-direction sphere mapped like an equirectangular picture:
     * u = azimuth from north (0) through east (0.25), v = 0 at the zenith, 1 at the nadir.
     */
    public static Geometry sphere(double r, int lonSteps, int latSteps) {
        Geometry g = new Geometry();
        for (int j = 0; j < latSteps; j++) {
            double v0 = (double) j / latSteps, v1 = (double) (j + 1) / latSteps;
            for (int i = 0; i < lonSteps; i++) {
                double u0 = (double) i / lonSteps, u1 = (double) (i + 1) / lonSteps;
                g.quad(SkyMath.scale(SkyMath.dirFromUv(u0, v0), r), u0, v0, -1,
                        SkyMath.scale(SkyMath.dirFromUv(u1, v0), r), u1, v0, -1,
                        SkyMath.scale(SkyMath.dirFromUv(u1, v1), r), u1, v1, -1,
                        SkyMath.scale(SkyMath.dirFromUv(u0, v1), r), u0, v1, -1);
            }
        }
        return g;
    }

    /** A square facing the origin at unit direction d; half = half-size in radians; spin rotates it. */
    public void billboard(double[] d, double r, double half, double spin, int argb) {
        double[] t1 = SkyMath.tangent(d);
        double[] t2 = SkyMath.cross(d, t1);
        if (spin != 0) {
            double c = Math.cos(spin), s = Math.sin(spin);
            double[] a = SkyMath.add(SkyMath.scale(t1, c), SkyMath.scale(t2, s));
            double[] b = SkyMath.add(SkyMath.scale(t1, -s), SkyMath.scale(t2, c));
            t1 = a;
            t2 = b;
        }
        double h = half * r;
        double[] c = SkyMath.scale(d, r);
        quad(SkyMath.add(c, t1, -h, t2, -h), 0, 0, argb,
                SkyMath.add(c, t1, h, t2, -h), 1, 0, argb,
                SkyMath.add(c, t1, h, t2, h), 1, 1, argb,
                SkyMath.add(c, t1, -h, t2, h), 0, 1, argb);
    }

    /** A billboard with its texture's "up" aligned to the given direction (used for oriented sprites). */
    public void billboardOriented(double[] d, double[] up, double r, double halfW, double halfH, int argb) {
        double[] side = SkyMath.norm(SkyMath.cross(d, up));
        double[] u = SkyMath.norm(SkyMath.cross(side, d));
        double[] c = SkyMath.scale(d, r);
        quad(SkyMath.add(c, side, -halfW * r, u, -halfH * r), 0, 1, argb,
                SkyMath.add(c, side, halfW * r, u, -halfH * r), 1, 1, argb,
                SkyMath.add(c, side, halfW * r, u, halfH * r), 1, 0, argb,
                SkyMath.add(c, side, -halfW * r, u, halfH * r), 0, 0, argb);
    }

    /**
     * A glowing ribbon through unit directions (tail first). Each point has its own
     * half-width (radians) and color, so trails can taper and fade.
     */
    public void ribbon(double[][] pts, double[] halfWidth, int[] colors, double r) {
        int n = pts.length;
        double[][] side = new double[n][];
        for (int i = 0; i < n; i++) {
            double[] dir = SkyMath.sub(pts[Math.min(n - 1, i + 1)], pts[Math.max(0, i - 1)]);
            side[i] = SkyMath.norm(SkyMath.cross(pts[i], dir));
        }
        for (int i = 0; i < n - 1; i++) {
            double[] a = SkyMath.scale(pts[i], r), b = SkyMath.scale(pts[i + 1], r);
            double wa = halfWidth[i] * r, wb = halfWidth[i + 1] * r;
            quad(SkyMath.add(a, side[i], -wa, side[i], 0), 0, 0, colors[i],
                    SkyMath.add(b, side[i + 1], -wb, side[i + 1], 0), 1, 0, colors[i + 1],
                    SkyMath.add(b, side[i + 1], wb, side[i + 1], 0), 1, 1, colors[i + 1],
                    SkyMath.add(a, side[i], wa, side[i], 0), 0, 1, colors[i]);
        }
    }
}
