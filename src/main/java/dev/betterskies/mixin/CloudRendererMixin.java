package dev.betterskies.mixin;

import dev.betterskies.BetterSkies;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import dev.betterskies.render.SkyRenderer;
import dev.betterskies.util.Colors;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clouds: CloudRenderer.render(int color, CloudStatus, float height, Vec3 camera, long gameTime, float partialTick). */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void betterskies$hide(CallbackInfo ci) {
        Profile p = BetterSkies.profile();
        if (Settings.ENABLED.on(p) && (!Settings.CLOUDS.on(p) || (Settings.SKY_CLOUDS.on(p) && Settings.SC_HIDE_VANILLA.on(p)))) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int betterskies$color(int original) {
        Profile p = BetterSkies.profile();
        if (!Settings.ENABLED.on(p)) {
            return original;
        }
        int rgb = original & 0xFFFFFF;
        String mode = Settings.CLOUD_TINT.get(p);
        if (!"VANILLA".equals(mode)) {
            int tint = "SKY".equals(mode) ? Colors.mix(SkyRenderer.horizonColor(), 0xFFFFFF, 0.35) : Settings.CLOUD_COLOR.rgb(p);
            // multiply keeps vanilla's day/night shading, then blend by strength
            int mult = Colors.rgb(Colors.r(rgb) * Colors.r(tint) / 255, Colors.g(rgb) * Colors.g(tint) / 255, Colors.b(rgb) * Colors.b(tint) / 255);
            rgb = Colors.mix(rgb, mult, Settings.CLOUD_TINT_STRENGTH.v(p));
        }
        int alpha = (int) Math.round(((original >>> 24) & 255) * Settings.CLOUD_OPACITY.v(p));
        return (Math.max(0, Math.min(255, alpha)) << 24) | rgb;
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float betterskies$height(float original) {
        Profile p = BetterSkies.profile();
        if (!Settings.ENABLED.on(p)) {
            return original;
        }
        return original + (float) Settings.CLOUD_HEIGHT.v(p);
    }
}
