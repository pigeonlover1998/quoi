package quoi.mixins;

import quoi.module.impl.render.RenderOptimiser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static quoi.module.impl.render.RenderOptimiser.should;

@Mixin(GuiGraphicsExtractor.class)
public class GuiGraphicsMixin {

    @Redirect(
            method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At(
                    value = "NEW",
                    target = "Lnet/minecraft/client/renderer/state/gui/GuiTextRenderState;"
            )
    )
    private GuiTextRenderState disableShadow(Font textRenderer, FormattedCharSequence orderedText, Matrix3x2fc matrix, int x, int y, int color, int backgroundColor, boolean shadow, boolean includeEmpty, ScreenRectangle clipBounds) {
        boolean disableShadows = should(RenderOptimiser.getDisableTextShadow());
        boolean forceContainerShadows = should(RenderOptimiser.getContainerTextShadow());
        boolean inContainer = Minecraft.getInstance().screen instanceof AbstractContainerScreen;

        boolean finalShadow;

        if (inContainer && forceContainerShadows) {
            finalShadow = true;
        } else if (disableShadows) {
            finalShadow = false;
        } else {
            finalShadow = shadow;
        }

        return new GuiTextRenderState(textRenderer, orderedText, matrix, x, y, color, backgroundColor, finalShadow, includeEmpty, clipBounds);
    }
}