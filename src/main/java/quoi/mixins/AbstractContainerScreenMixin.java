package quoi.mixins;

import quoi.api.events.GuiEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {

//    @Inject(
//            method = "render",
//            at = @At("HEAD"),
//            cancellable = true
//    )
//    protected void quoi$onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
//        if (new GuiEvent.Draw((Screen) (Object) this, context, mouseX, mouseY).post()) ci.cancel();
//    }

//    @Inject(
//            method = "renderBackground",
//            at = @At("HEAD"),
//            cancellable = true
//    )
//    protected void quoi$onRenderBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
//        if (new GuiEvent.DrawBackground((Screen) (Object) this, context, mouseX, mouseY).post()) ci.cancel();
//    }

    @Inject(
            method = "extractSlot",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$onDrawSlot(GuiGraphicsExtractor context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (new GuiEvent.Slot.Draw((Screen) (Object) this, context, slot).post()) ci.cancel();
    }

    @Inject(
            method = "slotClicked",
            at = @At("HEAD"),
            cancellable = true
    )
    public void quoi$onMouseClickedSlot(Slot slot, int slotId, int button, ContainerInput actionType, CallbackInfo ci) {
        if (slot == null) return;
        if (new GuiEvent.Slot.Click((Screen) (Object) this, slot, slotId, button, actionType).post()) ci.cancel();
    }

//    @Inject(
//            method = "mouseClicked",
//            at = @At("HEAD"),
//            cancellable = true
//    )
//    public void quoi$onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
//        if (EventBus.INSTANCE.post(new GuiEvent.Click((Screen) (Object) this, (int) mouseX, (int) mouseY, button, false)))
//            cir.cancel();
//    }
//
//    @Inject(
//            method = "mouseReleased",
//            at = @At("HEAD"),
//            cancellable = true
//    )
//    public void quoi$onMouseRelease(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
//        if (EventBus.INSTANCE.post(new GuiEvent.Click((Screen) (Object) this, (int) mouseX, (int) mouseY, button, true)))
//            cir.cancel();
//    }

//    @Inject(
//            method = "keyPressed",
//            at = @At("HEAD"),
//            cancellable = true
//    )
//    public void quoi$onKeyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
//        if (new GuiEvent.Key((Screen) (Object) this, input.input()).post()) cir.cancel();
//    }

    @Inject(
            method = "extractTooltip",
            at = @At("HEAD"),
            cancellable = true
    )
    public void quoi$onDrawMouseoverTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        if (new GuiEvent.DrawTooltip((Screen) (Object) this, context, mouseX, mouseY).post()) {
            ci.cancel();
        }
    }
}
