package dev.betterskies.screen;

import dev.betterskies.BetterSkies;
import dev.betterskies.config.BuiltinSkies;
import dev.betterskies.config.Category;
import dev.betterskies.config.Gradient;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Setting;
import dev.betterskies.config.Settings;
import dev.betterskies.config.Store;
import dev.betterskies.render.SkyColors;
import dev.betterskies.render.SkyRenderer;
import dev.betterskies.render.SkyState;
import dev.betterskies.ui.History;
import dev.betterskies.ui.Icons;
import dev.betterskies.ui.TextField;
import dev.betterskies.ui.Theme;
import dev.betterskies.ui.Ui;
import dev.betterskies.util.Colors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * BetterSkies Studio: the in-game sky editor. Everything is custom-drawn with
 * {@link Ui}; edits apply to the live sky instantly and can be undone.
 */
public final class StudioScreen extends Screen {
    private final Screen parent;
    final Ui ui = new Ui();
    final Controls controls = new Controls(this);
    final Previews previews = new Previews();
    final Pages pages = new Pages(this);
    private final History history = new History();
    private final Profile opening;
    final TextField search = new TextField();

    Category page;
    private float scroll;
    private float scrollTarget;
    private int contentHeight;
    private int viewHeight;
    private Popups.Popup popup;
    private Ui.Drag drag;
    private String dragId;
    private boolean peekHeld;
    private boolean peekMouse;
    private Float originalPitch;
    private int viewMode;
    private final List<Toast> toasts = new ArrayList<>();

    private record Toast(String text, int color, long born) {
    }

    // layout (computed each frame)
    int panelX, panelW, sidebarW, contentX, contentW, headerH = 30, footerH = 38;

    public StudioScreen(Screen parent) {
        super(Component.literal("BetterSkies Studio"));
        this.parent = parent;
        this.opening = BetterSkies.profile().copy();
        try {
            this.page = Category.valueOf(BetterSkies.store().lastCategory);
        } catch (IllegalArgumentException e) {
            this.page = Category.HOME;
        }
        search.placeholder = "Search settings…  (Ctrl+F)";
        search.maxLength = 40;
        search.onChange = s -> {
            scrollTarget = 0;
        };
    }

    /** Opens the Studio on a given page (used by tests and other mods). */
    public static StudioScreen openAt(Category c) {
        StudioScreen s = new StudioScreen(null);
        s.page = c;
        return s;
    }

    /** Opens the Studio with a search already typed (used by tests). */
    public static StudioScreen openSearch(String query) {
        StudioScreen s = new StudioScreen(null);
        s.search.set(query);
        return s;
    }

    /** Opens the Studio with the color picker showing (used by tests). */
    public static StudioScreen openColorPickerDemo() {
        StudioScreen s = openAt(Category.AURORA);
        s.pendingDemoPicker = true;
        return s;
    }

    private boolean pendingDemoPicker;

    // ================================================================== editing API

    Profile p() {
        return BetterSkies.profile();
    }

    /** A one-shot, undoable change to the live sky. */
    void edit(Runnable change) {
        Profile before = p().copy();
        change.run();
        if (!before.equals(p())) {
            history.record(before);
            BetterSkies.changed();
            previews.invalidate();
        }
        click();
    }

    void beginEdit() {
        history.begin(p());
    }

    void endEdit() {
        history.end(p());
        previews.invalidate();
    }

    void cancelEdit() {
        endEdit();
    }

    void startDrag(String id) {
        dragId = id;
    }

    boolean isDragging(String id) {
        return drag != null && id.equals(dragId);
    }

