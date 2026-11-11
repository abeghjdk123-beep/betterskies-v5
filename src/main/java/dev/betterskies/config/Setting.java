package dev.betterskies.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.betterskies.util.Colors;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * One setting, described once. The editor builds its rows, search, favorites,
 * reset buttons and tooltips from these descriptions, and the profile stores
 * the values. Settings are created through {@link Settings} only.
 */
public abstract sealed class Setting<T> permits Setting.Toggle, Setting.Slider, Setting.Color, Setting.Choice, Setting.Grad, Setting.Text {
    public final String id;
    public final String label;
    public final String help;
    public final Category category;
    public final String section;
    public final T def;
    final int index;
    /** Shown only in Advanced mode. */
    boolean advanced;
    /** Studio preferences that are not part of a sky (kept when loading a sky). */
    boolean global;
    /** Row is dimmed and locked unless this holds (e.g. aurora settings need aurora on). */
    Predicate<Profile> activeWhen = p -> true;
    String keywords = "";

    Setting(int index, String id, String label, String help, Category category, String section, T def) {
        this.index = index;
        this.id = id;
        this.label = label;
        this.help = help;
        this.category = category;
        this.section = section;
        this.def = def;
    }

    public boolean advanced() {
        return advanced;
    }

    public boolean global() {
        return global;
    }

    public boolean active(Profile p) {
        return activeWhen.test(p);
    }

    @SuppressWarnings("unchecked")
    public T get(Profile p) {
        Object v = p.values[index];
        return v == null ? def : (T) v;
    }

    public void set(Profile p, T value) {
        p.values[index] = sanitize(value);
    }

    public void reset(Profile p) {
        p.values[index] = def;
    }

    public boolean isDefault(Profile p) {
        return get(p).equals(def);
    }

