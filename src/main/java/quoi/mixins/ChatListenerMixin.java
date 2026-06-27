package quoi.mixins;

import quoi.module.impl.player.PlayerDisplay;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ChatListener.class)
public class ChatListenerMixin {
    @ModifyArg(
            method = "handleOverlay",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V"
            ),
            index = 0
    )
    private Component modifyOverlayMessage(Component original) {
        return PlayerDisplay.modifyActionBar(original);
    }
}
