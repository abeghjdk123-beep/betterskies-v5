package dev.betterskies.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Crisp pixel-art icons drawn with plain rectangles (Minecraft's font draws many
 * symbols tiny, so the editor paints its own). Each icon is a few rows of
 * '#' (pixel) and '.' (empty).
 */
public final class Icons {
    private Icons() {
    }

    private static final Map<String, String[]> ICONS = new HashMap<>();

    static {
        put("HOME",
                "....#....",
                "...###...",
                "..##.##..",
                ".##...##.",
                "#########",
                ".#.....#.",
                ".#.###.#.",
                ".#.#.#.#.",
                ".###.###.");
        put("LIBRARY",
                "####.####",
                "#..#.#..#",
                "#..#.#..#",
                "####.####",
                ".........",
                "####.####",
                "#..#.#..#",
                "#..#.#..#",
                "####.####");
        put("COLORS",
                "..#####..",
                ".#..####.",
                "#...#####",
                "#...#####",
                "#...#####",
                "#...#####",
                "#...#####",
                ".#..####.",
                "..#####..");
        put("SUN_MOON",
                "#...#...#",
                ".#.....#.",
                "...###...",
                "..#####..",
                "#.#####.#",
                "..#####..",
                "...###...",
                ".#.....#.",
                "#...#...#");
        put("STARS",
                "....#....",
                "....#....",
                "...###...",
                "..#####..",
                "#########",
                "..#####..",
                "...###...",
                "....#....",
                "....#....");
        put("AURORA",
                ".##...##.",
                "#..#.#..#",
                "....#....",
                ".........",
                ".##...##.",
                "#..#.#..#",
                "....#....",
                ".........",
                ".##...##.");
        put("METEORS",
                ".......##",
                "......###",
                ".....###.",
                "....###..",
                "...##....",
                "..##.....",
                ".#.......",
                "#........",
                ".........");
        put("ATMOSPHERE",
                ".........",
                "...###...",
                "..#####..",
                ".#######.",
                "#########",
                "#########",
                ".#######.",
                ".........",
                ".........");
        put("IMAGES",
                "#########",
                "#.......#",
                "#.##....#",
                "#.##....#",
                "#.....#.#",
                "#....###.",
                "#..######",
                "#.#######",
                "#########");
        put("GRADE",
                "..#......",
                "#########",
                "..#......",
                ".........",
                "......#..",
                "#########",
                "......#..",
                ".........",
                "...#.....");
        put("STUDIO",
                "...#.#...",
                ".#######.",
                ".##...##.",
                "###...###",
                "##.....##",
                "###...###",
                ".##...##.",
                ".#######.",
                "...#.#...");
        put("UNDO",
                "..#......",
                ".##......",
                "#######..",
                ".##....#.",
                "..#.....#",
                "........#",
                "........#",
                ".......#.",
                "....###..");
        put("REDO",
                "......#..",
                "......##.",
                "..#######",
                ".#....##.",
                "#.....#..",
                "#........",
                "#........",
                ".#.......",
                "..###....");
        put("EYE",
                ".........",
                "..#####..",
                ".#.....#.",
                "#..###..#",
                "#..#.#..#",
                "#..###..#",
                ".#.....#.",
                "..#####..",
                ".........");
        put("SEARCH",
                ".###...",
                "#...#..",
                "#...#..",
                "#...#..",
                ".###...",
                "....#..",
                ".....##");
        put("PIN",
                "...#...",
                "..###..",
                "#######",
                ".#####.",
                "..###..",
                ".##.##.",
                ".#...#.");
        put("RESET",
                ".####..",
                "#....#.",
                "#......",
                "#...###",
                "#....#.",
                ".####..",
                ".......");
        put("CLOSE",
                "#.....#",
                ".#...#.",
                "..#.#..",
                "...#...",
                "..#.#..",
                ".#...#.",
                "#.....#");
        put("EDIT",
                ".....##",
                "....###",
                "...###.",
                "..###..",
                ".###...",
                "###....",
                "##.....");
        put("SHARE",
                "..#####",
                "..#...#",
                "#####.#",
                "#...#.#",
                "#...###",
                "#...#..",
                "#####..");
        put("VIEW_HORIZON",
                ".........",
                "...###...",
                "..#####..",
                "#########",
                ".........",
                ".#######.",
                ".........",
                "...###...",
                ".........");
        put("VIEW_SKY",
                "..#####..",
                ".#.....#.",
                "#.......#",
                "#...#...#",
                "...###...",
                "..#####..",
                ".........",
                "#########",
                ".........");
        put("VIEW_UP",
                "....#....",
                "...###...",
                "..#####..",
                ".#######.",
                "...###...",
                "...###...",
                "...###...",
                "...###...",
                ".........");
        put("SPARKLE",
                "...#...",
                "...#...",
                "..###..",
                "#######",
                "..###..",
                "...#...",
                "...#...");
        put("CHEVRON",
                "#####",
                ".###.",
                "..#..");
    }

    private static void put(String name, String... rows) {
        ICONS.put(name, rows);
    }

    public static int width(String name) {
        String[] rows = ICONS.get(name);
        return rows == null ? 0 : rows[0].length();
    }

    public static int height(String name) {
        String[] rows = ICONS.get(name);
        return rows == null ? 0 : rows.length;
    }

    /** Draws an icon with its top-left corner at x, y. */
    public static void draw(Ui ui, String name, int x, int y, int argb) {
        String[] rows = ICONS.get(name);
        if (rows == null) {
            return;
        }
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int run = -1;
            for (int c = 0; c <= row.length(); c++) {
                boolean on = c < row.length() && row.charAt(c) == '#';
                if (on && run < 0) {
                    run = c;
                } else if (!on && run >= 0) {
                    ui.rect(x + run, y + r, c - run, 1, argb);
                    run = -1;
                }
            }
        }
    }

    /** Draws an icon at an integer scale (each pixel becomes scale x scale). */
    public static void drawScaled(Ui ui, String name, int x, int y, int scale, int argb) {
        String[] rows = ICONS.get(name);
        if (rows == null) {
            return;
        }
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                if (rows[r].charAt(c) == '#') {
                    ui.rect(x + c * scale, y + r * scale, scale, scale, argb);
                }
            }
        }
    }

    public static boolean has(String name) {
        return ICONS.containsKey(name);
    }

    /** Draws an icon centred in a box. */
    public static void center(Ui ui, String name, int bx, int by, int bw, int bh, int argb) {
        draw(ui, name, bx + (bw - width(name) + 1) / 2, by + (bh - height(name) + 1) / 2, argb);
    }
}
