package dev.betterskies;

import com.mojang.blaze3d.platform.InputConstants;
import dev.betterskies.config.Migration;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import dev.betterskies.config.Store;
import dev.betterskies.render.ImageLibrary;
import dev.betterskies.screen.StudioScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** BetterSkies: entry point and the shared state (the live sky, storage, images). */
public final class BetterSkies implements ClientModInitializer {
    public static final String MOD_ID = "betterskies";
    public static final Logger LOG = LoggerFactory.getLogger("BetterSkies");

    /** One low-priority background thread for star generation and picture loading. */
    public static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BetterSkies worker");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private static volatile Profile profile = new Profile();
    private static volatile long revision = 1;
    private static volatile int previewTime = -1;
    private static Store store;
    private static ImageLibrary images;
    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        store = new Store(configDir.resolve(MOD_ID));
        images = new ImageLibrary(store.imagesDir());
        Profile loaded = store.loadCurrent();
        if (loaded == null) {
            Profile migrated = Migration.run(configDir, store);
            loaded = migrated != null ? migrated : new Profile();
            store.saveCurrent(loaded);
        }
        profile = loaded;

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.betterskies.studio", InputConstants.Type.KEYSYM, InputConstants.KEY_F12, category));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new StudioScreen(null));
                }
            }
        });
        LOG.info("[BetterSkies] Ready with {} settings. Press F12 in game to open BetterSkies Studio.", Settings.count());
    }

    // ================================================================== live sky

    /** The sky being shown right now (the editor edits this directly for live preview). */
    public static Profile profile() {
        return profile;
    }

    /** Replaces the live sky. */
    public static void setProfile(Profile p) {
        profile = p;
        revision++;
    }

    /** Call after changing values of {@link #profile()} in place. */
    public static void changed() {
        revision++;
    }

    public static long revision() {
        return revision;
    }

    /** Saves the live sky to disk. */
    public static void save() {
        store.saveCurrent(profile);
    }

    /** Tick of day the editor is previewing, or -1 for the real time. */
    public static int previewTime() {
        return previewTime;
    }

    public static void setPreviewTime(int tick) {
        previewTime = tick;
    }

    public static Store store() {
        return store;
    }

    public static ImageLibrary images() {
        return images;
    }

    public static KeyMapping openKey() {
        return openKey;
    }
}
