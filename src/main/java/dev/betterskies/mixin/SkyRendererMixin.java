package dev.betterskies.mixin;

import dev.betterskies.BetterSkies;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks Minecraft's SkyRenderer:
 * <ul>
 *   <li>renderSkyDisc(int) - BetterSkies draws its whole sky here</li>
 *   <li>renderDarkDisc(), renderSunriseAndSunset(...)</li>
 *   <li>renderSun / renderMoon / renderStars (private parts of renderSunMoonAndStars)</li>
 * </ul>
 * Mojang names; Loom remaps them for the released jar.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Unique
    private static boolean betterskies$warned;

    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true, require = 1)
    private void betterskies$sky(int color, CallbackInfo ci) {
        try {
            if (dev.betterskies.render.SkyRenderer.render()) {
                ci.cancel();
            }
        } catch (Throwable t) {
            if (!betterskies$warned) {
                betterskies$warned = true;
                BetterSkies.LOG.error("[BetterSkies] Drawing the sky failed; showing the normal sky instead", t);
            }
        }
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void betterskies$darkDisc(CallbackInfo ci) {
        if (Settings.ENABLED.on(BetterSkies.profile())) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void betterskies$sunriseFan(CallbackInfo ci) {
        Profile p = BetterSkies.profile();
        if (Settings.ENABLED.on(p) && !Settings.GLOW_MODE.is(p, "VANILLA")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void betterskies$sun(CallbackInfo ci) {
        Profile p = BetterSkies.profile();
        if (Settings.ENABLED.on(p) && !Settings.SUN_STYLE.is(p, "VANILLA")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void betterskies$moon(CallbackInfo ci) {
        Profile p = BetterSkies.profile();
        if (Settings.ENABLED.on(p) && !Settings.MOON_STYLE.is(p, "VANILLA")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void betterskies$stars(CallbackInfo ci) {
        if (Settings.ENABLED.on(BetterSkies.profile())) {
            ci.cancel();
        }
    }
}
