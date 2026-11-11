package com.terraformersmc.modmenu.api;

/** Compile-only stub of Mod Menu's API (the real one is provided by Mod Menu at runtime). */
public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return screen -> null;
    }
}
