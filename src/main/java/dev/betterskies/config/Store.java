package dev.betterskies.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.betterskies.BetterSkies;
import dev.betterskies.util.Colors;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Everything on disk, under config/betterskies/:
 * <pre>
 *   studio.json      current sky + editor state (favorites, recent colors)
 *   skies/*.json     saved skies
 *   images/          sky pictures (panoramas, cubemaps, zips)
 *   backups/         the last few versions of studio.json
 * </pre>
 */
public final class Store {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String SHARE_PREFIX = "BSKY5:";

    private final Path root;
    private final Path skiesDir;
    private final Path imagesDir;
    private final Path backupsDir;

    public final Set<String> favorites = new LinkedHashSet<>();
    public final List<Integer> recentColors = new ArrayList<>();
    public String lastCategory = "HOME";

    public Store(Path root) {
        this.root = root;
        this.skiesDir = root.resolve("skies");
        this.imagesDir = root.resolve("images");
        this.backupsDir = root.resolve("backups");
        try {
            Files.createDirectories(skiesDir);
            Files.createDirectories(imagesDir);
            Files.createDirectories(backupsDir);
        } catch (IOException e) {
            BetterSkies.LOG.warn("[BetterSkies] Could not create config folders: {}", e.toString());
        }
    }

    public Path root() {
        return root;
    }

    public Path imagesDir() {
        return imagesDir;
    }

    public Path skiesDir() {
        return skiesDir;
    }

    // ================================================================== current sky

    /** Loads the current sky; returns null if there is none yet. */
    public Profile loadCurrent() {
        Path file = root.resolve("studio.json");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (o.has("favorites")) {
                for (JsonElement e : o.getAsJsonArray("favorites")) {
                    favorites.add(e.getAsString());
                }
            }
            if (o.has("recentColors")) {
                for (JsonElement e : o.getAsJsonArray("recentColors")) {
                    int c = Colors.parse(e.getAsString());
                    if (c >= 0) {
                        recentColors.add(c);
                    }
                }
            }
            if (o.has("lastCategory")) {
                lastCategory = o.get("lastCategory").getAsString();
            }
            return o.has("current") ? Profile.fromJson(o.getAsJsonObject("current")) : null;
        } catch (Exception e) {
            BetterSkies.LOG.error("[BetterSkies] studio.json is damaged ({}); starting from defaults. A copy was kept in backups/.", e.toString());
            try {
                Files.copy(file, backupsDir.resolve("studio-damaged-" + stamp() + ".json"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // nothing else to do
            }
            return null;
        }
    }

    public void saveCurrent(Profile p) {
        JsonObject o = new JsonObject();
        o.addProperty("format", Profile.FORMAT);
        o.add("current", p.toJson(true));
        JsonArray fav = new JsonArray();
        favorites.forEach(fav::add);
        o.add("favorites", fav);
        JsonArray rc = new JsonArray();
        recentColors.forEach(c -> rc.add(Colors.hex(c)));
        o.add("recentColors", rc);
        o.addProperty("lastCategory", lastCategory);
        Path file = root.resolve("studio.json");
        try {
            if (Files.isRegularFile(file)) {
                Files.copy(file, backupsDir.resolve("studio-" + stamp() + ".json"), StandardCopyOption.REPLACE_EXISTING);
                pruneBackups();
            }
            writeAtomic(file, GSON.toJson(o));
        } catch (IOException e) {
            BetterSkies.LOG.error("[BetterSkies] Could not save studio.json: {}", e.toString());
        }
    }

    public void rememberColor(int rgb) {
        recentColors.remove((Integer) rgb);
        recentColors.add(0, rgb);
        while (recentColors.size() > 10) {
            recentColors.remove(recentColors.size() - 1);
        }
    }

    private void pruneBackups() throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(backupsDir, "studio-2*.json")) {
            ds.forEach(files::add);
        }
        files.sort(Comparator.comparing(Path::getFileName));
        for (int i = 0; i < files.size() - 8; i++) {
            Files.deleteIfExists(files.get(i));
        }
    }

    private static String stamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    private static void writeAtomic(Path file, String text) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ================================================================== saved skies

    public record Saved(String name, Path file, Profile profile) {
    }

    public List<Saved> savedSkies() {
        List<Saved> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(skiesDir)) {
            for (Path f : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                try {
                    Profile p = Profile.fromJsonString(Files.readString(f, StandardCharsets.UTF_8));
                    String fallback = f.getFileName().toString().replaceFirst("\\.json$", "");
                    if (p.name == null || p.name.isBlank()) {
                        p.name = fallback;
                    }
                    out.add(new Saved(p.name, f, p));
                } catch (Exception e) {
                    BetterSkies.LOG.warn("[BetterSkies] Skipping unreadable sky {}: {}", f.getFileName(), e.toString());
                }
            }
        } catch (IOException e) {
            BetterSkies.LOG.warn("[BetterSkies] Could not list saved skies: {}", e.toString());
        }
        out.sort(Comparator.comparing(s -> s.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    /** Saves the look of a sky under a name. Returns the name actually used. */
    public String saveSky(String name, Profile p) throws IOException {
        name = cleanName(name);
        Profile copy = p.copy();
        copy.name = name;
        writeAtomic(skiesDir.resolve(fileName(name)), GSON.toJson(copy.toJson(false)));
        return name;
    }

    public void deleteSky(Saved s) throws IOException {
        Files.deleteIfExists(s.file());
    }

    public String renameSky(Saved s, String newName) throws IOException {
        String used = saveSky(newName, s.profile());
        if (!s.file().equals(skiesDir.resolve(fileName(used)))) {
            Files.deleteIfExists(s.file());
        }
        return used;
    }

    public boolean skyExists(String name) {
        return Files.exists(skiesDir.resolve(fileName(cleanName(name))));
    }

    public static String cleanName(String name) {
        String n = name == null ? "" : name.trim().replaceAll("[\\p{Cntrl}]", "");
        if (n.isEmpty()) {
            n = "My Sky";
        }
        return n.length() > 40 ? n.substring(0, 40) : n;
    }

    private static String fileName(String name) {
        String f = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_").replaceAll("^_+|_+$", "");
        return (f.isEmpty() ? "sky" : f) + ".json";
    }

    // ================================================================== share codes

    /** A compact text code anyone can paste into their own BetterSkies. */
    public static String shareCode(Profile p) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
                gz.write(new Gson().toJson(p.toJson(false)).getBytes(StandardCharsets.UTF_8));
            }
            return SHARE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Reads a share code (or raw JSON). Returns null if the text isn't a sky. */
    public static Profile fromShareCode(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        try {
            if (t.startsWith("{")) {
                return Profile.fromJsonString(t);
            }
            if (!t.startsWith(SHARE_PREFIX)) {
                return null;
            }
            byte[] raw = Base64.getUrlDecoder().decode(t.substring(SHARE_PREFIX.length()));
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                return Profile.fromJsonString(new String(gz.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            return null;
        }
    }
}
