package dev.betterskies.ui;

import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.betterskies.util.Colors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny immediate-mode UI: every frame the screen draws its controls and
 * registers clickable areas ("hits"); mouse events from the next frame are
 * routed to the hit under the cursor. Animation state lives in a map keyed by id.
 */
public final class Ui {
    public interface Click {
        void click(int button, double mx, double my, boolean doubleClick);
    }

    public interface Drag {
        void drag(double mx, double my);

        default void release() {
        }
    }

    public record Hit(String id, int x, int y, int w, int h, int cx1, int cy1, int cx2, int cy2,
                      Click click, Drag drag, String help, int layer) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h && mx >= cx1 && mx < cx2 && my >= cy1 && my < cy2;
        }
    }

    public GuiGraphics g;
    public Font font;
    public int mx;
    public int my;
    public float dt;
    public Theme t;
    public boolean animations = true;
    /** Popup layer: while a popup is open, only layer-1 controls react. */
    public int layer;
    public int popupX1, popupY1, popupX2, popupY2;
    public boolean popupOpen;

    private List<Hit> hits = new ArrayList<>();
    private List<Hit> building = new ArrayList<>();
    private final Map<String, Float> anims = new HashMap<>();
    private final Deque<int[]> clips = new ArrayDeque<>();
    private int[] clip = {-100000, -100000, 100000, 100000};
    public String help;
    private CursorType cursor;
    private long last = System.nanoTime();

    public void begin(GuiGraphics g, Font font, int mx, int my, Theme t, boolean animations) {
        this.g = g;
        this.font = font;
        this.mx = mx;
        this.my = my;
        this.t = t;
        this.animations = animations;
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        building = new ArrayList<>();
        help = null;
        cursor = null;
        layer = 0;
        clips.clear();
        clip = new int[]{-100000, -100000, 100000, 100000};
    }

    public void end() {
        hits = building;
        if (cursor != null) {
            g.requestCursor(cursor);
        }
    }

    /** Asks for a specific mouse cursor this frame (first request wins). */
    public void cursor(CursorType type) {
        if (cursor == null) {
            cursor = type;
        }
    }

    // ================================================================== hits & hover

    public boolean hover(int x, int y, int w, int h) {
        if (mx < clip[0] || mx >= clip[2] || my < clip[1] || my >= clip[3]) {
            return false;
        }
        if (popupOpen && layer == 0 && mx >= popupX1 && mx < popupX2 && my >= popupY1 && my < popupY2) {
            return false;
        }
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void hit(String id, int x, int y, int w, int h, Click click, Drag drag, String help) {
        building.add(new Hit(id, x, y, w, h, clip[0], clip[1], clip[2], clip[3], click, drag, help, layer));
        if (hover(x, y, w, h)) {
            if (help != null) {
                this.help = help;
            }
            if (cursor == null) {
                cursor = drag != null ? CursorTypes.RESIZE_EW : CursorTypes.POINTING_HAND;
            }
        }
    }

    public void hit(String id, int x, int y, int w, int h, Runnable action, String help) {
        hit(id, x, y, w, h, (b, mx, my, d) -> {
            if (b == 0) {
                action.run();
            }
        }, null, help);
    }

    public void helpArea(int x, int y, int w, int h, String help) {
        if (help != null && hover(x, y, w, h)) {
            this.help = help;
        }
    }

    /** Topmost hit under the mouse (last frame). */
    public Hit find(double mx, double my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (popupOpen && h.layer() == 0) {
                continue;
            }
            if (h.contains(mx, my)) {
                return h;
            }
        }
        return null;
    }

    // ================================================================== animation

    /** Smoothly moves a value toward target; returns the current value. */
    public float anim(String id, float target, float speed) {
        if (!animations) {
            anims.put(id, target);
            return target;
        }
        Float cur = anims.get(id);
        if (cur == null) {
            anims.put(id, target);
            return target;
        }
        float k = 1 - (float) Math.exp(-speed * dt);
        float v = cur + (target - cur) * k;
        if (Math.abs(v - target) < 0.001f) {
            v = target;
        }
        anims.put(id, v);
        return v;
    }

    public void setAnim(String id, float v) {
        anims.put(id, v);
    }

    // ================================================================== clipping

    public void pushClip(int x1, int y1, int x2, int y2) {
        clips.push(clip);
        clip = new int[]{Math.max(clip[0], x1), Math.max(clip[1], y1), Math.min(clip[2], x2), Math.min(clip[3], y2)};
        g.enableScissor(clip[0], clip[1], Math.max(clip[0], clip[2]), Math.max(clip[1], clip[3]));
    }

    public void popClip() {
        // GuiGraphics keeps its own scissor stack: disable pops back to the outer clip
        clip = clips.isEmpty() ? new int[]{-100000, -100000, 100000, 100000} : clips.pop();
        g.disableScissor();
    }

    // ================================================================== drawing

    public void rect(int x, int y, int w, int h, int argb) {
        if (w > 0 && h > 0 && (argb >>> 24) != 0) {
            g.fill(x, y, x + w, y + h, argb);
        }
    }

    /** Rounded rectangle (radius up to 6). */
    public void round(int x, int y, int w, int h, int r, int argb) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            rect(x, y, w, h, argb);
            return;
        }
        rect(x, y + r, w, h - 2 * r, argb);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            rect(x + inset, y + i, w - 2 * inset, 1, argb);
            rect(x + inset, y + h - 1 - i, w - 2 * inset, 1, argb);
        }
    }

    /** Rounded outline, 1px. */
    public void roundOutline(int x, int y, int w, int h, int r, int argb) {
        r = Math.min(r, Math.min(w, h) / 2);
        rect(x + r, y, w - 2 * r, 1, argb);
        rect(x + r, y + h - 1, w - 2 * r, 1, argb);
        rect(x, y + r, 1, h - 2 * r, argb);
        rect(x + w - 1, y + r, 1, h - 2 * r, argb);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            int next = i + 1 < r ? (int) Math.round(r - Math.sqrt(Math.max(0, r * r - (r - i - 1.5) * (r - i - 1.5)))) : 0;
            int len = Math.max(1, inset - next);
            rect(x + next, y + i, len, 1, argb);
            rect(x + w - next - len, y + i, len, 1, argb);
            rect(x + next, y + h - 1 - i, len, 1, argb);
            rect(x + w - next - len, y + h - 1 - i, len, 1, argb);
        }
    }

    /** Soft drop shadow under a rounded box. */
    public void shadow(int x, int y, int w, int h, int r, int strength) {
        for (int i = 4; i >= 1; i--) {
            round(x - i, y - i + 2, w + 2 * i, h + 2 * i, r + i, Colors.argb(strength / (i + 1), 0));
        }
    }

    public void gradientV(int x, int y, int w, int h, int top, int bottom) {
        if (w > 0 && h > 0) {
            g.fillGradient(x, y, x + w, y + h, top, bottom);
        }
    }

    public void gradientH(int x, int y, int w, int h, int left, int right) {
        int steps = Math.max(1, Math.min(w, 48));
        for (int i = 0; i < steps; i++) {
            int x0 = x + w * i / steps, x1 = x + w * (i + 1) / steps;
            rect(x0, y, x1 - x0, h, Colors.mixArgb(left, right, (i + 0.5) / steps));
        }
    }

    public void text(String s, int x, int y, int argb) {
        g.drawString(font, s, x, y, argb, false);
    }

    public void textShadow(String s, int x, int y, int argb) {
        g.drawString(font, s, x, y, argb, true);
    }

    public void textCenter(String s, int cx, int y, int argb) {
        g.drawString(font, s, cx - font.width(s) / 2, y, argb, false);
    }

    public void textRight(String s, int rx, int y, int argb) {
        g.drawString(font, s, rx - font.width(s), y, argb, false);
    }

    public void textScaled(String s, int x, int y, float scale, int argb, boolean shadow) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.drawString(font, s, 0, 0, argb, shadow);
        g.pose().popMatrix();
    }

    /** Cuts a string to fit a width, adding an ellipsis. */
    public String fit(String s, int w) {
        if (font.width(s) <= w) {
            return s;
        }
        String e = "…";
        return font.plainSubstrByWidth(s, Math.max(0, w - font.width(e))) + e;
    }

    /** Word-wraps plain text into lines no wider than w. */
    public List<String> wrap(String s, int w) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = line.isEmpty() ? word : line + " " + word;
            if (font.width(test) > w && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** A filled circle (small radii). */
    public void circle(int cx, int cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy + 0.25)));
            rect(cx - half, cy + dy, half * 2 + 1, 1, argb);
        }
    }
}
