package dev.betterskies.screen;

import dev.betterskies.BetterSkies;
import dev.betterskies.config.BuiltinSkies;
import dev.betterskies.config.Category;
import dev.betterskies.config.Gradient;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Setting;
import dev.betterskies.config.Settings;
import dev.betterskies.config.Store;
import dev.betterskies.render.Gpu;
import dev.betterskies.render.ImageLibrary;
import dev.betterskies.render.SkyRenderer;
import dev.betterskies.ui.Icons;
import dev.betterskies.ui.Theme;
import dev.betterskies.ui.Ui;
import dev.betterskies.util.Colors;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** What each editor page shows. */
final class Pages {
    private final StudioScreen s;
    private final Map<String, Integer> cardHeights = new HashMap<>();
    private List<Store.Saved> saved;
    private List<ImageLibrary.Entry> images;
    private final Map<String, Gpu.Tex> thumbs = new HashMap<>();
    private final Map<String, CompletableFuture<int[]>> thumbJobs = new HashMap<>();
    private int libraryTab;
    private String keyframe = "grad_day";

    Pages(StudioScreen s) {
        this.s = s;
    }

    private Ui ui() {
        return s.ui;
    }

    private Profile p() {
        return BetterSkies.profile();
    }

    void refreshSaved() {
        saved = null;
    }

    void refreshImages() {
        images = null;
    }

    // ================================================================== frame pieces

    private int pageHeader(Category c, int x, int y, int w) {
        Ui ui = ui();
        Theme t = ui.t;
        Icons.drawScaled(ui, c.name(), x, y + 2, 2, Colors.opaque(t.accent2));
        ui.textScaled(c.title, x + 24, y + 1, 1.35f, t.text, false);
        ui.text(ui.fit(c.blurb, w - 96), x + 24, y + 15, t.textDim);
        boolean hasSettings = c != Category.HOME && c != Category.LIBRARY && !Settings.in(c).isEmpty();
        if (hasSettings) {
            s.controls.button(ui, "resetpage", "Reset page", x + w - 66, y + 2, 66, 14, Controls.Style.GHOST,
                    "Put every setting on this page back to its default", () -> s.confirm("Reset " + c.title + "?",
                            "Every setting on the " + c.title + " page goes back to its default. You can undo this.",
                            "Reset", () -> s.edit(() -> {
                                for (Setting<?> st : Settings.in(c)) {
                                    st.reset(p());
                                }
                            })));
        }
        return y + 32;
    }

    /** Draws a card background using last frame's height, runs the body, remembers the new height. */
    private int card(String id, String title, int x, int y, int w, java.util.function.IntUnaryOperator body) {
        Ui ui = ui();
        Theme t = ui.t;
        int h = cardHeights.getOrDefault(id, 40);
        ui.round(x, y, w, h, 6, t.card);
        ui.roundOutline(x, y, w, h, 6, t.cardEdge);
        int top = y + 6;
        if (title != null) {
            ui.round(x + 7, y + 7, 2, 8, 1, Colors.opaque(t.accent));
            ui.text(title, x + 13, y + 7, t.text);
            top = y + 20;
        }
        int end = body.applyAsInt(top);
        int newH = end - y + 6;
        cardHeights.put(id, newH);
        return y + newH + 8;
    }

    private boolean visible(Setting<?> st) {
        return !st.advanced() || Settings.ADVANCED.on(p());
    }

    /** A card listing settings as rows; notes how many advanced ones are hidden. */
    private int settingsCard(String id, String title, List<Setting<?>> list, int x, int y, int w) {
        return card(id, title, x, y, w, top -> {
            int yy = top;
            int hidden = 0;
            for (Setting<?> st : list) {
                if (!visible(st)) {
                    hidden++;
                    continue;
                }
                yy += s.controls.row(ui(), st, x + 4, yy, w - 8);
            }
            if (hidden > 0) {
                Ui ui = ui();
                String msg = "+ " + hidden + " more in Advanced mode";
                boolean hov = ui.hover(x + 8, yy + 2, ui.font.width(msg), 10);
                ui.text(msg, x + 10, yy + 3, hov ? ui.t.accentText() : ui.t.textFaint);
                ui.hit("adv:" + id, x + 8, yy + 1, ui.font.width(msg) + 4, 11, () -> s.edit(() -> Settings.ADVANCED.set(p(), true)),
                        "Show advanced settings everywhere");
                yy += 13;
            }
            return yy;
        });
    }

