package dev.betterskies.screen;

import dev.betterskies.BetterSkies;
import dev.betterskies.config.Gradient;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Setting;
import dev.betterskies.config.Settings;
import dev.betterskies.ui.Theme;
import dev.betterskies.ui.Ui;
import dev.betterskies.util.Colors;

import java.util.HashMap;
import java.util.Map;

/** The editor's controls, drawn with {@link Ui}. Every method returns the height it used. */
final class Controls {
    private final StudioScreen s;
    /** Selected stop per gradient setting id. */
    private final Map<String, Integer> selectedStop = new HashMap<>();

    static final int ROW = 20;

    Controls(StudioScreen s) {
        this.s = s;
    }

    // ================================================================== buttons

    enum Style {PRIMARY, NORMAL, GHOST, DANGER}

    boolean button(Ui ui, String id, String label, int x, int y, int w, int h, Style style, String help, Runnable action) {
        return button(ui, id, label, x, y, w, h, style, true, help, action);
    }

    boolean button(Ui ui, String id, String label, int x, int y, int w, int h, Style style, boolean enabled, String help, Runnable action) {
        Theme t = ui.t;
        boolean hov = enabled && ui.hover(x, y, w, h);
        float a = ui.anim("btn:" + id, hov ? 1 : 0, 14);
        int bg = switch (style) {
            case PRIMARY -> Colors.argbf(0.85 + 0.15 * a, Colors.mix(t.accent, t.accent2, 0.15 * a));
            case DANGER -> Colors.argbf(0.25 + 0.5 * a, 0xFF4A5C);
            case GHOST -> Colors.argbf(0.08 * a + 0.0, 0xFFFFFF);
            default -> Colors.mixArgb(t.field, t.fieldHover, a);
        };
        int lift = (int) Math.round(a);
        if (style == Style.PRIMARY && enabled) {
            ui.round(x, y + 1, w, h, 4, t.accent(0.25 + 0.2 * a));
        }
        ui.round(x, y - lift, w, h, 4, enabled ? bg : 0x40303040);
        if (style == Style.NORMAL) {
            ui.roundOutline(x, y - lift, w, h, 4, Colors.argbf(0.08 + 0.12 * a, 0xFFFFFF));
        }
        int fg = !enabled ? t.textFaint : style == Style.PRIMARY ? 0xFF140E1E : style == Style.DANGER && hov ? 0xFFFFFFFF : t.text;
        ui.textCenter(ui.fit(label, w - 6), x + w / 2, y - lift + (h - 8) / 2, fg);
        if (enabled) {
            ui.hit(id, x, y, w, h, () -> {
                s.click();
                action.run();
            }, help);
        } else {
            ui.helpArea(x, y, w, h, help);
        }
        return hov;
    }

    /** A rounded chip (tabs, quick looks). */
    void chip(Ui ui, String id, String label, int x, int y, int w, int h, boolean selected, String help, Runnable action) {
        Theme t = ui.t;
        boolean hov = ui.hover(x, y, w, h);
        float a = ui.anim("chip:" + id, selected ? 1 : hov ? 0.45f : 0, 14);
        ui.round(x, y, w, h, h / 2, Colors.mixArgb(t.field, Colors.argbf(0.95, t.accent), a * 0.9));
        if (!selected) {
            ui.roundOutline(x, y, w, h, h / 2, Colors.argbf(0.1 + 0.2 * a, 0xFFFFFF));
        }
        ui.textCenter(ui.fit(label, w - 8), x + w / 2, y + (h - 8) / 2, selected ? 0xFF140E1E : t.text);
        ui.hit(id, x, y, w, h, () -> {
            s.click();
            action.run();
        }, help);
    }

    /** Small square icon button with a glyph. */
    void icon(Ui ui, String id, String glyph, int x, int y, int size, boolean enabled, String help, Runnable action) {
        Theme t = ui.t;
        boolean hov = enabled && ui.hover(x, y, size, size);
        float a = ui.anim("icon:" + id, hov ? 1 : 0, 14);
        ui.round(x, y, size, size, 3, Colors.argbf(0.1 * a + (enabled ? 0.04 : 0), 0xFFFFFF));
        ui.textCenter(glyph, x + size / 2 + 1, y + (size - 8) / 2, !enabled ? t.textFaint : hov ? t.accentText() : t.text);
        if (enabled) {
            ui.hit(id, x, y, size, size, () -> {
                s.click();
                action.run();
            }, help);
        } else {
            ui.helpArea(x, y, size, size, help);
        }
    }

