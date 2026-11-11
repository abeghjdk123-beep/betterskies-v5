package dev.betterskies.screen;

import dev.betterskies.BetterSkies;
import dev.betterskies.config.Gradient;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Setting;
import dev.betterskies.config.Settings;
import dev.betterskies.ui.TextField;
import dev.betterskies.ui.Theme;
import dev.betterskies.ui.Ui;
import dev.betterskies.util.Colors;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Floating panels drawn above everything: color picker, dropdowns, prompts, confirmations. */
final class Popups {
    private Popups() {
    }

    abstract static class Popup {
        int x, y, w, h;
        boolean closed;

        abstract void draw(StudioScreen s, Ui ui);

        boolean keyPressed(StudioScreen s, KeyEvent e) {
            if (e.key() == GLFW.GLFW_KEY_ESCAPE) {
                close(s);
                return true;
            }
            return false;
        }

        boolean charTyped(StudioScreen s, CharacterEvent e) {
            return false;
        }

        boolean scrolled(double amount) {
            return false;
        }

        void close(StudioScreen s) {
            closed = true;
        }

        void place(int ax, int ay, int sw, int sh) {
            x = Math.max(4, Math.min(ax, sw - w - 4));
            y = ay + h > sh - 4 ? Math.max(4, ay - h - 20) : ay;
        }

        void frame(Ui ui, String title) {
            Theme t = ui.t;
            ui.shadow(x, y, w, h, 6, 120);
            ui.round(x, y, w, h, 6, 0xF2141019);
            ui.roundOutline(x, y, w, h, 6, t.panelEdge);
            if (title != null) {
                ui.text(ui.fit(title, w - 24), x + 8, y + 7, t.text);
            }
        }
    }

    // ================================================================== color picker

    static final class ColorPicker extends Popup {
        private final String title;
        private final int original;
        private final IntConsumer apply;
        private double hue, sat, val;
        private final TextField hex = new TextField();
        private int current;

        ColorPicker(String title, int rgb, IntConsumer apply) {
            this.title = title;
            this.original = rgb;
            this.current = rgb;
            this.apply = apply;
            double[] hsv = Colors.toHsv(rgb);
            hue = hsv[0];
            sat = hsv[1];
            val = hsv[2];
            w = 196;
            h = 186;
            hex.set(Colors.hex(rgb));
            hex.maxLength = 7;
            hex.allowed = c -> c == '#' || Character.digit(c, 16) >= 0;
            hex.onChange = s -> {
                int c = Colors.parse(s);
                if (c >= 0) {
                    setColor(c, false);
                }
            };
            hex.onSubmit = s -> hex.focused = false;
        }

        private void setColor(int rgb, boolean updateHex) {
            current = rgb;
            double[] hsv = Colors.toHsv(rgb);
            if (hsv[1] > 0.001 && hsv[2] > 0.001) {
                hue = hsv[0];
            }
            sat = hsv[1];
            val = hsv[2];
            if (updateHex) {
                hex.set(Colors.hex(rgb));
            }
            apply.accept(rgb);
        }

        private void fromHsv() {
            current = Colors.hsv(hue, sat, val);
            hex.set(Colors.hex(current));
            apply.accept(current);
        }

