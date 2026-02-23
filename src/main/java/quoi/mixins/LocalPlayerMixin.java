package quoi.mixins;

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
}