    private int genericSections(Category c, int x, int y, int w, java.util.function.Predicate<Setting<?>> include) {
        for (Map.Entry<String, List<Setting<?>>> e : StudioScreen.sections(c).entrySet()) {
            List<Setting<?>> list = new ArrayList<>();
            for (Setting<?> st : e.getValue()) {
                if (include.test(st)) {
                    list.add(st);
                }
            }
            if (!list.isEmpty()) {
                y = settingsCard(c + ":" + e.getKey(), e.getKey(), list, x, y, w);
            }
        }
        return y;
    }

    // ================================================================== dispatch

    int draw(Category c, int x, int y, int w) {
        y = pageHeader(c, x, y, w);
        return switch (c) {
            case HOME -> home(x, y, w);
            case LIBRARY -> library(x, y, w);
            case COLORS -> colors(x, y, w);
            case IMAGES -> images(x, y, w);
            case GRADE -> grade(x, y, w);
            case ATMOSPHERE -> atmosphere(x, y, w);
            case STUDIO -> studio(x, y, w);
            default -> genericSections(c, x, y, w, st -> true);
        };
    }

    int search(int x, int y, int w, String query) {
        Ui ui = ui();
        Theme t = ui.t;
        ui.textScaled("Search", x, y + 1, 1.35f, t.text, false);
        ui.text("Results for \"" + query.trim() + "\"", x, y + 15, t.textDim);
        y += 32;
        int found = 0;
        for (Category c : Category.values()) {
            List<Setting<?>> hits = new ArrayList<>();
            for (Setting<?> st : Settings.in(c)) {
                if (st.matches(query)) {
                    hits.add(st);
                }
            }
            boolean pageMatch = c.title.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
            if (hits.isEmpty() && !pageMatch) {
                continue;
            }
            found += hits.size() + (pageMatch ? 1 : 0);
            final Category cat = c;
            y = card("search:" + c, c.title, x, y, w, top -> {
                int yy = top;
                for (Setting<?> st : hits) {
                    yy += s.controls.row(ui, st, x + 4, yy, w - 8);
                }
                s.controls.button(ui, "goto:" + cat, "Open " + cat.title + " →", x + 8, yy + 2, 110, 14, Controls.Style.GHOST,
                        "Go to the " + cat.title + " page", () -> s.goTo(cat));
                return yy + 18;
            });
        }
        if (found == 0) {
            ui.text("Nothing matches. Try a shorter word, like \"star\" or \"fog\".", x + 4, y + 4, t.textDim);
            y += 20;
        }
        return y;
    }

    // ================================================================== home

