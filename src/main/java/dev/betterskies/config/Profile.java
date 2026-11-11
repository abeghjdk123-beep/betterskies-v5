package dev.betterskies.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Arrays;

/**
 * Values for every {@link Setting}. A profile is "a sky": saving one stores all
 * of its look settings; studio preferences ({@link Setting#global()}) ride along
 * but are left alone when a different sky is loaded.
 */
public final class Profile {
    public static final int FORMAT = 5;

    final Object[] values;
    public String name = "My Sky";

    public Profile() {
        values = new Object[Settings.count()];
        for (Setting<?> s : Settings.all()) {
            values[s.index] = s.def;
        }
    }

    public Profile copy() {
        Profile p = new Profile();
        System.arraycopy(values, 0, p.values, 0, values.length);
        p.name = name;
        return p;
    }

    /** Copies the look of {@code sky} into this profile, keeping studio preferences. */
    public void loadLook(Profile sky) {
        for (Setting<?> s : Settings.all()) {
            if (!s.global) {
                values[s.index] = sky.values[s.index];
            }
        }
        name = sky.name;
    }

    /** True if both profiles describe the same sky (studio preferences ignored). */
    public boolean sameLook(Profile o) {
        for (Setting<?> s : Settings.all()) {
            if (!s.global && !values[s.index].equals(o.values[s.index])) {
                return false;
            }
        }
        return true;
    }

    public void resetCategory(Category c) {
        for (Setting<?> s : Settings.all()) {
            if (s.category == c) {
                s.reset(this);
            }
        }
    }

    // ---------------------------------------------------------------- json

    public JsonObject toJson(boolean includeGlobal) {
        JsonObject o = new JsonObject();
        o.addProperty("format", FORMAT);
        o.addProperty("name", name);
        JsonObject v = new JsonObject();
        for (Setting<?> s : Settings.all()) {
            if (s.global && !includeGlobal) {
                continue;
            }
            v.add(s.id, json(s));
        }
        o.add("settings", v);
        return o;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private JsonElement json(Setting s) {
        return s.toJson(s.get(this));
    }

    /** Reads a profile; unknown keys are ignored and missing ones keep their defaults. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Profile fromJson(JsonObject o) {
        Profile p = new Profile();
        if (o.has("name")) {
            p.name = o.get("name").getAsString();
        }
        JsonObject v = o.has("settings") ? o.getAsJsonObject("settings") : o;
        for (Setting s : Settings.all()) {
            JsonElement e = v.get(s.id);
            if (e == null || e.isJsonNull()) {
                continue;
            }
            try {
                s.set(p, s.fromJson(e));
            } catch (RuntimeException ignored) {
                // bad value: keep the default
            }
        }
        return p;
    }

    public static Profile fromJsonString(String json) {
        return fromJson(JsonParser.parseString(json).getAsJsonObject());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Profile p && Arrays.equals(values, p.values) && name.equals(p.name);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }
}
