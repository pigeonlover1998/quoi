package quoi.mixins;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.utils.skyblock.player.SwapManager;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @Shadow
    private int carriedIndex;

    @Inject(method = "ensureHasSentCarriedItem", at = @At("HEAD"), cancellable = true)
    public void onEnsureHasSentCarriedItem(CallbackInfo ci) {
        if (!SwapManager.INSTANCE.onEnsureHasSentCarriedItem(this.carriedIndex)) {
            ci.cancel();
        }
    }
}
