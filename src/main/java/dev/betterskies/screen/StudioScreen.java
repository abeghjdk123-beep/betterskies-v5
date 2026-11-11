package dev.betterskies.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class StudioScreen extends Screen {
    private final Screen parent;

    public StudioScreen(Screen parent) {
        super(Component.literal("BetterSkies Studio"));
        this.parent = parent;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
