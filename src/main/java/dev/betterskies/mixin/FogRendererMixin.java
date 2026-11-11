package dev.betterskies.mixin;

import dev.betterskies.BetterSkies;
import dev.betterskies.config.Profile;
import dev.betterskies.config.Settings;
import dev.betterskies.render.SkyRenderer;
import dev.betterskies.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fog color: FogRenderer.computeFogColor(Camera, float, ClientLevel, int, float). */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Inject(method = "computeFogColor", at = @At("RETURN"), cancellable = true)
    private void betterskies$fog(CallbackInfoReturnable<Vector4f> cir) {
        Profile p = BetterSkies.profile();
        Vector4f v = cir.getReturnValue();
        if (v == null || !Settings.ENABLED.on(p) || Settings.FOG_TINT.is(p, "OFF")) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && (player.isUnderWater() || player.isInLava())) {
            return;
        }
        int target = Settings.FOG_TINT.is(p, "HORIZON") ? SkyRenderer.horizonColor() : Settings.FOG_COLOR.rgb(p);
        float k = (float) Settings.FOG_STRENGTH.v(p);
        // keep the vanilla brightness (caves, night, underwater) by scaling the target to it
        float lum = Math.max(0.0001f, 0.2126f * v.x() + 0.7152f * v.y() + 0.0722f * v.z());
        float tl = (float) Math.max(0.0001, Colors.luminance(target));
        float scale = Math.min(1.6f, lum / tl);
        float r = Colors.r(target) / 255f * scale, g = Colors.g(target) / 255f * scale, b = Colors.b(target) / 255f * scale;
        cir.setReturnValue(new Vector4f(v.x() + (r - v.x()) * k, v.y() + (g - v.y()) * k, v.z() + (b - v.z()) * k, v.w()));
    }
}