    // ================================================================== setting rows

    /** Draws one setting as a row (label + control). Returns the height used. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    int row(Ui ui, Setting<?> st, int x, int y, int w) {
        Profile p = BetterSkies.profile();
        Theme t = ui.t;
        boolean active = st.active(p);
        if (st instanceof Setting.Grad gs) {
            return gradientRow(ui, gs, x, y, w, active);
        }
        boolean hov = ui.hover(x, y, w, ROW);
        float ha = ui.anim("row:" + st.id, hov ? 1 : 0, 16);
        if (ha > 0.01f) {
            ui.round(x, y, w, ROW, 3, Colors.argbf(0.05 * ha, 0xFFFFFF));
        }
        // pin star
        boolean pinned = BetterSkies.store().favorites.contains(st.id);
        if (pinned || hov) {
            boolean starHov = ui.hover(x + 2, y + 5, 10, 10);
            ui.text(pinned ? "★" : "☆", x + 3, y + 6, pinned ? Colors.opaque(t.accent2) : starHov ? t.text : t.textFaint);
            ui.hit("pin:" + st.id, x + 1, y + 4, 11, 12, () -> s.togglePin(st), pinned ? "Unpin from Home" : "Pin to the Home page");
        }
        int cw = Math.max(110, Math.min(190, (int) (w * 0.5)));
        int cx = x + w - cw - 4;
        String label = ui.fit(st.label, cx - x - 30);
        ui.text(label, x + 15, y + 6, active ? t.text : t.textFaint);
        if (st.advanced() && hov) {
            ui.text("adv", x + 18 + ui.font.width(label), y + 6, Colors.argbf(0.6, t.accent2));
        }
        // reset
        if (!st.isDefault(p)) {
            int rx = cx - 13;
            boolean rh = ui.hover(rx, y + 4, 11, 12);
            if (hov) {
                ui.text("↺", rx + 2, y + 6, rh ? t.accentText() : t.textDim);
                ui.hit("reset:" + st.id, rx, y + 4, 11, 12, () -> s.edit(() -> st.reset(p)), "Reset " + st.label + " to its default");
            } else {
                ui.rect(rx + 5, y + 9, 2, 2, t.accent(0.8));
            }
        }
        String help = st.help + (active ? "" : "  (needs another setting turned on first)");
        ui.helpArea(x, y, w, ROW, help);
        if (st instanceof Setting.Toggle tg) {
            toggle(ui, tg, cx + cw - 26, y + 4, active, help);
        } else if (st instanceof Setting.Slider sl) {
            slider(ui, sl, cx, y + 2, cw, active, help);
        } else if (st instanceof Setting.Color c) {
            colorField(ui, c, cx, y + 3, cw, active, help);
        } else if (st instanceof Setting.Choice ch) {
            choice(ui, ch, cx, y + 3, cw, active, help);
        } else if (st instanceof Setting.Text tx) {
            String v = tx.display(p);
            ui.textRight(ui.fit(v, cw), cx + cw, y + 6, t.textDim);
        }
        return ROW + 1;
    }

    void toggle(Ui ui, Setting.Toggle tg, int x, int y, boolean active, String help) {
        Profile p = BetterSkies.profile();
        Theme t = ui.t;
        boolean on = tg.on(p);
        float a = ui.anim("tog:" + tg.id, on ? 1 : 0, 18);
        int w = 24, h = 12;
        int track = Colors.mixArgb(0xFF3A3346, Colors.argbf(1, Colors.mix(t.accent, t.accent2, 0.2)), a);
        if (!active) {
            track = Colors.argbf(0.35, track);
        }
        ui.round(x, y, w, h, 6, track);
        int kx = x + 2 + Math.round(a * (w - 12));
        ui.circle(kx + 4, y + 6, 4, active ? 0xFFFFFFFF : 0xFF8A8494);
        if (active) {
            ui.hit("tog:" + tg.id, x - 4, y - 3, w + 8, h + 6, () -> s.edit(() -> tg.flip(p)), help);
        }
    }

    void slider(Ui ui, Setting.Slider sl, int x, int y, int w, boolean active, String help) {
        Profile p = BetterSkies.profile();
        Theme t = ui.t;
        int valueW = 46;
        int tx = x, tw = w - valueW - 6;
        int cy = y + 8;
        double n = Math.max(0, Math.min(1, sl.norm(p)));
        float shown = ui.anim("sl:" + sl.id, (float) n, 22);
        boolean hov = active && ui.hover(tx - 3, y, tw + 6, 16);
        boolean dragging = s.isDragging("sl:" + sl.id);
        ui.round(tx, cy - 2, tw, 4, 2, active ? 0xFF3A3346 : 0x603A3346);
        int fillW = Math.round(shown * tw);
        // signed sliders fill from their zero point
        double zero = sl.min < 0 && sl.max > 0 ? (0 - sl.min) / (sl.max - sl.min) : 0;
        int zx = tx + (int) Math.round(zero * tw);
        int fx = tx + fillW;
        int left = Math.min(zx, fx), right = Math.max(zx, fx);
        if (right > left) {
            int c1 = active ? Colors.opaque(t.accent) : 0x80606070;
            int c2 = active ? Colors.opaque(t.accent2) : 0x80707080;
            ui.gradientH(left, cy - 2, right - left, 4, c1, c2);
        }
        if (zero > 0) {
            ui.rect(zx, cy - 4, 1, 8, 0x60FFFFFF);
        }
        float ka = ui.anim("slk:" + sl.id, hov || dragging ? 1 : 0, 16);
        int kr = 3 + Math.round(ka * 1.5f);
        if (active) {
            ui.circle(fx, cy, kr + 2, t.accent(0.35 * ka));
        }
        ui.circle(fx, cy, kr, active ? 0xFFFFFFFF : 0xFF8A8494);
        // value pill
        int vx = x + w - valueW;
        ui.round(vx, y + 1, valueW, 14, 3, dragging ? Colors.argbf(0.9, t.accent) : t.field);
        ui.textCenter(ui.fit(sl.display(p), valueW - 4), vx + valueW / 2, y + 4, dragging ? 0xFF140E1E : active ? t.text : t.textFaint);
        if (!active) {
            return;
        }
        ui.hit("sl:" + sl.id, tx - 4, y, tw + 8, 16, (b, mx, my, dbl) -> {
            if (b == 1) {
                s.edit(() -> sl.reset(p));
                return;
            }
            if (b != 0) {
                return;
            }
            s.beginEdit();
            s.startDrag("sl:" + sl.id);
            set(sl, p, (mx - tx) / tw);
        }, new Ui.Drag() {
            @Override
            public void drag(double mx, double my) {
                set(sl, p, (mx - tx) / tw);
            }

            @Override
            public void release() {
                s.endEdit();
            }
        }, help + "  (right-click: reset)");
        ui.hit("slv:" + sl.id, vx, y + 1, valueW, 14, (b, mx, my, dbl) -> {
            if (b == 0) {
                s.click();
                s.promptNumber(sl);
            } else if (b == 1) {
                s.edit(() -> sl.reset(p));
            }
        }, null, "Click to type an exact value");
    }

    private void set(Setting.Slider sl, Profile p, double t) {
        double before = sl.v(p);
        sl.set(p, sl.denorm(t));
        if (before != sl.v(p)) {
            BetterSkies.changed();
        }
    }

    void colorField(Ui ui, Setting.Color c, int x, int y, int w, boolean active, String help) {
        Profile p = BetterSkies.profile();
        Theme t = ui.t;
        int rgb = c.rgb(p);
        int sw = 28;
        int sx = x + w - sw;
        boolean hov = active && ui.hover(x, y, w, 14);
        float a = ui.anim("col:" + c.id, hov ? 1 : 0, 16);
        ui.textRight(Colors.hex(rgb), sx - 5, y + 3, active ? Colors.mixArgb(t.textDim, t.text, a) : t.textFaint);
        ui.round(sx - 1, y - 1, sw + 2, 16, 4, Colors.argbf(0.3 + 0.5 * a, a > 0.5 ? t.accent : 0xFFFFFF));
        ui.round(sx, y, sw, 14, 3, Colors.argbf(active ? 1 : 0.4, rgb));
        if (active) {
            ui.hit("col:" + c.id, x + w / 3, y - 1, w - w / 3, 16, () -> s.pickColor(c.label, rgb, sx, y + 16,
                    v -> {
                        c.set(p, v);
                        BetterSkies.changed();
                    }), help);
        }
    }

    void choice(Ui ui, Setting.Choice ch, int x, int y, int w, boolean active, String help) {
        Profile p = BetterSkies.profile();
        Theme t = ui.t;
        int n = ch.options.size();
        int total = 0;
        for (Setting.Choice.Option o : ch.options) {
            total += ui.font.width(o.label()) + 10;
        }
        if (n <= 3 && total <= w) {
            // segmented control
            int segW = w / n;
            int sel = ch.ordinal(p);
            float pos = ui.anim("seg:" + ch.id, sel, 18);
            ui.round(x, y, w, 14, 4, active ? t.field : 0x60241F2E);
            ui.round(x + Math.round(pos * segW) + 1, y + 1, segW - 2, 12, 3, active ? Colors.argbf(0.95, t.accent) : 0x60808080);
            for (int i = 0; i < n; i++) {
                Setting.Choice.Option o = ch.options.get(i);
                int sx = x + i * segW;
                boolean on = i == sel;
                ui.textCenter(ui.fit(o.label(), segW - 4), sx + segW / 2, y + 3, on ? 0xFF140E1E : active ? t.textDim : t.textFaint);
                if (active) {
                    String id = o.id();
                    ui.hit("seg:" + ch.id + ":" + i, sx, y, segW, 14, () -> s.edit(() -> ch.set(p, id)),
                            o.help().isEmpty() ? help : o.label() + ": " + o.help());
                }
            }
            return;
        }
        boolean hov = active && ui.hover(x, y, w, 14);
        ui.round(x, y, w, 14, 4, hov ? t.fieldHover : active ? t.field : 0x60241F2E);
        ui.text(ui.fit(ch.display(p), w - 16), x + 5, y + 3, active ? t.text : t.textFaint);
        ui.text("▾", x + w - 10, y + 3, hov ? t.accentText() : t.textDim);
        if (active) {
            ui.hit("ch:" + ch.id, x, y, w, 14, (b, mx, my, dbl) -> {
                if (b == 0) {
                    s.click();
                    s.dropdown(ch, x, y + 15, w);
                } else if (b == 1) {
                    s.edit(() -> ch.cycle(p, 1));
                }
            }, null, help + "  (right-click: next option)");
        }
    }

    // ================================================================== gradient editor

    int gradientRow(Ui ui, Setting.Grad gs, int x, int y, int w, boolean active) {
        Theme t = ui.t;
        Profile p = BetterSkies.profile();
        ui.text(gs.label, x + 4, y + 2, active ? t.text : t.textFaint);
        if (!active) {
            ui.text("(turn on its option above)", x + 8 + ui.font.width(gs.label), y + 2, t.textFaint);
        }
        int bx = x + 4, bw = w - 8, by = y + 13;
        gradientEditor(ui, gs, bx, by, bw, active);
        ui.helpArea(x, y, w, 13, gs.help);
        return 13 + (active ? gradientEditorHeight(gs) : 30) + 3;
    }

    /** Gradient bar with draggable color stops. Height: 26 (+18 when a stop is selected). */
    void gradientEditor(Ui ui, Setting.Grad gs, int x, int y, int w, boolean active) {
        Theme t = ui.t;
        Profile p = BetterSkies.profile();
        Gradient gr = gs.get(p);
        Gradient.Blend blend = Gradient.Blend.valueOf(Settings.BLEND.get(p));
        double bias = Settings.BIAS.v(p);
        int h = 16;
        ui.round(x - 1, y - 1, w + 2, h + 2, 4, 0x80000000);
        ui.pushClip(x, y, x + w, y + h);
        for (int i = 0; i < w; i += 2) {
            int c = gr.sample((i + 1.0) / w, blend, bias);
            ui.rect(x + i, y, 2, h, Colors.argbf(active ? 1 : 0.4, c));
        }
        ui.popClip();
        Integer sel = selectedStop.get(gs.id);
        if (sel != null && sel >= gr.size()) {
            selectedStop.remove(gs.id);
            sel = null;
        }
        // add-stop area (the bar itself)
        if (active) {
            ui.hit("gbar:" + gs.id, x, y, w, h, (b, mx, my, dbl) -> {
                if (b != 0) {
                    return;
                }
                double pos = Math.max(0, Math.min(1, (mx - x) / w));
                Gradient g0 = gs.get(p);
                if (g0.size() >= Gradient.MAX_STOPS) {
                    s.toast("A gradient can have at most " + Gradient.MAX_STOPS + " colors", t.warn);
                    return;
                }
                Gradient.Stop st = new Gradient.Stop(pos, g0.sample(pos, blend, bias));
                s.edit(() -> gs.set(p, g0.add(st)));
                selectedStop.put(gs.id, gs.get(p).indexOf(st));
            }, null, "Click the bar to add a color. Drag the handles to move colors; right-click a handle to remove it.");
        }
        // handles
        for (int i = 0; i < gr.size(); i++) {
            Gradient.Stop st = gr.get(i);
            int hx = x + (int) Math.round(st.pos() * w);
            boolean isSel = sel != null && sel == i;
            boolean hov = active && ui.hover(hx - 5, y + h - 2, 10, 13);
            int top = y + h + 1;
            int edge = isSel ? Colors.opaque(t.accent2) : hov ? 0xFFFFFFFF : 0xFFBDB6CC;
            ui.rect(hx, y - 2, 1, h + 3, isSel ? Colors.opaque(t.accent2) : 0x90FFFFFF);
            ui.round(hx - 5, top, 11, 10, 2, edge);
            ui.round(hx - 4, top + 1, 9, 8, 2, Colors.opaque(st.rgb()));
            if (!active) {
                continue;
            }
            final int index = i;
            ui.hit("gstop:" + gs.id + ":" + i, hx - 6, top - 2, 13, 13, (b, mx, my, dbl) -> {
                if (b == 1) {
                    if (gs.get(p).size() <= 2) {
                        s.toast("A gradient needs at least two colors", t.warn);
                        return;
                    }
                    s.edit(() -> gs.set(p, gs.get(p).remove(index)));
                    selectedStop.remove(gs.id);
                    return;
                }
                selectedStop.put(gs.id, index);
                if (dbl) {
                    openStopColor(gs, p, index, hx, top + 12);
                    return;
                }
                s.click();
                s.beginEdit();
                s.startDrag("gstop:" + gs.id);
            }, new Ui.Drag() {
                @Override
                public void drag(double mx, double my) {
                    Integer cur = selectedStop.get(gs.id);
                    Gradient g0 = gs.get(p);
                    if (cur == null || cur >= g0.size()) {
                        return;
                    }
                    Gradient.Stop moved = new Gradient.Stop(Math.max(0, Math.min(1, (mx - x) / w)), g0.get(cur).rgb());
                    Gradient g1 = g0.with(cur, moved);
                    gs.set(p, g1);
                    selectedStop.put(gs.id, g1.indexOf(moved));
                    BetterSkies.changed();
                }

                @Override
                public void release() {
                    s.endEdit();
                }
            }, "Drag to move · double-click to change color · right-click to remove");
        }
        // selected stop details
        if (active && sel != null) {
            Gradient.Stop st = gr.get(sel);
            int ry = y + h + 14;
            ui.text("Color " + (sel + 1) + "/" + gr.size(), x, ry + 3, t.textDim);
            ui.text(Math.round(st.pos() * 100) + "% up", x + 62, ry + 3, t.textFaint);
            int sx = x + 112;
            final int index = sel;
            boolean sh = ui.hover(sx, ry, 40, 14);
            ui.round(sx - 1, ry - 1, 42, 16, 4, sh ? Colors.opaque(t.accent) : 0x50FFFFFF);
            ui.round(sx, ry, 40, 14, 3, Colors.opaque(st.rgb()));
            ui.hit("gstopc:" + gs.id, sx, ry, 40, 14, () -> openStopColor(gs, p, index, sx, ry + 16), "Change this color");
            ui.text(Colors.hex(st.rgb()), sx + 46, ry + 3, t.textDim);
            int bx = x + w;
            bx -= 46;
            button(ui, "gdel:" + gs.id, "Remove", bx, ry, 46, 14, Style.GHOST, gr.size() > 2, "Remove this color", () -> {
                s.edit(() -> gs.set(p, gs.get(p).remove(index)));
                selectedStop.remove(gs.id);
            });
        }
    }

    private void openStopColor(Setting.Grad gs, Profile p, int index, int ax, int ay) {
        Gradient g0 = gs.get(p);
        if (index >= g0.size()) {
            return;
        }
        double pos = g0.get(index).pos();
        s.pickColor(gs.label + " color", g0.get(index).rgb(), ax, ay, v -> {
            Gradient cur = gs.get(p);
            // find the stop at this position (indices can shift while editing)
            int best = 0;
            double bd = 2;
            for (int i = 0; i < cur.size(); i++) {
                double d = Math.abs(cur.get(i).pos() - pos);
                if (d < bd) {
                    bd = d;
                    best = i;
                }
            }
            gs.set(p, cur.with(best, new Gradient.Stop(cur.get(best).pos(), v)));
            BetterSkies.changed();
        });
    }

    int gradientEditorHeight(Setting.Grad gs) {
        return 30 + (selectedStop.containsKey(gs.id) ? 18 : 0);
    }

    void clearSelection(Setting.Grad gs) {
        selectedStop.remove(gs.id);
    }
}