    private int home(int x, int y, int w) {
        Ui ui = ui();
        Theme t = ui.t;
        Profile p = p();
        // hero
        y = card("home:hero", null, x, y, w, top -> {
            int pw = Math.min(170, w / 2 - 10), ph = 74;
            int px = x + 8, py = top + 2;
            ui.round(px - 1, py - 1, pw + 2, ph + 2, 4, 0x60000000);
            ui.pushClip(px, py, px + pw, py + ph);
            s.previews.sky(ui, p, px, py, pw, ph);
            ui.popClip();
            int rx = px + pw + 12, rw = x + w - 8 - rx;
            ui.text("Current sky", rx, py, t.textFaint);
            String name = p.name == null || p.name.isBlank() ? "My Sky" : p.name;
            boolean nh = ui.hover(rx, py + 10, Math.min(rw, ui.font.width(name) * 3 / 2 + 12), 14);
            ui.textScaled(ui.fit(name, (int) (rw / 1.5f)), rx, py + 11, 1.5f, nh ? t.accentText() : t.text, false);
            ui.hit("rename", rx, py + 10, Math.min(rw, ui.font.width(name) * 3 / 2 + 12), 14,
                    () -> s.prompt("Name this sky", null, name, 40, v -> s.edit(() -> p().name = Store.cleanName(v))), "Click to rename this sky");
            String state = Settings.ENABLED.on(p) ? "Showing in game" : "Off (vanilla sky)";
            ui.text(state, rx, py + 28, Settings.ENABLED.on(p) ? t.good : t.warn);
            int bw = (rw - 4) / 2;
            s.controls.button(ui, "h:browse", "Browse skies", rx, py + 42, bw, 14, Controls.Style.PRIMARY,
                    "Pick one of the built-in skies or your saved skies", () -> s.goTo(Category.LIBRARY));
            s.controls.button(ui, "h:save", "Save sky…", rx + bw + 4, py + 42, bw, 14, Controls.Style.NORMAL,
                    "Save this sky to My Skies", this::saveAs);
            s.controls.button(ui, "h:surprise", "Surprise me", rx, py + 60, bw, 14, Controls.Style.NORMAL,
                    "Make a random sky with matching colors (undo with Ctrl+Z)", s::surprise);
            s.controls.button(ui, "h:share", "Copy code", rx + bw + 4, py + 60, bw, 14, Controls.Style.NORMAL,
                    "Copy a share code for this sky to the clipboard", s::copyShareCode);
            return py + ph + 4;
        });
        // quick switches
        List<Setting.Toggle> quick = List.of(Settings.DAY_CYCLE, Settings.STARS, Settings.MILKY_WAY, Settings.NEBULA,
                Settings.AURORA, Settings.METEORS, Settings.SHOWERS, Settings.COMETS, Settings.DUST, Settings.SKY_CLOUDS, Settings.CLOUDS,
                Settings.MOON_HALO, Settings.EFFECTS);
        y = card("home:quick", "Quick switches", x, y, w, top -> {
            int cols = w >= 330 ? 3 : 2;
            int tw = (w - 16 - (cols - 1) * 4) / cols;
            for (int i = 0; i < quick.size(); i++) {
                Setting.Toggle tg = quick.get(i);
                int tx = x + 8 + (i % cols) * (tw + 4), ty = top + (i / cols) * 22;
                boolean on = tg.on(p);
                boolean hov = ui.hover(tx, ty, tw, 19);
                float a = ui.anim("qt:" + tg.id, on ? 1 : 0, 14);
                ui.round(tx, ty, tw, 19, 4, Colors.mixArgb(hov ? t.fieldHover : t.field, Colors.argbf(0.3, t.accent), a));
                ui.text(ui.fit(tg.label, tw - 34), tx + 6, ty + 6, on ? t.text : t.textDim);
                s.controls.toggle(ui, tg, tx + tw - 28, ty + 4, true, tg.help);
                ui.hit("qtile:" + tg.id, tx, ty, tw - 32, 19, () -> s.edit(() -> tg.flip(p())), tg.help);
            }
            return top + ((quick.size() + cols - 1) / cols) * 22;
        });
        // pinned
        List<Setting<?>> pinned = new ArrayList<>();
        for (String id : BetterSkies.store().favorites) {
            Setting<?> st = Settings.byId(id);
            if (st != null) {
                pinned.add(st);
            }
        }
        y = card("home:pinned", "Pinned", x, y, w, top -> {
            if (pinned.isEmpty()) {
                ui.text("Hover any setting and click ☆ to pin it here.", x + 10, top + 2, t.textFaint);
                return top + 14;
            }
            int yy = top;
            for (Setting<?> st : pinned) {
                yy += s.controls.row(ui, st, x + 4, yy, w - 8);
            }
            return yy;
        });
        // sharing
        y = card("home:share", "Share & files", x, y, w, top -> {
            int bw = (w - 24) / 3;
            s.controls.button(ui, "h:copy", "Copy share code", x + 8, top, bw, 16, Controls.Style.NORMAL,
                    "Copy this sky as text to send to friends", s::copyShareCode);
            s.controls.button(ui, "h:paste", "Paste share code", x + 12 + bw, top, bw, 16, Controls.Style.NORMAL,
                    "Load a sky from a share code on your clipboard", s::pasteShareCode);
            s.controls.button(ui, "h:folder", "Open folder", x + 16 + 2 * bw, top, bw, 16, Controls.Style.NORMAL,
                    "Open config/betterskies (saved skies, images, backups)", () -> s.openFolder(BetterSkies.store().root()));
            return top + 18;
        });
        String status = SkyRenderer.status();
        ui.text(status.isEmpty() ? String.format(Locale.ROOT, "Sky render time: %.2f ms", SkyRenderer.lastFrameMillis()) : status,
                x + 4, y, t.textFaint);
        return y + 14;
    }

