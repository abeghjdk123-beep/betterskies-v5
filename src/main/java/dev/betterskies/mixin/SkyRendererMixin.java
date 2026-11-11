package dev.betterskies.mixin;

import dev.betterskies.BetterSkies;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import dev.betterskies.render.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks Minecraft's SkyRenderer (intermediary class_9975). Intermediary names are
 * used directly (remap = false) because that is what released games run:
 * <ul>
 *   <li>method_62302 renderSkyDisc(int) - BetterSkies draws its whole sky here</li>
 *   <li>method_62305 renderDarkDisc()</li>
 *   <li>method_62306 renderSunriseAndSunset(PoseStack, float, int)</li>
 *   <li>method_62303 sun, method_62304 moon, method_62310 stars (private parts of renderSunMoonAndStars)</li>
 * </ul>
 */
@Mixin(targets = "net.minecraft.class_9975", remap = false)
public abstract class SkyRendererMixin {
    @Unique
    private static boolean betterskies$warned;

    @Inject(method = "method_62302", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void betterskies$sky(int color, CallbackInfo ci) {
        try {
            if (SkyRenderer.render()) {
                ci.cancel();
            }
        } catch (Throwable t) {
            if (!betterskies$warned) {
                betterskies$warned = true;
                BetterSkies.LOG.error("[BetterSkies] Drawing the sky failed; showing the normal sky instead", t);
            }
        }
    }

    @Inject(method = "method_62305", at = @At("HEAD"), cancellable = true, remap = false)
    private void betterskies$darkDisc(CallbackInfo ci) {
        if (Settings.ENABLED.on(BetterSkies.profile())) {
            ci.cancel();
        }
    }

    @Inject(method = "method_62306", at = @At("HEAD"), cancellable = true, remap = false)
    private void betterskies$sunriseFan(CallbackInfo ci) {
        Profile p = BetterSkies.profile();
        if (Settings.ENABLED.on(p) && !Settings.GLOW_MODE.is(p, "VANILLA")) {
            ci.cancel();
        }
    }

    @Inject(method = "method_62303", at = @At("HEAD"), cancellable = true, remap = false)
    private void betterskies$sun(CallbackInfo ci) {
        Profile p = BetterSkies.profile();
        if (Settings.ENABLED.on(p) && !Settings.SUN_STYLE.is(p, "VANILLA")) {
            ci.cancel();
        }
    }

    @Inject(method = "method_62304", at = @At("HEAD"), cancellable = true, remap = false)
    private void betterskies$moon(CallbackInfo ci) {
        Profile p = BetterSkies.profile();
        if (Settings.ENABLED.on(p) && !Settings.MOON_STYLE.is(p, "VANILLA")) {
            ci.cancel();
        }
    }

    @Inject(method = "method_62310", at = @At("HEAD"), cancellable = true, remap = false)
    private void betterskies$stars(CallbackInfo ci) {
        if (Settings.ENABLED.on(BetterSkies.profile())) {
            ci.cancel();
        }
    }
}
