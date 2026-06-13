package quoi.mixins;

import quoi.api.events.GuiEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(
            method = "init*",
            at = @At("HEAD"),
            cancellable = true
    )
    protected void quoi$onInitPre(CallbackInfo ci) {
        if (new GuiEvent.Open((Screen) (Object) this).post()) ci.cancel();
    }

    @Inject(
            method = "init*",
            at = @At("TAIL"),
            cancellable = true
    )
    protected void quoi$onInitPost(CallbackInfo ci) {
        if (new GuiEvent.Open.Post((Screen) (Object) this).post()) ci.cancel();
    }

    @Inject(
            method = "onClose",
            at = @At("HEAD"),
            cancellable = true
    )
    protected void quoi$onClose(CallbackInfo ci) {
        if (new GuiEvent.Close((Screen) (Object) this).post()) ci.cancel();
    }

    @Inject(
            method = "extractRenderStateWithTooltipAndSubtitles",
            at = @At("HEAD"),
            cancellable = true
    )
    protected void quoi$onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (new GuiEvent.Draw((Screen) (Object) this, context, mouseX, mouseY).post()) ci.cancel();
    }

    @Inject(
            method = "extractRenderStateWithTooltipAndSubtitles",
            at = @At("TAIL"),
            cancellable = true
    )
    protected void quoi$onRenderPost(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (new GuiEvent.Draw.Post((Screen) (Object) this, context, mouseX, mouseY).post()) ci.cancel();
    }

    @Inject(
            method = "extractBackground",
            at = @At("HEAD"),
            cancellable = true
    )
    protected void quoi$onRenderBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (new GuiEvent.DrawBackground((Screen) (Object) this, context, mouseX, mouseY).post()) ci.cancel();
    }

    @Inject(
            method = "extractBackground",
            at = @At("TAIL"),
            cancellable = true
    )
    protected void quoi$onRenderBackgroundPost(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (new GuiEvent.DrawBackground.Post((Screen) (Object) this, context, mouseX, mouseY).post()) ci.cancel();
    }
}