    private void saveAs() {
        String suggestion = p().name == null || p().name.isBlank() ? "My Sky" : p().name;
        s.prompt("Save sky as", "Saved to My Skies (config/betterskies/skies)", suggestion, 40, name -> {
            Runnable doSave = () -> {
                try {
                    String used = BetterSkies.store().saveSky(name, p());
                    p().name = used;
                    BetterSkies.save();
                    refreshSaved();
                    s.toast("Saved \"" + used + "\"", ui().t.good);
                } catch (Exception e) {
                    s.toast("Couldn't save: " + e.getMessage(), ui().t.bad);
                }
            };
            if (BetterSkies.store().skyExists(name)) {
                s.confirm("Replace \"" + name + "\"?", "A saved sky with this name already exists.", "Replace", doSave);
            } else {
                doSave.run();
            }
        });
    }

    // ================================================================== library

    private int library(int x, int y, int w) {
        Ui ui = ui();
        Theme t = ui.t;
        if (saved == null) {
            saved = BetterSkies.store().savedSkies();
        }
        List<BuiltinSkies.Entry> builtin = BuiltinSkies.all();
        s.controls.chip(ui, "lib:0", "Built-in (" + builtin.size() + ")", x, y, 96, 16, libraryTab == 0, "Skies that come with BetterSkies", () -> libraryTab = 0);
        s.controls.chip(ui, "lib:1", "My skies (" + saved.size() + ")", x + 100, y, 96, 16, libraryTab == 1, "Skies you saved", () -> {
            libraryTab = 1;
            refreshSaved();
        });
        y += 24;
        int cols = w >= 360 ? 3 : 2;
        int gap = 6;
        int cw = (w - gap * (cols - 1)) / cols;
        int ch = 96;
        if (libraryTab == 0) {
            for (int i = 0; i < builtin.size(); i++) {
                BuiltinSkies.Entry e = builtin.get(i);
                int cx = x + (i % cols) * (cw + gap), cy = y + (i / cols) * (ch + gap);
                skyCard("b:" + i, e.profile(), e.name(), e.blurb(), cx, cy, cw, ch, () -> s.loadSky(e.profile()), null);
            }
            y += ((builtin.size() + cols - 1) / cols) * (ch + gap);
        } else {
            int bw = (w - 8) / 3;
            s.controls.button(ui, "lib:save", "Save current sky…", x, y, bw, 16, Controls.Style.PRIMARY, "Save the sky you're editing", this::saveAs);
            s.controls.button(ui, "lib:paste", "Paste share code", x + bw + 4, y, bw, 16, Controls.Style.NORMAL,
                    "Load a sky from a share code on your clipboard", s::pasteShareCode);
            s.controls.button(ui, "lib:folder", "Open folder", x + 2 * bw + 8, y, bw, 16, Controls.Style.NORMAL,
                    "Open the folder with your saved skies (.json files)", () -> s.openFolder(BetterSkies.store().skiesDir()));
            y += 24;
            if (saved.isEmpty()) {
                ui.text("No saved skies yet. Make one you like, then press Save.", x + 4, y + 4, t.textDim);
                ui.text("Tip: drop .json sky files on this window to add them.", x + 4, y + 16, t.textFaint);
                return y + 32;
            }
            for (int i = 0; i < saved.size(); i++) {
                Store.Saved sv = saved.get(i);
                int cx = x + (i % cols) * (cw + gap), cy = y + (i / cols) * (ch + gap);
                skyCard("s:" + sv.file().getFileName(), sv.profile(), sv.name(), null, cx, cy, cw, ch, () -> s.loadSky(sv.profile()), sv);
            }
            y += ((saved.size() + cols - 1) / cols) * (ch + gap);
        }
        return y + 4;
    }

