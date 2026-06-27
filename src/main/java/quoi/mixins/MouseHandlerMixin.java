package quoi.mixins;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Shadow;
import quoi.api.events.MouseEvent;
import quoi.module.impl.misc.chat.impl.ChatPeek;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.module.impl.player.Tweaks;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Shadow
    private double xpos;
    @Shadow
    private double ypos;

    @Unique
    private double beforeX;
    @Unique
    private double beforeY;

    @Inject(
            method = "onScroll",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (checkShit(window)) return;

        if (ChatPeek.isDown()) {
            ChatPeek.scroll((int) vertical);
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

    /**
     * from OdinFabric (BSD 3-Clause)
     * copyright (c) 2025-2026 odtheking
     * original: https://github.com/odtheking/Odin/blob/main/src/main/java/com/odtheking/mixin/mixins/MouseHandlerMixin.java
     */
    @Inject(
            method = "grabMouse",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/MouseHandler;xpos:D",
                    ordinal = 0,
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void odin$lockXPos(CallbackInfo ci) {
        this.beforeX = this.xpos;
        this.beforeY = this.ypos;
    }

    @Inject(method = "releaseMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getWindow()Lcom/mojang/blaze3d/platform/Window;"))
    private void odin$correctCursorPosition(CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof ContainerScreen && Tweaks.shouldHookMouse()) {
            InputConstants.grabOrReleaseMouse(Minecraft.getInstance().getWindow(), InputConstants.CURSOR_NORMAL, this.beforeX, this.beforeY);
            this.xpos = this.beforeX;
            this.ypos = this.beforeY;
        }
    }
}
