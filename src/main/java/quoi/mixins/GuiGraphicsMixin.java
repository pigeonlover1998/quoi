package quoi.mixins;

import quoi.module.impl.render.RenderOptimiser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static quoi.module.impl.render.RenderOptimiser.should;

@Mixin(GuiGraphics.class)
public class GuiGraphicsMixin {

    @Redirect(
            method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At(
                    value = "NEW",
                    target = "Lnet/minecraft/client/gui/render/state/GuiTextRenderState;"
            )
    )
    private GuiTextRenderState disableShadow(Font textRenderer, FormattedCharSequence orderedText, Matrix3x2f matrix, int x, int y, int color, int backgroundColor, boolean shadow, ScreenRectangle clipBounds) {
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

        return new GuiTextRenderState(textRenderer, orderedText, matrix, x, y, color, backgroundColor, finalShadow, clipBounds);
    }
}