        @Override
        void draw(StudioScreen s, Ui ui) {
            Theme t = ui.t;
            frame(ui, title);
            int sx = x + 8, sy = y + 20, size = 100;
            // saturation/value square: each column fades from (s, v=1) to black
            int cols = 50;
            for (int i = 0; i < cols; i++) {
                double sv = (i + 0.5) / cols;
                int top = 0xFF000000 | Colors.hsv(hue, sv, 1);
                int x0 = sx + size * i / cols, x1 = sx + size * (i + 1) / cols;
                ui.gradientV(x0, sy, x1 - x0, size, top, 0xFF000000);
            }
            int px = sx + (int) Math.round(sat * size), py = sy + (int) Math.round((1 - val) * size);
            ui.rect(px - 3, py, 7, 1, val > 0.5 ? 0xFF000000 : 0xFFFFFFFF);
            ui.rect(px, py - 3, 1, 7, val > 0.5 ? 0xFF000000 : 0xFFFFFFFF);
            ui.hit("cp:sv", sx, sy, size, size, (b, mx, my, d) -> {
                pickSv(mx, my, sx, sy, size);
            }, new Ui.Drag() {
                @Override
                public void drag(double mx, double my) {
                    pickSv(mx, my, sx, sy, size);
                }
            }, "Saturation (left-right) and brightness (up-down)");
            // hue bar
            int hx = sx + size + 6, hw = 12;
            for (int i = 0; i < size; i += 2) {
                ui.rect(hx, sy + i, hw, 2, 0xFF000000 | Colors.hsv((i + 1.0) / size, 1, 1));
            }
            int hy = sy + (int) Math.round(hue * size);
            ui.rect(hx - 2, hy - 1, hw + 4, 3, 0xFFFFFFFF);
            ui.rect(hx - 1, hy, hw + 2, 1, 0xFF000000);
            ui.hit("cp:h", hx - 2, sy, hw + 4, size, (b, mx, my, d) -> pickHue(my, sy, size), new Ui.Drag() {
                @Override
                public void drag(double mx, double my) {
                    pickHue(my, sy, size);
                }
            }, "Hue");
            // preview: before / after
            int pvx = hx + hw + 8, pvw = x + w - 8 - pvx;
            ui.text("New", pvx, sy, t.textDim);
            ui.round(pvx, sy + 10, pvw, 22, 3, 0xFF000000 | current);
            ui.text("Before", pvx, sy + 38, t.textDim);
            ui.round(pvx, sy + 48, pvw, 16, 3, 0xFF000000 | original);
            ui.hit("cp:orig", pvx, sy + 48, pvw, 16, () -> setColor(original, true), "Go back to the original color");
            hex.draw(ui, "cp:hex", pvx, sy + 72, pvw, 14, "Type a hex color like #FF78C8");
            // swatches: recent colors, then colors from the sky
            int rowY = sy + size + 8;
            ui.text("Recent", sx, rowY, t.textFaint);
            swatches(ui, "cp:r", BetterSkies.store().recentColors, sx + 40, rowY - 2);
            ui.text("In sky", sx, rowY + 16, t.textFaint);
            swatches(ui, "cp:s", skyColors(), sx + 40, rowY + 14);
            // buttons
            int by = y + h - 20;
            s.controls.button(ui, "cp:ok", "Done", x + w - 58, by, 50, 14, Controls.Style.PRIMARY, "Keep this color", () -> close(s));
            s.controls.button(ui, "cp:cancel", "Cancel", x + w - 112, by, 50, 14, Controls.Style.GHOST, "Discard the change", () -> {
                apply.accept(original);
                closed = true;
                s.cancelEdit();
            });
        }

        private void swatches(Ui ui, String id, List<Integer> colors, int x0, int y0) {
            int n = Math.min(colors.size(), 10);
            for (int i = 0; i < n; i++) {
                int c = colors.get(i);
                int sx = x0 + i * 14;
                boolean hov = ui.hover(sx, y0, 12, 12);
                ui.round(sx, y0, 12, 12, 2, hov ? 0xFFFFFFFF : 0x60FFFFFF);
                ui.round(sx + 1, y0 + 1, 10, 10, 2, 0xFF000000 | c);
                ui.hit(id + i, sx, y0, 12, 12, () -> setColor(c, true), Colors.hex(c));
            }
        }

        private static List<Integer> skyColors() {
            Profile p = BetterSkies.profile();
            Set<Integer> set = new LinkedHashSet<>();
            for (Setting.Grad g : List.of(Settings.GRAD_DAY, Settings.GRAD_DUSK, Settings.GRAD_NIGHT)) {
                for (Gradient.Stop st : g.get(p).stops()) {
                    set.add(st.rgb());
                }
            }
            return new ArrayList<>(set);
        }

        private void pickSv(double mx, double my, int sx, int sy, int size) {
            sat = Math.max(0, Math.min(1, (mx - sx) / size));
            val = Math.max(0, Math.min(1, 1 - (my - sy) / size));
            fromHsv();
        }

        private void pickHue(double my, int sy, int size) {
            hue = Math.max(0, Math.min(0.999, (my - sy) / size));
            fromHsv();
        }

        @Override
        boolean keyPressed(StudioScreen s, KeyEvent e) {
            if (hex.focused && e.key() != GLFW.GLFW_KEY_ESCAPE) {
                return hex.keyPressed(e);
            }
            if (e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER) {
                close(s);
                return true;
            }
            return super.keyPressed(s, e);
        }

        @Override
        boolean charTyped(StudioScreen s, CharacterEvent e) {
            return hex.charTyped(e);
        }

        @Override
        void close(StudioScreen s) {
            if (current != original) {
                BetterSkies.store().rememberColor(current);
            }
            closed = true;
            s.endEdit();
        }
    }

    // ================================================================== dropdown

    static final class Dropdown extends Popup {
        private final Setting.Choice choice;
        private float scroll;

        Dropdown(Setting.Choice choice, int width) {
            this.choice = choice;
            this.w = width;
            this.h = Math.min(choice.options.size(), 9) * 15 + 6;
        }

