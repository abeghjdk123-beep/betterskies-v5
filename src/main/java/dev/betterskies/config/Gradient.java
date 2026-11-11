package dev.betterskies.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.betterskies.util.Colors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * An immutable list of color stops. Position 0 is the horizon, 1 is straight up.
 * Edits return a new Gradient, which keeps undo/redo and copies trivial.
 */
public final class Gradient {
    public record Stop(double pos, int rgb) {
        public Stop {
            pos = Math.max(0, Math.min(1, pos));
            rgb &= 0xFFFFFF;
        }
    }

    public static final int MAX_STOPS = 12;

    private final List<Stop> stops;

    public Gradient(List<Stop> stops) {
        List<Stop> s = new ArrayList<>(stops);
        s.sort(Comparator.comparingDouble(Stop::pos));
        if (s.isEmpty()) {
            s.add(new Stop(0, 0x88AAFF));
        }
        if (s.size() == 1) {
            s.add(new Stop(1, s.get(0).rgb()));
        }
        while (s.size() > MAX_STOPS) {
            s.remove(s.size() - 2);
        }
        this.stops = Collections.unmodifiableList(s);
    }

    /** Evenly spaced stops from horizon to zenith. */
    public static Gradient of(int... colors) {
        List<Stop> s = new ArrayList<>();
        for (int i = 0; i < colors.length; i++) {
            s.add(new Stop(colors.length == 1 ? 0 : (double) i / (colors.length - 1), colors[i]));
        }
        return new Gradient(s);
    }

    /** Pairs of (position, color). */
    public static Gradient at(Object... posColor) {
        List<Stop> s = new ArrayList<>();
        for (int i = 0; i + 1 < posColor.length; i += 2) {
            s.add(new Stop(((Number) posColor[i]).doubleValue(), ((Number) posColor[i + 1]).intValue()));
        }
        return new Gradient(s);
    }

    public List<Stop> stops() {
        return stops;
    }

    public int size() {
        return stops.size();
    }

    public Stop get(int i) {
        return stops.get(i);
    }

    // ---------------------------------------------------------------- edits

    public Gradient with(int index, Stop stop) {
        List<Stop> s = new ArrayList<>(stops);
        s.set(index, stop);
        return new Gradient(s);
    }

    public Gradient add(Stop stop) {
        if (stops.size() >= MAX_STOPS) {
            return this;
        }
        List<Stop> s = new ArrayList<>(stops);
        s.add(stop);
        return new Gradient(s);
    }

    public Gradient remove(int index) {
        if (stops.size() <= 2) {
            return this;
        }
        List<Stop> s = new ArrayList<>(stops);
        s.remove(index);
        return new Gradient(s);
    }

    public Gradient reversed() {
        List<Stop> s = new ArrayList<>();
        for (Stop st : stops) {
            s.add(new Stop(1 - st.pos(), st.rgb()));
        }
        return new Gradient(s);
    }

    public Gradient evenlySpaced() {
        List<Stop> s = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            s.add(new Stop((double) i / (stops.size() - 1), stops.get(i).rgb()));
        }
        return new Gradient(s);
    }

    /** Index of the stop that would sit right after position p after sorting. */
    public int indexOf(Stop st) {
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i) == st || stops.get(i).equals(st)) {
                return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- sampling

    public enum Blend {
        SMOOTH("Smooth", "Eased transitions between colors (softest look)."),
        PERCEPTUAL("Perceptual", "Mixes in OKLab, so color pairs never pass through muddy grey."),
        LINEAR("Linear", "Straight RGB mixing, like the classic sky."),
        BANDS("Bands", "Hard bands of color for a stylised, toon look.");

        public final String label;
        public final String help;

        Blend(String label, String help) {
            this.label = label;
            this.help = help;
        }
    }

    /**
     * Color at height t (0 horizon, 1 zenith). bias moves every mid-point up or down
     * (0.5 = centred).
     */
    public int sample(double t, Blend blend, double bias) {
        t = Math.max(0, Math.min(1, t));
        Stop first = stops.get(0);
        if (t <= first.pos()) {
            return first.rgb();
        }
        Stop last = stops.get(stops.size() - 1);
        if (t >= last.pos()) {
            return last.rgb();
        }
        for (int i = 1; i < stops.size(); i++) {
            Stop b = stops.get(i);
            if (t <= b.pos()) {
                Stop a = stops.get(i - 1);
                double span = b.pos() - a.pos();
                double f = span <= 1e-9 ? 1 : (t - a.pos()) / span;
                f = applyBias(f, bias);
                return switch (blend) {
                    case SMOOTH -> Colors.mixOklab(a.rgb(), b.rgb(), f * f * (3 - 2 * f));
                    case PERCEPTUAL -> Colors.mixOklab(a.rgb(), b.rgb(), f);
                    case LINEAR -> Colors.mix(a.rgb(), b.rgb(), f);
                    case BANDS -> f < 0.5 ? a.rgb() : b.rgb();
                };
            }
        }
        return last.rgb();
    }

    private static double applyBias(double f, double bias) {
        if (Math.abs(bias - 0.5) < 1e-4) {
            return f;
        }
        // Schlick bias curve: bias < .5 pulls toward the lower color, > .5 toward the upper
        double b = Math.max(0.02, Math.min(0.98, 1 - bias));
        return f / ((1 / b - 2) * (1 - f) + 1);
    }

    /** Average color, used for "match sky" fog/cloud tints. */
    public int average() {
        long r = 0, g = 0, b = 0;
        int n = 16;
        for (int i = 0; i < n; i++) {
            int c = sample((i + 0.5) / n, Blend.LINEAR, 0.5);
            r += Colors.r(c);
            g += Colors.g(c);
            b += Colors.b(c);
        }
        return Colors.rgb((int) (r / n), (int) (g / n), (int) (b / n));
    }

    // ---------------------------------------------------------------- json

    public JsonElement toJson() {
        JsonArray arr = new JsonArray();
        for (Stop s : stops) {
            JsonObject o = new JsonObject();
            o.addProperty("pos", Math.round(s.pos() * 10000) / 10000.0);
            o.addProperty("color", Colors.hex(s.rgb()));
            arr.add(o);
        }
        return arr;
    }

    public static Gradient fromJson(JsonElement e, Gradient fallback) {
        try {
            if (e == null || !e.isJsonArray()) {
                return fallback;
            }
            List<Stop> s = new ArrayList<>();
            for (JsonElement el : e.getAsJsonArray()) {
                JsonObject o = el.getAsJsonObject();
                int c = Colors.parse(o.get("color").getAsString());
                if (c >= 0) {
                    s.add(new Stop(o.get("pos").getAsDouble(), c));
                }
            }
            return s.size() >= 2 ? new Gradient(s) : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Gradient g && g.stops.equals(stops);
    }

    @Override
    public int hashCode() {
        return stops.hashCode();
    }
}