    private void skyCard(String id, Profile sky, String name, String blurb, int x, int y, int w, int h, Runnable apply, Store.Saved sv) {
        Ui ui = ui();
        Theme t = ui.t;
        boolean hov = ui.hover(x, y, w, h);
        float a = ui.anim("card:" + id, hov ? 1 : 0, 14);
        boolean current = p().sameLook(sky);
        int lift = Math.round(a * 2);
        ui.round(x, y - lift, w, h, 6, Colors.mixArgb(t.card, t.cardHover, a));
        ui.roundOutline(x, y - lift, w, h, 6, current ? Colors.opaque(t.accent) : Colors.mixArgb(t.cardEdge, t.accent(0.8), a));
        int pw = w - 8, ph = 44;
        ui.pushClip(x + 4, y + 4 - lift, x + 4 + pw, y + 4 - lift + ph);
        s.previews.sky(ui, sky, x + 4, y + 4 - lift, pw, ph);
        ui.popClip();
        if (current) {
            ui.round(x + w - 48, y + 7 - lift, 42, 11, 5, Colors.opaque(t.accent));
            ui.textCenter("Current", x + w - 27, y + 9 - lift, 0xFF140E1E);
        }
        ui.text(ui.fit(name, w - 10), x + 6, y + ph + 9 - lift, t.text);
        if (blurb != null) {
            List<String> lines = ui.wrap(blurb, w - 12);
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                ui.text(lines.get(i), x + 6, y + ph + 21 + i * 9 - lift, t.textFaint);
            }
        }
        ui.hit("card:" + id, x, y, w, h, () -> {
            s.click();
            apply.run();
        }, (blurb != null ? blurb + "  " : "") + "Click to use this sky (Ctrl+Z to go back)");
        if (sv != null && hov) {
            int by = y + h - 18 - lift;
            s.controls.icon(ui, "ren:" + id, "EDIT", x + 4, by, 14, true, "Rename", () -> s.prompt("Rename sky", null, sv.name(), 40, v -> {
                try {
                    BetterSkies.store().renameSky(sv, v);
                    refreshSaved();
                } catch (Exception e) {
                    s.toast("Couldn't rename: " + e.getMessage(), t.bad);
                }
            }));
            s.controls.icon(ui, "shr:" + id, "SHARE", x + 20, by, 14, true, "Copy share code", () -> {
                net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(Store.shareCode(sv.profile()));
                s.toast("Share code copied", t.good);
            });
            s.controls.icon(ui, "del:" + id, "CLOSE", x + w - 18, by, 14, true, "Delete", () -> s.confirm("Delete \"" + sv.name() + "\"?",
                    "This removes the saved sky file. Your current sky is not affected.", "Delete", () -> {
                        try {
                            BetterSkies.store().deleteSky(sv);
                            refreshSaved();
                            s.toast("Deleted \"" + sv.name() + "\"", t.textDim);
                        } catch (Exception e) {
                            s.toast("Couldn't delete: " + e.getMessage(), t.bad);
                        }
                    }));
        }
    }

    // ================================================================== colors

    private static final int[] KEY_TICKS = {6000, 12400, 23300, 18000};

    private int colors(int x, int y, int w) {
        Ui ui = ui();
        Theme t = ui.t;
        Profile p = p();
        boolean cycle = Settings.DAY_CYCLE.on(p);
        java.util.List<Setting<?>> model = new ArrayList<>(StudioScreen.sections(Category.COLORS).get("Sky model"));
        y = settingsCard("colors:model", "Sky model", model, x, y, w);
        // gradient card
        y = card("colors:grad", cycle ? "Gradients through the day" : "Sky gradient", x, y, w, top -> {
            int yy = top;
            yy += s.controls.row(ui, Settings.DAY_CYCLE, x + 4, yy, w - 8);
            List<Setting.Grad> keys = new ArrayList<>();
            keys.add(Settings.GRAD_DAY);
            if (cycle) {
                keys.add(Settings.GRAD_DUSK);
                if (Settings.SEPARATE_DAWN.on(p)) {
                    keys.add(Settings.GRAD_DAWN);
                }
                keys.add(Settings.GRAD_NIGHT);
            }
            Setting.Grad sel = null;
            for (Setting.Grad g : keys) {
                if (g.id.equals(keyframe)) {
                    sel = g;
                }
            }
            if (sel == null) {
                sel = Settings.GRAD_DAY;
                keyframe = sel.id;
            }
            if (cycle) {
                int cw = Math.min(80, (w - 16 - (keys.size() - 1) * 4) / keys.size());
                for (int i = 0; i < keys.size(); i++) {
                    Setting.Grad g = keys.get(i);
                    int tick = KEY_TICKS[g == Settings.GRAD_DAY ? 0 : g == Settings.GRAD_DUSK ? 1 : g == Settings.GRAD_DAWN ? 2 : 3];
                    s.controls.chip(ui, "kf:" + g.id, g.label, x + 8 + i * (cw + 4), yy + 2, cw, 16, g.id.equals(keyframe),
                            "Edit the " + g.label + " colors (the sky jumps to that time so you can see it)", () -> {
                                keyframe = g.id;
                                s.previewAt(tick);
                            });
                }
                yy += 24;
            }
            final Setting.Grad edit = sel;
            ui.text("Horizon", x + 10, yy, t.textFaint);
            ui.textRight("Zenith (straight up)", x + w - 10, yy, t.textFaint);
            yy += 11;
            s.controls.gradientEditor(ui, edit, x + 10, yy, w - 20, true);
            yy += s.controls.gradientEditorHeight(edit);
            // tools
            int bw = (w - 20 - 12) / 4;
            int bx = x + 10;
            s.controls.button(ui, "g:rev", "⇄ Reverse", bx, yy, bw, 14, Controls.Style.NORMAL, "Flip the gradient upside down",
                    () -> s.edit(() -> edit.set(p(), edit.get(p()).reversed())));
            s.controls.button(ui, "g:even", "Even spacing", bx + bw + 4, yy, bw, 14, Controls.Style.NORMAL, "Space the colors evenly",
                    () -> s.edit(() -> edit.set(p(), edit.get(p()).evenlySpaced())));
            s.controls.button(ui, "g:copy", "Copy to all", bx + 2 * (bw + 4), yy, bw, 14, Controls.Style.NORMAL, cycle,
                    "Use these colors for every time of day", () -> s.edit(() -> {
                        Gradient g = edit.get(p());
                        for (Setting.Grad other : List.of(Settings.GRAD_DAY, Settings.GRAD_DUSK, Settings.GRAD_DAWN, Settings.GRAD_NIGHT)) {
                            other.set(p(), g);
                        }
                    }));
            s.controls.button(ui, "g:reset", "Reset", bx + 3 * (bw + 4), yy, bw, 14, Controls.Style.GHOST, "Default colors for this gradient",
                    () -> {
                        s.controls.clearSelection(edit);
                        s.edit(() -> edit.reset(p()));
                    });
            yy += 20;
            for (Setting<?> st : List.of(Settings.SEPARATE_DAWN, Settings.CYCLE_SOFTNESS)) {
                if (visible(st)) {
                    yy += s.controls.row(ui, st, x + 4, yy, w - 8);
                }
            }
            return yy;
        });
        return genericSections(Category.COLORS, x, y, w, st -> !st.section.equals("Day cycle") && !st.section.equals("Sky model"));
    }

    // ================================================================== images

    private int images(int x, int y, int w) {
        Ui ui = ui();
        Theme t = ui.t;
        Profile p = p();
        if (images == null) {
            images = BetterSkies.images().scan();
        }
        y = card("img:list", "Pictures", x, y, w, top -> {
            int bw = (w - 20) / 2;
            s.controls.button(ui, "img:folder", "Open images folder", x + 8, top, bw, 16, Controls.Style.NORMAL,
                    "Put .png/.jpg panoramas, cube sheets, six-face folders or .zip files here", () -> s.openFolder(BetterSkies.images().folder()));
            s.controls.button(ui, "img:refresh", "↻ Refresh list", x + 12 + bw, top, bw, 16, Controls.Style.NORMAL,
                    "Look for new pictures in the folder", () -> {
                        refreshImages();
                        SkyRenderer.imagesChanged();
                    });
            int yy = top + 22;
            int cols = w >= 360 ? 4 : 3;
            int gap = 5;
            int tw = (w - 16 - gap * (cols - 1)) / cols, th = tw / 2 + 14;
            String current = Settings.IMAGE.get(p);
            List<String> names = new ArrayList<>();
            names.add("");
            for (ImageLibrary.Entry e : images) {
                names.add(e.name());
            }
            for (int i = 0; i < names.size(); i++) {
                String n = names.get(i);
                int tx = x + 8 + (i % cols) * (tw + gap), ty = yy + (i / cols) * (th + gap);
                boolean sel = n.equals(current);
                boolean hov = ui.hover(tx, ty, tw, th);
                float a = ui.anim("img:" + n, sel ? 1 : hov ? 0.5f : 0, 14);
                ui.round(tx, ty, tw, th, 4, Colors.mixArgb(t.field, t.fieldHover, a));
                ui.roundOutline(tx, ty, tw, th, 4, sel ? Colors.opaque(t.accent) : Colors.argbf(0.1 + 0.3 * a, 0xFFFFFF));
                int iw = tw - 6, ih = tw / 2 - 4;
                if (n.isEmpty()) {
                    ui.round(tx + 3, ty + 3, iw, ih, 3, 0xFF15121C);
                    ui.textCenter("None", tx + tw / 2, ty + 3 + ih / 2 - 4, t.textDim);
                } else {
                    Identifier id = thumb(n);
                    if (id != null) {
                        ui.g.blit(RenderPipelines.GUI_TEXTURED, id, tx + 3, ty + 3, 0f, 0f, iw, ih, 128, 64, 128, 64);
                    } else {
                        ui.round(tx + 3, ty + 3, iw, ih, 3, 0xFF15121C);
                        ui.textCenter("…", tx + tw / 2, ty + 3 + ih / 2 - 4, t.textFaint);
                    }
                }
                ui.textCenter(ui.fit(n.isEmpty() ? "No image" : n, tw - 6), tx + tw / 2, ty + th - 10, sel ? t.text : t.textDim);
                ui.hit("imgsel:" + i, tx, ty, tw, th, () -> s.edit(() -> {
                            Settings.IMAGE.set(p(), n);
                            if (ImageLibrary.isBuiltin(n) && Settings.IMAGE_FLOW.v(p()) == 0 && Settings.IMAGE_SHIMMER.v(p()) == 0) {
                                // built-in panoramas are made to move
                                boolean aurora = ImageLibrary.BUILTIN_AURORA.equals(n);
                                Settings.IMAGE_FLOW.set(p(), aurora ? 0.6 : 0.45);
                                Settings.IMAGE_SHIMMER.set(p(), aurora ? 0.6 : 0.0);
                                Settings.IMAGE_BLEND.set(p(), aurora ? "ADD" : "NORMAL");
                                Settings.IMAGE_SHOW.set(p(), aurora ? "NIGHT" : "ALWAYS");
                                Settings.IMAGE_SPIN.set(p(), aurora ? 0.0 : 0.15);
                            }
                        }),
                        n.isEmpty() ? "Don't show a sky image" : "Show \"" + n + "\" in the sky");
            }
            yy += ((names.size() + cols - 1) / cols) * (th + gap);
            if (images.isEmpty()) {
                ui.text("No pictures yet: drop .png/.jpg/.zip files on this window,", x + 10, yy, t.textFaint);
                ui.text("or put them in config/betterskies/images.", x + 10, yy + 10, t.textFaint);
                yy += 22;
            }
            return yy;
        });
        return genericSections(Category.IMAGES, x, y, w, st -> st != Settings.IMAGE);
    }

    /** Thumbnail texture for an image, loading it in the background on first use. */
    private Identifier thumb(String name) {
        Gpu.Tex tex = thumbs.get(name);
        if (tex != null) {
            return tex.gui("thumb/" + Integer.toHexString(name.hashCode()) + "_" + name.length());
        }
        CompletableFuture<int[]> job = thumbJobs.get(name);
        if (job == null) {
            ImageLibrary lib = BetterSkies.images();
            thumbJobs.put(name, CompletableFuture.supplyAsync(() -> {
                try {
                    ImageLibrary.Entry e = lib.find(name);
                    return e == null ? null : ImageLibrary.thumbnail(lib.load(e, 512), 128, 64);
                } catch (Exception ex) {
                    return null;
                }
            }, BetterSkies.WORKER));
            return null;
        }
        if (job.isDone()) {
            int[] px = job.getNow(null);
            if (px != null) {
                for (int i = 0; i < px.length; i++) {
                    px[i] |= 0xFF000000;
                }
                thumbs.put(name, Gpu.texture("thumb " + name, px, 128, 64));
            }
        }
        return null;
    }

    void close() {
        for (Gpu.Tex tex : thumbs.values()) {
            tex.close();
        }
        thumbs.clear();
        thumbJobs.clear();
    }

    // ================================================================== grade

    private int grade(int x, int y, int w) {
        Ui ui = ui();
        y = card("grade:looks", "Quick looks", x, y, w, top -> {
            int cw = 64, gap = 4;
            int perRow = Math.max(1, (w - 16 + gap) / (cw + gap));
            for (int i = 0; i < BuiltinSkies.LOOKS.size(); i++) {
                BuiltinSkies.Look look = BuiltinSkies.LOOKS.get(i);
                int cx = x + 8 + (i % perRow) * (cw + gap), cy = top + (i / perRow) * 20;
                s.controls.chip(ui, "look:" + i, look.name(), cx, cy, cw, 16, false,
                        "Apply the " + look.name() + " look (only the Color Grade settings change)", () -> s.edit(() -> look.apply().accept(p())));
            }
            return top + ((BuiltinSkies.LOOKS.size() + perRow - 1) / perRow) * 20;
        });
        return genericSections(Category.GRADE, x, y, w, st -> true);
    }

    // ================================================================== clouds & weather

    private int atmosphere(int x, int y, int w) {
        Ui ui = ui();
        y = card("weather:presets", "Weather presets", x, y, w, top -> {
            int cw = 92, gap = 4;
            int perRow = Math.max(1, (w - 16 + gap) / (cw + gap));
            for (int i = 0; i < BuiltinSkies.WEATHER.size(); i++) {
                BuiltinSkies.Look look = BuiltinSkies.WEATHER.get(i);
                int cx = x + 8 + (i % perRow) * (cw + gap), cy = top + (i / perRow) * 20;
                s.controls.chip(ui, "weather:" + i, look.name(), cx, cy, cw, 16, false,
                        "Set clouds, haze, fog and rays for " + look.name().toLowerCase(Locale.ROOT)
                                + " (your sky colors are kept; Ctrl+Z to undo)", () -> s.edit(() -> {
                            Settings.SKY_CLOUDS.set(p(), true);
                            look.apply().accept(p());
                        }));
            }
            int rows = (BuiltinSkies.WEATHER.size() + perRow - 1) / perRow;
            ui.text(ui.fit("Sky-only looks: the world's real weather isn't changed.", w - 16), x + 10, top + rows * 20 + 2, ui.t.textFaint);
            return top + rows * 20 + 13;
        });
        return genericSections(Category.ATMOSPHERE, x, y, w, st -> true);
    }

    // ================================================================== studio

    private int studio(int x, int y, int w) {
        Ui ui = ui();
        Theme t = ui.t;
        y = genericSections(Category.STUDIO, x, y, w, st -> true);
        y = card("studio:data", "Your data", x, y, w, top -> {
            int bw = (w - 20) / 2;
            s.controls.button(ui, "st:folder", "Open BetterSkies folder", x + 8, top, bw, 16, Controls.Style.NORMAL,
                    "config/betterskies: studio.json, saved skies, images and automatic backups", () -> s.openFolder(BetterSkies.store().root()));
            s.controls.button(ui, "st:reset", "Reset the whole sky…", x + 12 + bw, top, bw, 16, Controls.Style.DANGER,
                    "Every sky setting back to the BetterSkies default (you can undo)", () -> s.confirm("Reset everything?",
                            "All sky settings return to the BetterSkies default. Saved skies and Studio preferences are kept.", "Reset",
                            () -> s.loadSky(new Profile())));
            return top + 20;
        });
        return card("studio:about", "About", x, y, w, top -> {
            String[] lines = {
                    "BetterSkies 5 · Minecraft 1.21.11 (Fabric) · MIT license",
                    "Original mod by Tilted_Twyn. Studio, renderer and settings rebuilt in v5.",
                    "Keys: F12 open · Tab peek · Ctrl+Z/Y undo/redo · Ctrl+F search · ↑↓ pages",
                    Settings.count() + " settings · " + BuiltinSkies.all().size() + " built-in skies"};
            for (int i = 0; i < lines.length; i++) {
                ui.text(ui.fit(lines[i], w - 16), x + 10, top + i * 11, i == 0 ? t.text : t.textDim);
            }
            return top + lines.length * 11;
        });
    }
}
