package dev.betterskies.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** A single-line text box drawn with {@link Ui}. */
public final class TextField {
    public String text = "";
    public int cursor;
    public boolean focused;
    public boolean selectAll;
    public String placeholder = "";
    public int maxLength = 64;
    public Predicate<Character> allowed = c -> c >= 32 && c != 127;
    public Consumer<String> onChange = s -> {
    };
    public Consumer<String> onSubmit = s -> {
    };
    private float blink;

    public void set(String s) {
        text = s == null ? "" : s;
        cursor = text.length();
    }

    public void focus() {
        focused = true;
        selectAll = true;
        cursor = text.length();
    }

    public void draw(Ui ui, String id, int x, int y, int w, int h, String help) {
        boolean hov = ui.hover(x, y, w, h);
        Theme t = ui.t;
        ui.round(x, y, w, h, 3, focused ? 0xFF2C2638 : hov ? t.fieldHover : t.field);
        if (focused) {
            ui.roundOutline(x, y, w, h, 3, t.accent(0.9));
        }
        int ty = y + (h - 8) / 2;
        int inner = w - 8;
        if (text.isEmpty() && !focused) {
            ui.text(ui.fit(placeholder, inner), x + 4, ty, t.textFaint);
        } else {
            // scroll the text so the cursor stays visible
            String before = text.substring(0, Math.min(cursor, text.length()));
            int shift = Math.max(0, ui.font.width(before) - inner + 4);
            ui.pushClip(x + 2, y, x + w - 2, y + h);
            if (selectAll && focused && !text.isEmpty()) {
                ui.rect(x + 4 - shift, ty - 1, ui.font.width(text), 10, t.accent(0.45));
            }
            ui.text(text, x + 4 - shift, ty, t.text);
            if (focused) {
                blink += ui.dt;
                if ((blink % 1.0f) < 0.6f) {
                    ui.rect(x + 4 - shift + ui.font.width(before), ty - 1, 1, 10, t.text);
                }
            }
            ui.popClip();
        }
        if (hov) {
            ui.cursor(com.mojang.blaze3d.platform.cursor.CursorTypes.IBEAM);
        }
        ui.hit(id, x, y, w, h, (b, mx, my, dbl) -> {
            focused = true;
            blink = 0;
            if (dbl) {
                selectAll = true;
                return;
            }
            selectAll = false;
            int rel = (int) mx - x - 4;
            int best = text.length();
            for (int i = 0; i <= text.length(); i++) {
                if (ui.font.width(text.substring(0, i)) >= rel) {
                    best = i;
                    break;
                }
            }
            cursor = best;
        }, null, help);
    }

    public boolean charTyped(CharacterEvent e) {
        if (!focused) {
            return false;
        }
        String s = e.codepointAsString();
        for (char c : s.toCharArray()) {
            if (!allowed.test(c)) {
                return true;
            }
        }
        insert(s);
        return true;
    }

    private void insert(String s) {
        if (selectAll) {
            text = "";
            cursor = 0;
            selectAll = false;
        }
        if (text.length() + s.length() > maxLength) {
            s = s.substring(0, Math.max(0, maxLength - text.length()));
        }
        text = text.substring(0, cursor) + s + text.substring(cursor);
        cursor += s.length();
        blink = 0;
        onChange.accept(text);
    }

    public boolean keyPressed(KeyEvent e) {
        if (!focused) {
            return false;
        }
        int key = e.key();
        boolean ctrl = e.hasControlDownWithQuirk();
        blink = 0;
        if (ctrl && key == GLFW.GLFW_KEY_A) {
            selectAll = true;
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            StringBuilder ok = new StringBuilder();
            for (char c : clip.toCharArray()) {
                if (allowed.test(c)) {
                    ok.append(c);
                }
            }
            insert(ok.toString());
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_C) {
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (selectAll) {
                    text = "";
                    cursor = 0;
                    selectAll = false;
                } else if (cursor > 0) {
                    int from = cursor - 1;
                    if (ctrl) {
                        from = text.lastIndexOf(' ', Math.max(0, cursor - 2)) + 1;
                    }
                    text = text.substring(0, from) + text.substring(cursor);
                    cursor = from;
                }
                onChange.accept(text);
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (selectAll) {
                    text = "";
                    cursor = 0;
                    selectAll = false;
                } else if (cursor < text.length()) {
                    text = text.substring(0, cursor) + text.substring(cursor + 1);
                }
                onChange.accept(text);
            }
            case GLFW.GLFW_KEY_LEFT -> {
                selectAll = false;
                cursor = Math.max(0, cursor - 1);
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                selectAll = false;
                cursor = Math.min(text.length(), cursor + 1);
            }
            case GLFW.GLFW_KEY_HOME -> {
                selectAll = false;
                cursor = 0;
            }
            case GLFW.GLFW_KEY_END -> {
                selectAll = false;
                cursor = text.length();
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                onSubmit.accept(text);
            }
            case GLFW.GLFW_KEY_ESCAPE -> focused = false;
            default -> {
                return key != GLFW.GLFW_KEY_TAB;
            }
        }
        return true;
    }
}
