package quoi.mixins;

import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.module.impl.misc.ItemAnimations;
import quoi.module.impl.player.Tweaks;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static quoi.module.impl.player.Tweaks.shouldSb;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin extends AbstractClientPlayer {

    public LocalPlayerMixin(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }

    @ModifyReturnValue(
            method = "isMovingSlowly",
            at = @At("RETURN")
    )
    private boolean onShouldSlowDown(boolean original) {
        if (shouldSb(Tweaks.getSneakLagFix())) {
            return this.isShiftKeyDown() && !this.getAbilities().flying;
        }
        return original;
    }

    @Inject(
            method = "swing",
            at = @At("HEAD")
    )
    private void quoi$onSwing(InteractionHand hand, CallbackInfo ci) {
        ItemAnimations.onSwing();
    }
}
