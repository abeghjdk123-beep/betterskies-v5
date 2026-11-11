package dev.betterskies.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.betterskies.screen.StudioScreen;

/** Opens BetterSkies Studio from Mod Menu's config button. */
public final class ModMenuCompat implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return StudioScreen::new;
    }
}
