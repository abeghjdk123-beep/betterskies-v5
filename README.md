# ✦ BetterSkies 5

**A full sky studio for Minecraft 1.21.11 (Fabric).** Paint your own sky with multi-color gradients that change through the day, fill the night with procedural stars, the Milky Way, nebulae and northern lights, add shooting stars and comets, style the sun, moon, clouds and fog — all live, in a redesigned in-game editor.

> BetterSkies 5 is a ground-up remake of BetterSkies 3.9 by **Tilted_Twyn**. Same idea, rebuilt renderer, rebuilt settings, brand-new editor.

## ⬇ Download

**[Get the latest build here](https://github.com/abeghjdk123-beep/betterskies-v5/releases/tag/latest)** — download the `.jar` and put it in your `mods` folder.

You need:
- Minecraft **1.21.11** with **Fabric Loader** 0.17+
- **Fabric API**
- *(optional)* Mod Menu — adds a config button that opens the Studio

If you still have BetterSkies 3 (`smoothskygradient`) installed, remove it: the two replace the same sky. Your old sky and presets are imported automatically the first time v5 starts.

## Opening the Studio

Press **F12** in game (rebindable under Controls → BetterSkies), or use Mod Menu.

| Key | Does |
| --- | --- |
| `F12` / `Esc` | Open / close (changes save automatically) |
| `Tab` (hold) | Peek: hide the editor and look at your sky |
| `Ctrl+Z` / `Ctrl+Y` | Undo / redo (up to 120 steps) |
| `Ctrl+F` | Search every setting |
| `↑` / `↓` | Previous / next page |

## What's new compared with 3.x

**Rendering**
- The sky is drawn as a high-resolution textured dome instead of 35,000 colored vertices — sharper gradients, sharper stars, and much less CPU work per frame.
- Stars, Milky Way, nebulae and cosmic dust are painted on a background thread into a 1K–4K sky texture (no stutter while you tweak), with realistic star colors and the brightest stars twinkling individually.
- **Day cycle with full gradients**: separate multi-color gradients for day, sunset, night (and optionally sunrise), blended smoothly as the sun moves. 3.x only had four single colors.
- Perceptual (OKLab) color blending, so sunsets never go muddy grey between colors; plus Smooth, Linear and toon-style Bands modes.
- New procedural **sun** (soft disc, glowing orb, pixel) and **moon** (realistic craters and true phase shading, soft, pixel), moon glow, icy moon halo, earthshine, and an **orbit tilt** so the sun arcs across the sky.
- **Northern lights** rebuilt as real 3D curtains hanging in the sky: bright green bases with a sharp lower edge, pink and red tops, fine vertical rays, folds that ripple, and a soft glow on the sky behind. They converge overhead when they reach high ("Reach overhead"), and a Storm style gives an intense pink-and-green outburst. **Natural colors** adapt the aurora to your night-sky colors, nebulae and moonlight, or pick your own colors.
- **Star twinkle** you can actually see: stars scintillate in brightness, size and color, more strongly near the horizon where the air is thicker.
- **Real atmosphere horizon** fades smoothly below the horizon into haze and ground color instead of stopping in a hard band.
- **Reset the whole sky** (Studio page) and the **Blank sky** entry in the library give you a plain sky with every effect turned off, a clean canvas to build your own.
- **Meteor showers**: some nights bring showers with meteors radiating from one point. **Comets** with tails that point away from the sun.
- **Sunset glow** that wraps around the horizon toward the sun, replacing the vanilla fan (or keep vanilla's).
- Horizon haze, a configurable horizon line, and what to show below the horizon (fade to ground, extend, mirror).
- Weather: rain darkening, storm tint, hiding space effects in rain.
- **Sky clock**: freeze the sky at any time of day without touching the world's time.
- Sky images: 2:1 panoramas, OptiFine-style 3:2 cube sheets, six-face folders or `.zip` files, all converted to one panorama. Normal or glow blending, rotation, spin, turn-with-sun, horizon fade, tint, day/night visibility.
- **Color grade** that only touches the sky: exposure, contrast, highlights, shadows, gamma, saturation, vibrance, temperature, tint, hue shift — plus nine one-click looks.
- **Realistic clouds**: soft, natural clouds painted by BetterSkies — puffy cumulus, wispy cirrus, mixed, layered stratus or scattered puffs. Set coverage, thickness, cloud size, edge softness, detail, a random pattern seed, wind speed and direction, altitude, horizon fade, and sunlit / shadow / sunset colors. They drift with the wind, slide past as you travel, glow warm at sunrise and sunset, dim at night and darken in rain. Minecraft's blocky clouds are hidden while they're on (optional).
- **Real atmosphere** sky mode (Sky Colors → Sky model): physically based light scattering through a spherical atmosphere with air (Rayleigh), haze (Mie) and ozone, including the planet's shadow. You get true blue skies, white horizon haze, the glow around the sun, deep orange-red sunsets, blue-purple twilight, Earth's shadow and the pink Belt of Venus at dusk, with automatic eye adaptation. Tune haze, air thickness, ozone, sun glow, brightness, viewing altitude, ground color and tint, mix in your own gradients, and optionally fade into your Night gradient after dark. Sunlight takes its real color (white at noon, orange at sunset) and lights the clouds.
- **Sunlight through clouds**: clouds near the sun light up with glowing silver edges and sun-colored bodies.
- **Sun rays**: beams fanning out from the sun that follow the real gaps in the clouds; strength, length, number, color, and an "only through clouds" option.
- **Weather presets**: Clear, Fair weather, Partly cloudy, Overcast, Thunderstorm (with lightning flickering inside the clouds), Foggy morning, Hazy summer, Golden evening and Winter night, in one click.
- **Moving panoramas**: any sky picture can Drift (turn), Flow (its contents gently roll and ripple) and Shimmer (waves of brightness, like a living aurora). Animated GIFs and numbered frame sequences play with smooth crossfades. Two built-in animated panoramas are included: Drifting clouds and Aurora veil.
- Fog and blocky clouds can match your sky: fog takes the horizon color (keeping vanilla brightness in caves; skipped underwater and in lava), clouds can be tinted, faded and raised or lowered by blocks.

**The Studio (editor)**
- A completely custom-drawn interface: sidebar pages, cards, animated switches and sliders, dropdowns, an HSV color picker with recent colors and colors from your sky, toasts, and themes (7 accent colors, adjustable glass opacity).
- The sky updates **live** behind the editor while you edit. A **24-hour timeline** in the footer lets you scrub to any time of day; picking a gradient tab (Day/Sunset/Night) jumps the sky to that time automatically.
- **Gradient editor**: click the bar to add colors, drag handles to move them, double-click to recolor, right-click to remove; reverse, even spacing, copy to every time of day.
- **Every setting is defined once** with its own label, help text, range and default, so: search finds anything, every row has a reset button (↺) and right-click reset, any setting can be pinned (☆) to the Home page, sliders accept typed values (click the number), and a help bar explains whatever is under the mouse.
- **Simple / Advanced** mode: pages stay short until you want the fine-tuning knobs.
- **Sky Library**: 12 built-in skies with painted previews (day / sunset / night), your saved skies, rename / delete / share.
- **Share codes**: copy a sky as text and paste it into anyone's BetterSkies.
- Drag & drop `.png/.jpg/.zip` pictures or `.json` sky files onto the window to import them.
- Camera view buttons (horizon / sky / zenith) and "Surprise me" for a random sky with matching colors.
- Undo/redo for everything, plus "Revert" to the sky you had when you opened the editor.

**Settings & files**
- Everything lives in `config/betterskies/`: `studio.json` (current sky and editor state), `skies/*.json` (saved skies), `images/` (sky pictures), `backups/` (the last 8 versions of `studio.json`, written automatically).
- Unknown or broken values never crash: they fall back to defaults, and a damaged file is backed up before being replaced.
- Settings that don't apply right now (e.g. aurora colors while the aurora is off) are dimmed instead of hidden.

## Sky images

Put pictures in `config/betterskies/images/` (or drop them on the Studio window):

- `name.png` / `name.jpg` — a 2:1 equirectangular panorama
- `name.png` with a 3:2 shape — a cube sheet (top row: bottom, top, south; bottom row: west, north, east)
- `name.gif`, or a folder / `.zip` of numbered frames (`frame_001.png`, `frame_002.png`, …): an animated sky (up to 32 frames, crossfaded)
- a folder or `.zip` with six faces named `…north`, `…south`, `…east`, `…west`, `…top`/`…up`, `…bottom`/`…down` (or `px nx py ny pz nz`)

Use **Glow (add)** blending for pictures of stars or nebulae on black, so they light up your gradient instead of covering it.

## Building

```
./gradlew build
```

The jar ends up in `build/libs/`. Every push to `main` is built by GitHub Actions and published to the [latest release](https://github.com/abeghjdk123-beep/betterskies-v5/releases/tag/latest).

## License

MIT — see [LICENSE](LICENSE). Original BetterSkies by Tilted_Twyn (MIT).