    /** True if the search text matches this setting. */
    public boolean matches(String query) {
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) {
            return true;
        }
        String hay = (label + " " + help + " " + section + " " + category.title + " " + keywords).toLowerCase(Locale.ROOT);
        for (String word : q.split("\\s+")) {
            if (!hay.contains(word)) {
                return false;
            }
        }
        return true;
    }

    abstract T sanitize(T value);

    abstract JsonElement toJson(T value);

    abstract T fromJson(JsonElement e);

    /** Text form of the current value (search results, tooltips). */
    public abstract String display(Profile p);

    // ================================================================== types

    public static final class Toggle extends Setting<Boolean> {
        Toggle(int i, String id, String label, String help, Category c, String section, boolean def) {
            super(i, id, label, help, c, section, def);
        }

        public boolean on(Profile p) {
            return get(p);
        }

        public void flip(Profile p) {
            set(p, !get(p));
        }

        @Override
        Boolean sanitize(Boolean v) {
            return v != null && v;
        }

        @Override
        JsonElement toJson(Boolean v) {
            return new JsonPrimitive(v);
        }

        @Override
        Boolean fromJson(JsonElement e) {
            return e.getAsBoolean();
        }

        @Override
        public String display(Profile p) {
            return get(p) ? "On" : "Off";
        }
    }

    public enum Unit {
        PERCENT, DECIMAL, INTEGER, DEGREES, MULTIPLIER, BLOCKS, TIME_OF_DAY, EV, SIGNED_PERCENT
    }

    public static final class Slider extends Setting<Double> {
        public final double min;
        public final double max;
        public final double step;
        public final Unit unit;

        Slider(int i, String id, String label, String help, Category c, String section, double def,
               double min, double max, double step, Unit unit) {
            super(i, id, label, help, c, section, def);
            this.min = min;
            this.max = max;
            this.step = step;
            this.unit = unit;
        }

        public double v(Profile p) {
            return get(p);
        }

        public float f(Profile p) {
            return (float) (double) get(p);
        }

        /** Value mapped to [0,1] over the slider's range. */
        public double norm(Profile p) {
            return (get(p) - min) / (max - min);
        }

        public double denorm(double t) {
            return min + (max - min) * Math.max(0, Math.min(1, t));
        }

        @Override
        Double sanitize(Double v) {
            if (v == null || v.isNaN()) {
                return def;
            }
            double x = Math.max(min, Math.min(max, v));
            if (step > 0) {
                x = Math.round((x - min) / step) * step + min;
                x = Math.max(min, Math.min(max, x));
                x = Math.round(x * 1e6) / 1e6;
            }
            return x;
        }

        @Override
        JsonElement toJson(Double v) {
            return new JsonPrimitive(v);
        }

        @Override
        Double fromJson(JsonElement e) {
            return e.getAsDouble();
        }

        @Override
        public String display(Profile p) {
            return format(get(p));
        }

        public String format(double v) {
            return switch (unit) {
                case PERCENT -> Math.round(v * 100) + "%";
                case SIGNED_PERCENT -> (v > 0 ? "+" : "") + Math.round(v * 100) + "%";
                case DECIMAL -> String.format(Locale.ROOT, "%.2f", v);
                case INTEGER -> Long.toString(Math.round(v));
                case DEGREES -> Math.round(v) + "°";
                case MULTIPLIER -> String.format(Locale.ROOT, "%.2f×", v);
                case BLOCKS -> (v > 0 ? "+" : "") + Math.round(v) + " blocks";
                case EV -> String.format(Locale.ROOT, "%+.1f EV", v);
                case TIME_OF_DAY -> clock(v);
            };
        }

        /** Minecraft tick of day (0 = 6:00) to a clock string. */
        public static String clock(double tick) {
            int minutes = (int) Math.round(((tick / 1000.0) + 6) * 60) % (24 * 60);
            if (minutes < 0) {
                minutes += 24 * 60;
            }
            return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
        }
    }

    public static final class Color extends Setting<Integer> {
        Color(int i, String id, String label, String help, Category c, String section, int def) {
            super(i, id, label, help, c, section, def);
        }

        public int rgb(Profile p) {
            return get(p);
        }

        @Override
        Integer sanitize(Integer v) {
            return v == null ? def : v & 0xFFFFFF;
        }

        @Override
        JsonElement toJson(Integer v) {
            return new JsonPrimitive(Colors.hex(v));
        }

        @Override
        Integer fromJson(JsonElement e) {
            int c = Colors.parse(e.getAsString());
            return c < 0 ? def : c;
        }

        @Override
        public String display(Profile p) {
            return Colors.hex(get(p));
        }
    }

    /** One of a fixed list of options. Values are option ids. */
    public static final class Choice extends Setting<String> {
        public record Option(String id, String label, String help) {
        }

        public final List<Option> options;

        Choice(int i, String id, String label, String help, Category c, String section, List<Option> options, String def) {
            super(i, id, label, help, c, section, def);
            this.options = options;
        }

        public boolean is(Profile p, String optionId) {
            return get(p).equals(optionId);
        }

        public int ordinal(Profile p) {
            String v = get(p);
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).id().equals(v)) {
                    return i;
                }
            }
            return 0;
        }

        public Option option(Profile p) {
            return options.get(ordinal(p));
        }

        public void cycle(Profile p, int dir) {
            int n = options.size();
            set(p, options.get(((ordinal(p) + dir) % n + n) % n).id());
        }

        @Override
        String sanitize(String v) {
            for (Option o : options) {
                if (o.id().equals(v)) {
                    return v;
                }
            }
            return def;
        }

        @Override
        JsonElement toJson(String v) {
            return new JsonPrimitive(v);
        }

        @Override
        String fromJson(JsonElement e) {
            return e.getAsString();
        }

        @Override
        public String display(Profile p) {
            return option(p).label();
        }
    }

    public static final class Grad extends Setting<Gradient> {
        Grad(int i, String id, String label, String help, Category c, String section, Gradient def) {
            super(i, id, label, help, c, section, def);
        }

        @Override
        Gradient sanitize(Gradient v) {
            return v == null ? def : v;
        }

        @Override
        JsonElement toJson(Gradient v) {
            return v.toJson();
        }

        @Override
        Gradient fromJson(JsonElement e) {
            return Gradient.fromJson(e, def);
        }

        @Override
        public String display(Profile p) {
            return get(p).size() + " colors";
        }
    }

    /** Free text (e.g. the chosen sky image file). */
    public static final class Text extends Setting<String> {
        Text(int i, String id, String label, String help, Category c, String section, String def) {
            super(i, id, label, help, c, section, def);
        }

        @Override
        String sanitize(String v) {
            return v == null ? "" : v.length() > 200 ? v.substring(0, 200) : v;
        }

        @Override
        JsonElement toJson(String v) {
            return new JsonPrimitive(v);
        }

        @Override
        String fromJson(JsonElement e) {
            return e.getAsString();
        }

        @Override
        public String display(Profile p) {
            return get(p).isEmpty() ? "None" : get(p);
        }
    }
}
