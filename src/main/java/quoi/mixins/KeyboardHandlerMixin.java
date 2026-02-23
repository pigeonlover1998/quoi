package quoi.mixins;

import quoi.api.events.GuiEvent;
import quoi.api.events.KeyEvent;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Unique Minecraft mc = Minecraft.getInstance();

    @Inject(
            method = "keyPress",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$onKey(long window, int action, net.minecraft.client.input.KeyEvent input, CallbackInfo ci) { // todo fix media keys
        if (mc.screen != null) return;
        if (action == 1) {
            if (new KeyEvent.Press(input.input(), input.scancode(), input.modifiers()).post()) ci.cancel();
        } else if (action == 0) {
            if (new KeyEvent.Release(input.input(), input.scancode(), input.modifiers()).post()) ci.cancel();
        }
    }

    @Inject(
            method = "charTyped",
            at = @At("HEAD"),
            cancellable = true
    )
    public void quoi$onKeyPressed(long window, CharacterEvent characterEvent, CallbackInfo ci) {
        if (mc.screen == null || !characterEvent.isAllowedChatCharacter()) return;
        if (new GuiEvent.Char(mc.screen, (char) characterEvent.codepoint()).post()) ci.cancel();
    }
}
