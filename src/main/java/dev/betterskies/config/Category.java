package dev.betterskies.config;

/** Editor pages. The glyph is drawn as the sidebar icon. */
public enum Category {
    HOME("Home", "⌂", "Overview, quick switches and your current sky"),
    LIBRARY("Sky Library", "▦", "Built-in skies, your saved skies and sky images"),
    COLORS("Sky Colors", "◐", "Gradients, the day cycle and the horizon"),
    SUN_MOON("Sun & Moon", "☀", "Shape, size, color and glow of the sun and moon"),
    STARS("Stars & Space", "✦", "Stars, the Milky Way, nebulae and cosmic dust"),
    AURORA("Aurora", "≈", "Northern lights: style, colors and motion"),
    METEORS("Meteors & Comets", "☄", "Shooting stars, meteor showers and comets"),
    ATMOSPHERE("Clouds & Weather", "☁", "Clouds, fog, sunset glow, rain and the sky clock"),
    IMAGES("Sky Images", "▣", "Panorama and cubemap pictures as sky layers"),
    GRADE("Color Grade", "◈", "Sky-only color filters"),
    STUDIO("Studio", "⚙", "Look of this editor, quality and performance");

    public final String title;
    public final String glyph;
    public final String blurb;

    Category(String title, String glyph, String blurb) {
        this.title = title;
        this.glyph = glyph;
        this.blurb = blurb;
    }
}
