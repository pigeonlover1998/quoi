package quoi.mixins;

import quoi.module.impl.render.NameTags;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(NameTagFeatureRenderer.class)
public class NameTagFeatureRendererMixin {

    @ModifyArgs(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V"
            )
    )
    private void draw(Args args) {
        if (!NameTags.INSTANCE.getEnabled()) return;
        args.set(4, NameTags.getShadow());
        if (NameTags.getCustomBg()) args.set(8, NameTags.getBgColour().getRgb());
    }
}