    void click() {
        if (Settings.UI_SOUNDS.on(p()) && minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.35f, 0.25f));
        }
    }

    void toast(String text, int color) {
        toasts.add(new Toast(text, color, System.currentTimeMillis()));
        if (toasts.size() > 4) {
            toasts.remove(0);
        }
    }

    void togglePin(Setting<?> st) {
        click();
        var fav = BetterSkies.store().favorites;
        if (!fav.remove(st.id)) {
            fav.add(st.id);
            toast("Pinned \"" + st.label + "\" to Home", ui.t.good);
        }
    }

    void goTo(Category c) {
        if (page != c) {
            page = c;
            scroll = scrollTarget = 0;
            ui.setAnim("page", 0);
        }
        search.set("");
        search.focused = false;
    }

    /** Replaces the sky's look with another sky (undoable). */
    void loadSky(Profile sky) {
        edit(() -> p().loadLook(sky));
        previews.invalidate();
        toast("Loaded \"" + sky.name + "\"", ui.t.good);
    }

    void openPopup(Popups.Popup pp) {
        popup = pp;
    }

    void pickColor(String title, int rgb, int ax, int ay, IntConsumer apply) {
        click();
        beginEdit();
        Popups.ColorPicker cp = new Popups.ColorPicker(title, rgb, apply);
        cp.place(ax - cp.w + 30, ay, width, height);
        openPopup(cp);
    }

    void dropdown(Setting.Choice ch, int x, int y, int w) {
        Popups.Dropdown d = new Popups.Dropdown(ch, Math.max(w, 110));
        d.place(x, y, width, height);
        openPopup(d);
    }

    void prompt(String title, String hint, String initial, int max, java.util.function.Consumer<String> done) {
        Popups.Prompt pr = new Popups.Prompt(title, hint, initial, max, done);
        pr.place(width / 2 - pr.w / 2, height / 2 - pr.h / 2, width, height);
        openPopup(pr);
    }

    void confirm(String title, String message, String yes, Runnable action) {
        Popups.Confirm c = new Popups.Confirm(title, message, yes, action);
        c.place(width / 2 - c.w / 2, height / 2 - 50, width, height);
        openPopup(c);
    }

    void promptNumber(Setting.Slider sl) {
        Popups.Prompt pr = new Popups.Prompt(sl.label, "Range " + sl.format(sl.min) + " to " + sl.format(sl.max),
                sl.unit == Setting.Unit.TIME_OF_DAY ? sl.display(p()) : sl.display(p()).replaceAll("[^0-9.+:-]", ""), 12, text -> {
            Double v = parseNumber(sl, text);
            if (v == null) {
                toast("That's not a number", ui.t.warn);
                return;
            }
            edit(() -> sl.set(p(), v));
        }).numeric();
        pr.place(width / 2 - pr.w / 2, height / 2 - pr.h / 2, width, height);
        openPopup(pr);
    }

    private static Double parseNumber(Setting.Slider sl, String text) {
        try {
            String t = text.trim().replace("%", "").replace("+", "");
            if (sl.unit == Setting.Unit.TIME_OF_DAY && t.contains(":")) {
                String[] hm = t.split(":");
                double minutes = Integer.parseInt(hm[0]) * 60 + Integer.parseInt(hm[1]);
                return ((minutes / 60.0 - 6 + 24) % 24) * 1000;
            }
            double v = Double.parseDouble(t);
            if (sl.unit == Setting.Unit.PERCENT || sl.unit == Setting.Unit.SIGNED_PERCENT) {
                v /= 100;
            }
            return v;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ================================================================== screen lifecycle

    @Override
    protected void init() {
        if (minecraft != null && minecraft.player != null && originalPitch == null) {
            originalPitch = minecraft.player.getXRot();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void removed() {
        if (history.editing()) {
            history.end(p());
        }
        BetterSkies.setPreviewTime(-1);
        pages.close();
        BetterSkies.store().lastCategory = page.name();
        BetterSkies.save();
        if (minecraft != null && minecraft.player != null && originalPitch != null && viewMode != 0) {
            minecraft.player.setXRot(originalPitch);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (minecraft == null || minecraft.level == null) {
            // no world: paint the current sky as a backdrop
            Profile p = p();
            int tick = BetterSkies.previewTime() >= 0 ? BetterSkies.previewTime() : 13000;
            SkyState s = new SkyState(p, tick, 0, 0);
            dev.betterskies.config.Grade grade = new dev.betterskies.config.Grade(p);
            int rows = 64;
            for (int i = 0; i < rows; i++) {
                double el0 = 80 - 95.0 * i / rows, el1 = 80 - 95.0 * (i + 1) / rows;
                int c0 = 0xFF000000 | grade.apply(SkyColors.colorAtElevation(p, s, el0));
                int c1 = 0xFF000000 | grade.apply(SkyColors.colorAtElevation(p, s, el1));
                g.fillGradient(0, height * i / rows, width, height * (i + 1) / rows, c0, c1);
            }
        }
    }

    // ================================================================== rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Profile p = p();
        Theme t = new Theme(p);
        ui.popupOpen = popup != null;
        if (popup != null) {
            ui.popupX1 = popup.x;
            ui.popupY1 = popup.y;
            ui.popupX2 = popup.x + popup.w;
            ui.popupY2 = popup.y + popup.h;
        }
        ui.begin(g, font, mouseX, mouseY, t, Settings.ANIMATIONS.on(p));
        if (pendingDemoPicker) {
            pendingDemoPicker = false;
            pickColor(Settings.AURORA_COLOR.label, Settings.AURORA_COLOR.rgb(p), width / 2, 60, v -> {
            });
        }

        float open = ui.anim("open", 1, 9);
        if (open < 0.02f) {
            ui.setAnim("open", 0.02f);
        }
        float peek = ui.anim("peek", peekHeld || peekMouse ? 1 : 0, 12);

        panelW = width < 400 ? width : Math.max(380, Math.min(600, (int) (width * 0.62)));
        sidebarW = panelW >= 470 ? 112 : 30;
        panelX = Math.round(-(panelW + 12) * peek - (1 - open) * 40);
        contentX = panelX + sidebarW;
        contentW = panelW - sidebarW;
        boolean helpBar = Settings.HELP_BAR.on(p);
        footerH = helpBar ? 38 : 26;

        // panel body
        int alphaMul = (int) (255 * Math.min(1, open * 1.4f));
        ui.rect(panelX, 0, panelW, height, Colors.argb(Colors.a(t.panel) * alphaMul / 255, t.panel));
        ui.rect(panelX + panelW, 0, 1, height, t.panelEdge);
        ui.gradientH(panelX + panelW + 1, 0, 10, height, 0x50000000, 0x00000000);
        // soft accent glow in the top-left corner
        ui.gradientV(panelX, 0, panelW, 60, t.accent(0.10), 0x00000000);

        drawHeader(p, t);
        drawSidebar(p, t);
        drawContent(p, t);
        drawFooter(p, t, helpBar);

        // peek hint when hidden
        if (peek > 0.5f) {
            ui.round(6, height - 22, 150, 16, 4, 0xB0000000);
            ui.text("Release Tab to show the editor", 12, height - 18, t.textDim);
        }
        drawToasts(t);

        if (popup != null) {
            ui.layer = 1;
            popup.draw(this, ui);
            ui.layer = 0;
            if (popup.closed) {
                popup = null;
            }
        }
        ui.end();
    }

    private void drawHeader(Profile p, Theme t) {
        int x = panelX, y = 0, w = panelW, h = headerH;
        ui.rect(x, y + h - 1, w, 1, 0x20FFFFFF);
        // logo
        float spin = (System.currentTimeMillis() % 6000) / 6000f;
        int logoC = Colors.mixArgb(Colors.opaque(t.accent), Colors.opaque(t.accent2), 0.5 + 0.5 * Math.sin(spin * Math.PI * 2));
        Icons.drawScaled(ui, "SPARKLE", x + 7, y + 8, 2, logoC);
        ui.text("BetterSkies", x + 25, y + 7, t.text);
        ui.text("Studio", x + 25, y + 17, Colors.opaque(t.accent2));
        int right = x + w - 6;
        // master switch
        boolean on = Settings.ENABLED.on(p);
        int pw = 44;
        int px = right - pw;
        boolean ph = ui.hover(px, y + 8, pw, 14);
        float pa = ui.anim("power", on ? 1 : 0, 14);
        ui.round(px, y + 8, pw, 14, 7, Colors.mixArgb(0xFF3A2F3F, Colors.argbf(1, Colors.mix(t.good, 0x000000, 0.1)), pa));
        ui.circle(px + 7 + Math.round(pa * (pw - 14)), y + 15, 4, 0xFFFFFFFF);
        ui.text(on ? "ON" : "OFF", on ? px + 7 : px + pw - 22, y + 11, on ? 0xFF0F1A12 : t.textDim);
        if (ph) {
            ui.roundOutline(px, y + 8, pw, 14, 7, 0x60FFFFFF);
        }
        ui.hit("power", px, y + 8, pw, 14, () -> edit(() -> Settings.ENABLED.flip(p())),
                "Turn the BetterSkies sky on or off (your settings are kept)");
        right = px - 8;
        // undo / redo
        controls.icon(ui, "redo", "REDO", right - 16, y + 7, 16, history.redoCount() > 0, "Redo (Ctrl+Y)", this::redo);
        controls.icon(ui, "undo", "UNDO", right - 34, y + 7, 16, history.undoCount() > 0, "Undo (Ctrl+Z) · " + history.undoCount() + " steps", this::undo);
        right -= 40;
        // peek
        controls.icon(ui, "peek", "EYE", right - 16, y + 7, 16, true, "Hold Tab (or hold this button) to hide the editor and look at your sky", () -> {
            peekMouse = true;
        });
        right -= 22;
        // search
        int sw = Math.min(170, right - (x + 92));
        if (sw > 60) {
            search.draw(ui, "search", right - sw, y + 7, sw, 16, "Find any setting by name or description");
            if (search.text.isEmpty() && !search.focused) {
                Icons.draw(ui, "SEARCH", right - 12, y + 12, t.textFaint);
            }
        }
    }

    private void drawSidebar(Profile p, Theme t) {
        int x = panelX, y = headerH, w = sidebarW, h = height - headerH - footerH;
        ui.rect(x + w - 1, y, 1, h, 0x14FFFFFF);
        boolean compact = w < 60;
        int reserve = compact ? 26 : 38;
        int count = Category.values().length;
        int itemH = Math.max(13, Math.min(compact ? 20 : 19, (h - reserve - 8) / count));
        int iy = y + 4;
        boolean searching = !search.text.isBlank();
        for (Category c : Category.values()) {
            boolean sel = !searching && c == page;
            boolean hov = ui.hover(x + 4, iy, w - 8, itemH - 1);
            float a = ui.anim("side:" + c, sel ? 1 : hov ? 0.4f : 0, 14);
            if (a > 0.01f) {
                ui.round(x + 4, iy, w - 8, itemH - 1, 4, Colors.argbf(0.18 * a, sel ? t.accent : 0xFFFFFF));
            }
            if (sel) {
                float bar = ui.anim("sidebar-y", iy, 18);
                ui.round(x + 2, Math.round(bar) + 3, 3, itemH - 7, 1, Colors.opaque(t.accent));
            }
            int gc = sel ? Colors.opaque(t.accent2) : hov ? t.text : t.textDim;
            int ty = iy + (itemH - 1 - 8) / 2;
            Icons.draw(ui, c.name(), x + 9, iy + (itemH - 1 - Icons.height(c.name())) / 2, gc);
            if (!compact) {
                ui.text(ui.fit(c.title, w - 34), x + 25, ty, sel ? t.text : hov ? t.text : t.textDim);
            }
            ui.hit("side:" + c, x + 4, iy, w - 8, itemH - 1, () -> {
                click();
                goTo(c);
            }, c.title + ": " + c.blurb);
            iy += itemH;
        }
        // advanced mode switch at the bottom
        int by = y + h - (compact ? 22 : 34);
        if (!compact) {
            ui.text("Advanced", x + 10, by + 3, t.textDim);
            controls.toggle(ui, Settings.ADVANCED, x + w - 34, by + 1, true, Settings.ADVANCED.help);
            ui.text("v5.0", x + 10, by + 18, t.textFaint);
            ui.text("F12", x + w - 26, by + 18, t.textFaint);
        } else {
            controls.icon(ui, "adv", "GRADE", x + 7, by, 16, true, "Advanced settings: " + (Settings.ADVANCED.on(p) ? "on" : "off"),
                    () -> edit(() -> Settings.ADVANCED.flip(p())));
        }
    }

    private void drawContent(Profile p, Theme t) {
        int top = headerH, bottom = height - footerH;
        viewHeight = bottom - top;
        int maxScroll = Math.max(0, contentHeight - viewHeight + 12);
        scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
        scroll = ui.animations ? scroll + (scrollTarget - scroll) * Math.min(1, ui.dt * 14) : scrollTarget;
        float enter = ui.anim("page", 1, 10);
        int x = contentX + 10 + Math.round((1 - enter) * 8);
        int w = contentW - 20;
        ui.pushClip(contentX, top, contentX + contentW, bottom);
        int y0 = top + 8 - Math.round(scroll);
        int y = y0;
        if (!search.text.isBlank()) {
            y = pages.search(x, y, w, search.text);
        } else {
            y = pages.draw(page, x, y, w);
        }
        contentHeight = y - y0;
        ui.popClip();
        // scrollbar
        if (contentHeight > viewHeight) {
            int trackX = contentX + contentW - 4;
            int barH = Math.max(20, viewHeight * viewHeight / contentHeight);
            int barY = top + (int) ((viewHeight - barH) * (scroll / Math.max(1, contentHeight - viewHeight + 12)));
            ui.round(trackX, barY, 2, barH, 1, 0x50FFFFFF);
        }
        // fade edges
        ui.gradientV(contentX, top, contentW - 6, 6, Colors.argb(Colors.a(t.panel) / 2, t.panel), 0x00000000);
    }

    private void drawFooter(Profile p, Theme t, boolean helpBar) {
        int x = panelX, w = panelW, y = height - footerH;
        ui.rect(x, y, w, 1, 0x20FFFFFF);
        int rowY = y + 6;
        // time scrubber
        int tx = x + 8;
        boolean live = BetterSkies.previewTime() < 0;
        long worldTime = minecraft != null && minecraft.level != null ? minecraft.level.getDayTime() : 6000;
        int tick = live ? (int) Math.floorMod(worldTime, 24000L) : BetterSkies.previewTime();
        boolean night = tick > 12800 && tick < 23200;
        ui.text(night ? "☾" : "☀", tx, rowY + 4, Colors.opaque(night ? 0xB9C8FF : 0xFFD27A));
        ui.text(Setting.Slider.clock(tick), tx + 10, rowY + 4, t.text);
        boolean views = minecraft != null && minecraft.player != null;
        int lx = tx + 42, lw = Math.max(70, Math.min(170, w - 162 - 46 - (views ? 56 : 0)));
        ui.round(lx - 1, rowY + 1, lw + 2, 14, 3, 0x60000000);
        previews.timeline(ui, p, BetterSkies.revision(), lx, rowY + 2, lw, 12);
        int mxp = lx + (int) Math.round(tick / 24000.0 * lw);
        ui.rect(mxp - 1, rowY - 1, 3, 18, 0xFFFFFFFF);
        ui.rect(mxp, rowY, 1, 16, Colors.opaque(t.accent));
        ui.hit("time", lx, rowY, lw, 16, (b, mx, my, d) -> {
            setPreview(mx, lx, lw);
        }, new Ui.Drag() {
            @Override
            public void drag(double mx, double my) {
                setPreview(mx, lx, lw);
            }
        }, "Drag to preview your sky at any time of day (the world's real time isn't changed)");
        controls.chip(ui, "live", live ? "Live" : "Live ↺", lx + lw + 5, rowY + 1, 40, 14, live,
                "Show the world's real time again", () -> BetterSkies.setPreviewTime(-1));
        // view buttons
        int vx = lx + lw + 50;
        if (views && vx + 3 * 17 <= x + w - 112) {
            String[] names = {"VIEW_HORIZON", "VIEW_SKY", "VIEW_UP"};
            String[] tips = {"Look at the horizon", "Look up at the sky", "Look straight up"};
            for (int i = 0; i < 3; i++) {
                int vi = i;
                controls.icon(ui, "view" + i, names[i], vx + i * 17, rowY, 16, true, tips[i], () -> setView(vi));
            }
        }
        // revert / done
        boolean dirty = !opening.sameLook(p);
        controls.button(ui, "done", "Done", x + w - 56, rowY, 48, 16, Controls.Style.PRIMARY, "Save and close (Esc)", this::onClose);
        controls.button(ui, "revert", "Revert", x + w - 108, rowY, 48, 16, Controls.Style.GHOST, dirty,
                "Undo everything since you opened the editor", () -> confirm("Revert changes?",
                        "Go back to the sky you had when you opened BetterSkies Studio?", "Revert", () -> {
                            Profile before = p().copy();
                            p().loadLook(opening);
                            history.record(before);
                            BetterSkies.changed();
                            previews.invalidate();
                            toast("Reverted", ui.t.good);
                        }));
        // help bar
        if (helpBar) {
            String status = SkyRenderer.status();
            String text = ui.help != null ? ui.help : !status.isEmpty() ? status
                    : minecraft != null && minecraft.level == null ? "Join a world to see your sky live (you can still edit everything here)."
                    : "Tip: hold Tab to peek at the sky · Ctrl+Z undo · drop pictures on this window to add sky images";
            ui.text(ui.fit(text, w - 16), x + 8, y + 26, ui.help != null ? t.textDim : t.textFaint);
        }
    }

    private void setPreview(double mx, int lx, int lw) {
        int tick = (int) Math.round(Math.max(0, Math.min(1, (mx - lx) / lw)) * 23999);
        BetterSkies.setPreviewTime(tick);
    }

    void previewAt(int tick) {
        BetterSkies.setPreviewTime(tick);
    }

    private void setView(int mode) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        viewMode = mode;
        float pitch = switch (mode) {
            case 1 -> -40f;
            case 2 -> -89f;
            default -> originalPitch != null && Math.abs(originalPitch) < 30 ? originalPitch : 0f;
        };
        minecraft.player.setXRot(pitch);
    }

    private void drawToasts(Theme t) {
        long now = System.currentTimeMillis();
        int y = headerH + 6;
        Iterator<Toast> it = toasts.iterator();
        while (it.hasNext()) {
            Toast to = it.next();
            long age = now - to.born();
            if (age > 2800) {
                it.remove();
                continue;
            }
            float a = Math.min(1, Math.min(age / 150f, (2800 - age) / 400f));
            int tw = ui.font.width(to.text()) + 20;
            int x = panelX + panelW + 10 + Math.round((1 - a) * 20);
            if (x + tw > width - 4) {
                x = width - tw - 4;
            }
            ui.round(x, y, tw, 16, 4, Colors.argbf(0.88 * a, 0x120F18));
            ui.rect(x, y + 3, 2, 10, Colors.argbf(a, to.color()));
            ui.text(to.text(), x + 10, y + 4, Colors.argbf(Math.max(0.1, a), 0xF3EFFA));
            y += 20;
        }
    }

    // ================================================================== undo

    void undo() {
        Profile prev = history.undo(p());
        if (prev != null) {
            BetterSkies.setProfile(prev);
            previews.invalidate();
        }
    }

    void redo() {
        Profile next = history.redo(p());
        if (next != null) {
            BetterSkies.setProfile(next);
            previews.invalidate();
        }
    }

    // ================================================================== input

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x(), my = e.y();
        if (popup != null && !(mx >= popup.x && mx < popup.x + popup.w && my >= popup.y && my < popup.y + popup.h)) {
            popup.close(this);
            popup = null;
            return true;
        }
        Ui.Hit h = ui.find(mx, my);
        if (h == null || !"search".equals(h.id())) {
            search.focused = false;
        }
        if (h != null) {
            if (h.drag() != null && e.button() == 0) {
                drag = h.drag();
                dragId = h.id();
            }
            Popups.Popup before = popup;
            h.click().click(e.button(), mx, my, doubleClick);
            if (popup != null && popup != before) {
                // the click opened a popup: it owns the edit from here, so no drag
                drag = null;
                dragId = null;
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (drag != null) {
            drag.drag(e.x(), e.y());
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        peekMouse = false;
        if (drag != null) {
            Ui.Drag d = drag;
            drag = null;
            d.release();
            dragId = null;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (popup != null && popup.scrolled(sy)) {
            return true;
        }
        if (mx >= contentX && mx < contentX + contentW) {
            scrollTarget -= (float) (sy * 28);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        if (popup != null) {
            popup.keyPressed(this, e);
            if (popup != null && popup.closed) {
                popup = null;
            }
            return true;
        }
        if (search.focused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (!search.text.isEmpty()) {
                    search.set("");
                } else {
                    search.focused = false;
                }
                return true;
            }
            return search.keyPressed(e);
        }
        boolean ctrl = e.hasControlDownWithQuirk();
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            if (e.hasShiftDown()) {
                redo();
            } else {
                undo();
            }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_F) {
            search.focus();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_S) {
            BetterSkies.save();
            toast("Saved", ui.t.good);
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            peekHeld = true;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || (BetterSkies.openKey() != null && key == GLFW.GLFW_KEY_F12)) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            Category[] all = Category.values();
            int i = (page.ordinal() + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1) + all.length) % all.length;
            goTo(all[i]);
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean keyReleased(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_TAB) {
            peekHeld = false;
            return true;
        }
        return super.keyReleased(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (popup != null) {
            return popup.charTyped(this, e);
        }
        if (search.focused) {
            return search.charTyped(e);
        }
        return false;
    }

    @Override
    public void onFilesDrop(List<Path> files) {
        List<Path> pictures = new ArrayList<>();
        int skies = 0;
        for (Path f : files) {
            String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
            if (n.endsWith(".json")) {
                try {
                    Profile sky = Profile.fromJsonString(java.nio.file.Files.readString(f));
                    String name = sky.name == null || sky.name.isBlank() ? n.replace(".json", "") : sky.name;
                    BetterSkies.store().saveSky(name, sky);
                    skies++;
                } catch (Exception ex) {
                    toast("Couldn't read " + f.getFileName(), ui.t.bad);
                }
            } else {
                pictures.add(f);
            }
        }
        if (!pictures.isEmpty()) {
            toast(BetterSkies.images().importFiles(pictures), ui.t.good);
            SkyRenderer.imagesChanged();
            pages.refreshImages();
            goTo(Category.IMAGES);
        }
        if (skies > 0) {
            toast("Added " + skies + " sky file" + (skies == 1 ? "" : "s") + " to My Skies", ui.t.good);
            pages.refreshSaved();
            goTo(Category.LIBRARY);
        }
    }

    /** Opens a folder in the system file browser. */
    void openFolder(Path dir) {
        try {
            Util.getPlatform().openPath(dir);
        } catch (Exception e) {
            toast("Folder: " + dir, ui.t.textDim);
        }
    }

    void copyShareCode() {
        String code = Store.shareCode(p());
        minecraft.keyboardHandler.setClipboard(code);
        toast("Share code copied (" + code.length() + " characters)", ui.t.good);
    }

    void pasteShareCode() {
        Profile sky = Store.fromShareCode(minecraft.keyboardHandler.getClipboard());
        if (sky == null) {
            toast("The clipboard doesn't hold a BetterSkies code", ui.t.warn);
            return;
        }
        loadSky(sky);
    }

    /** Builds a random but harmonious sky. */
    void surprise() {
        java.util.Random r = new java.util.Random();
        double h = r.nextDouble();
        double h2 = h + (r.nextBoolean() ? 0.08 : -0.08) + (r.nextDouble() - 0.5) * 0.1;
        Profile sky = new Profile();
        sky.name = "Surprise " + (100 + r.nextInt(900));
        Settings.GRAD_DAY.set(sky, Gradient.of(Colors.hsv(h, 0.22, 1), Colors.hsv(h, 0.45, 0.95), Colors.hsv(h + 0.03, 0.7, 0.78)));
        Settings.GRAD_DUSK.set(sky, Gradient.at(0.0, Colors.hsv(h2 - 0.55, 0.6, 1), 0.3, Colors.hsv(h2 - 0.65, 0.55, 0.95),
                0.65, Colors.hsv(h2 + 0.05, 0.6, 0.6), 1.0, Colors.hsv(h2 + 0.08, 0.7, 0.25)));
        Settings.GRAD_NIGHT.set(sky, Gradient.at(0.0, Colors.hsv(h2 + 0.05, 0.6, 0.3), 0.5, Colors.hsv(h2 + 0.07, 0.7, 0.13), 1.0, Colors.hsv(h2 + 0.1, 0.8, 0.04)));
        Settings.NEBULA.set(sky, r.nextDouble() < 0.5);
        Settings.NEB_COLOR.set(sky, Colors.hsv(h2 + 0.1, 0.6, 1));
        Settings.NEB_COLOR2.set(sky, Colors.hsv(h2 - 0.15, 0.5, 1));
        Settings.AURORA.set(sky, r.nextDouble() < 0.4);
        Settings.AURORA_COLOR.set(sky, Colors.hsv(h + 0.35, 0.7, 1));
        Settings.AURORA_COLOR2.set(sky, Colors.hsv(h + 0.6, 0.6, 1));
        Settings.COMETS.set(sky, r.nextDouble() < 0.3);
        Settings.DUST.set(sky, r.nextDouble() < 0.3);
        Settings.STAR_COLORS.set(sky, r.nextBoolean() ? "REALISTIC" : "TINTED");
        Settings.STAR_COLOR2.set(sky, Colors.hsv(h2, 0.4, 1));
        Settings.MOON_COLOR.set(sky, Colors.hsv(h + 0.5, 0.15, 1));
        Settings.STAR_SEED.set(sky, (double) (1 + r.nextInt(999)));
        loadSky(sky);
    }

    /** Groups settings of a category by section, keeping declaration order. */
    static Map<String, List<Setting<?>>> sections(Category c) {
        Map<String, List<Setting<?>>> out = new LinkedHashMap<>();
        for (Setting<?> s : Settings.in(c)) {
            out.computeIfAbsent(s.section, k -> new ArrayList<>()).add(s);
        }
        return out;
    }
}
