package quoi.mixins;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.utils.skyblock.player.PacketOrderManager;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z", ordinal = 0), method = "handleKeybinds")
    public void onAttackKey(CallbackInfo ci) {
        PacketOrderManager.INSTANCE.execute(PacketOrderManager.State.ATTACK);
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z", ordinal = 1), method = "handleKeybinds")
    public void onUseKey(CallbackInfo ci) {
        PacketOrderManager.INSTANCE.execute(PacketOrderManager.State.ITEM_USE);
    }
}
