package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FogRenderer.class, priority = 900)//We must execute before sodium
public class MixinFogRenderer {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void voxy$modifyFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        if (!VoxyConfig.CONFIG.isRenderingEnabled()) return;

        var vrs = IVoxyRenderSystemHolder.getNullable();
        if (vrs == null) return;
        var data = cir.getReturnValue();
        boolean fogIsDamnClose = data.environmentalEnd<10;
        if (!fogIsDamnClose) {
            var mode = VoxyConfig.CONFIG.getFogMode();
            if (mode != me.cortex.voxy.client.config.FogMode.DEFAULT) {
                // OFF または 各種推奨モード（EXTENDED, SUBTLE, ATMOSPHERIC）:
                // バニラ描画領域（手前の数十チャンク）にバニラ環境フォグがかかるのを防ぎ、
                // フォグ開始位置をMODの描画開始地点（バニラ描画距離の端）に合わせる。
                // これにより手前チャンクの謎のフェードと、Voxy境界での急激な濃度段差（断層）を根絶する。
                data.environmentalStart = 99999999;
                data.environmentalEnd = 99999999;
            }
        }

        data.renderDistanceStart = 999999999;
        data.renderDistanceEnd = 999999999;
    }
}
