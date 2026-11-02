package com.FIYTP.realmedicalmod.mixin;

import com.FIYTP.realmedicalmod.client.font.BrainDamageFontHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin 注入 Font 类的文字批量渲染方法
 */
@Mixin(value = Font.class, priority = 1000)
public abstract class FontMixin {

    @Shadow
    public abstract int drawInBatch(
            FormattedCharSequence text,
            float x,
            float y,
            int color,
            boolean dropShadow,
            Matrix4f matrix,
            MultiBufferSource buffer,
            Font.DisplayMode displayMode,
            int backgroundColor,
            int packedLightCoords
    );

    @Inject(
            method = "drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void realmedicalmod$onDrawInBatchString(
            String text,
            float x,
            float y,
            int color,
            boolean dropShadow,
            Matrix4f matrix,
            MultiBufferSource buffer,
            Font.DisplayMode displayMode,
            int backgroundColor,
            int packedLightCoords,
            boolean bidirectional,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (BrainDamageFontHelper.IS_PROCESSING.get()) {
            return;
        }

        float strength = BrainDamageFontHelper.updateAndGetEffectStrength();
        if (strength <= 0.0f || text == null || text.isEmpty()) {
            return;
        }

        BrainDamageFontHelper.IS_PROCESSING.set(true);
        try {
            int brainHealth = BrainDamageFontHelper.getCachedBrainHealth();

            // 1. 字符乱码替换
            String obfuscatedText = BrainDamageFontHelper.obfuscateString(text);

            // 2. 颜色与透明度波动
            int newColor = BrainDamageFontHelper.computeColorPerturbation(color, brainHealth, strength);

            Font self = (Font) (Object) this;

            // 3. 若属于重度 (40~60)，增加轻微重影
            if (BrainDamageFontHelper.shouldRenderGhostShadow(brainHealth)) {
                Matrix4f ghostMatrix = new Matrix4f(matrix);
                ghostMatrix.translate(1.2f, 0.8f, 0.0f);
                int ghostColor = (newColor & 0x00FFFFFF) | 0x44000000;
                self.drawInBatch(obfuscatedText, x, y, ghostColor, false, ghostMatrix, buffer, displayMode, backgroundColor, packedLightCoords, bidirectional);
            }

            int result = self.drawInBatch(
                    obfuscatedText,
                    x,
                    y,
                    newColor,
                    dropShadow,
                    matrix,
                    buffer,
                    displayMode,
                    backgroundColor,
                    packedLightCoords,
                    bidirectional
            );
            cir.setReturnValue(result);
        } finally {
            BrainDamageFontHelper.IS_PROCESSING.set(false);
        }
    }

    @Inject(
            method = "drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void realmedicalmod$onDrawInBatchCharSequence(
            FormattedCharSequence text,
            float x,
            float y,
            int color,
            boolean dropShadow,
            Matrix4f matrix,
            MultiBufferSource buffer,
            Font.DisplayMode displayMode,
            int backgroundColor,
            int packedLightCoords,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (BrainDamageFontHelper.IS_PROCESSING.get()) {
            return;
        }

        float strength = BrainDamageFontHelper.updateAndGetEffectStrength();
        if (strength <= 0.0f || text == null) {
            return;
        }

        BrainDamageFontHelper.IS_PROCESSING.set(true);
        try {
            int brainHealth = BrainDamageFontHelper.getCachedBrainHealth();

            // 1. 字符乱码替换
            FormattedCharSequence obfuscatedText = BrainDamageFontHelper.obfuscateCharSequence(text);

            // 2. 颜色与透明度波动
            int newColor = BrainDamageFontHelper.computeColorPerturbation(color, brainHealth, strength);

            Font self = (Font) (Object) this;

            // 3. 若属于重度 (40~60)，增加轻微重影
            if (BrainDamageFontHelper.shouldRenderGhostShadow(brainHealth)) {
                Matrix4f ghostMatrix = new Matrix4f(matrix);
                ghostMatrix.translate(1.2f, 0.8f, 0.0f);
                int ghostColor = (newColor & 0x00FFFFFF) | 0x44000000;
                self.drawInBatch(obfuscatedText, x, y, ghostColor, false, ghostMatrix, buffer, displayMode, backgroundColor, packedLightCoords);
            }

            int result = self.drawInBatch(
                    obfuscatedText,
                    x,
                    y,
                    newColor,
                    dropShadow,
                    matrix,
                    buffer,
                    displayMode,
                    backgroundColor,
                    packedLightCoords
            );
            cir.setReturnValue(result);
        } finally {
            BrainDamageFontHelper.IS_PROCESSING.set(false);
        }
    }
}