        @Override
        void draw(StudioScreen s, Ui ui) {
            Theme t = ui.t;
            Profile p = BetterSkies.profile();
            ui.shadow(x, y, w, h, 4, 110);
            ui.round(x, y, w, h, 4, 0xF5141019);
            ui.roundOutline(x, y, w, h, 4, t.panelEdge);
            ui.pushClip(x, y + 3, x + w, y + h - 3);
            int sel = choice.ordinal(p);
            int oy = y + 3 - Math.round(scroll);
            for (int i = 0; i < choice.options.size(); i++) {
                Setting.Choice.Option o = choice.options.get(i);
                int ry = oy + i * 15;
                boolean hov = ui.hover(x + 2, ry, w - 4, 15);
                if (i == sel) {
                    ui.round(x + 2, ry, w - 4, 15, 3, t.accent(0.9));
                } else if (hov) {
                    ui.round(x + 2, ry, w - 4, 15, 3, 0x22FFFFFF);
                }
                ui.text(ui.fit(o.label(), w - 12), x + 7, ry + 4, i == sel ? 0xFF140E1E : t.text);
                String id = o.id();
                ui.hit("dd:" + i, x + 2, ry, w - 4, 15, () -> {
                    s.edit(() -> choice.set(p, id));
                    closed = true;
                }, o.help().isEmpty() ? o.label() : o.label() + ": " + o.help());
            }
            ui.popClip();
        }

        @Override
        boolean scrolled(double amount) {
            float max = Math.max(0, choice.options.size() * 15 + 6 - h);
            scroll = (float) Math.max(0, Math.min(max, scroll - amount * 15));
            return true;
        }
    }

    // ================================================================== text prompt

    static final class Prompt extends Popup {
        private final String title;
        private final String hint;
        private final TextField field = new TextField();
        private final Consumer<String> done;

        Prompt(String title, String hint, String initial, int maxLength, Consumer<String> done) {
            this.title = title;
            this.hint = hint;
            this.done = done;
            field.set(initial);
            field.maxLength = maxLength;
            field.focus();
            field.onSubmit = s -> finish();
            w = 220;
            h = 76;
        }

        Prompt numeric() {
            field.allowed = c -> Character.isDigit(c) || c == '.' || c == '-' || c == ':' || c == '%' || c == '+';
            return this;
        }

        private void finish() {
            closed = true;
            done.accept(field.text);
        }

        @Override
        void draw(StudioScreen s, Ui ui) {
            frame(ui, title);
            field.draw(ui, "pr:field", x + 8, y + 22, w - 16, 16, hint);
            if (hint != null) {
                ui.text(ui.fit(hint, w - 16), x + 8, y + 42, ui.t.textFaint);
            }
            s.controls.button(ui, "pr:ok", "OK", x + w - 58, y + h - 20, 50, 14, Controls.Style.PRIMARY, null, this::finish);
            s.controls.button(ui, "pr:cancel", "Cancel", x + w - 112, y + h - 20, 50, 14, Controls.Style.GHOST, null, () -> closed = true);
        }

        @Override
        boolean keyPressed(StudioScreen s, KeyEvent e) {
            if (e.key() == GLFW.GLFW_KEY_ESCAPE) {
                closed = true;
                return true;
            }
            field.focused = true;
            return field.keyPressed(e);
        }

        @Override
        boolean charTyped(StudioScreen s, CharacterEvent e) {
            field.focused = true;
            return field.charTyped(e);
        }
    }

    // ================================================================== confirm

    static final class Confirm extends Popup {
        private final String title;
        private final List<String> lines = new ArrayList<>();
        private final String message;
        private final String yes;
        private final Runnable action;

        Confirm(String title, String message, String yes, Runnable action) {
            this.title = title;
            this.message = message;
            this.yes = yes;
            this.action = action;
            w = 230;
            h = 80;
        }

        @Override
        void draw(StudioScreen s, Ui ui) {
            if (lines.isEmpty()) {
                lines.addAll(ui.wrap(message, w - 16));
                h = 44 + lines.size() * 10;
            }
            frame(ui, title);
            for (int i = 0; i < lines.size(); i++) {
                ui.text(lines.get(i), x + 8, y + 20 + i * 10, ui.t.textDim);
            }
            s.controls.button(ui, "cf:yes", yes, x + w - 78, y + h - 20, 70, 14, Controls.Style.DANGER, null, () -> {
                closed = true;
                action.run();
            });
            s.controls.button(ui, "cf:no", "Cancel", x + w - 132, y + h - 20, 50, 14, Controls.Style.GHOST, null, () -> closed = true);
        }

        @Override
        boolean keyPressed(StudioScreen s, KeyEvent e) {
            if (e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_KP_ENTER) {
                closed = true;
                action.run();
                return true;
            }
            return super.keyPressed(s, e);
        }
    }
}
