package quoi.mixins;

import quoi.api.events.MouseEvent;
import quoi.module.impl.misc.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(
            method = "onScroll",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (checkShit(window)) return;

        if (Chat.INSTANCE.isDown()) {
            Chat.INSTANCE.scroll((int) vertical);
            ci.cancel();
        }

        if (new MouseEvent.Scroll(horizontal, vertical).post()) ci.cancel();
    }

    @Inject(
            method = "onButton",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onMouseClick(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        if (checkShit(window)) return;
        if (new MouseEvent.Click(input.button(), action == 1).post()) ci.cancel();
    }

    @Inject(
            method = "onMove",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onMouseMove(long window, double mx, double my, CallbackInfo ci) {
        if (checkShit(window)) return;
        if (new MouseEvent.Move(mx, my).post()) ci.cancel();
    }

    @Unique
    private boolean checkShit(long window) {
        Minecraft mc = Minecraft.getInstance();
        return window != mc.getWindow().handle() || mc.screen != null;
    }
}
