package quoi.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import quoi.api.world.Direction;
import quoi.utils.skyblock.player.RotationUtils;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    @ModifyExpressionValue(
            method = {"sendPosition", "tick"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"
            )
    )
    private float silentRotationYaw(float original) {
        Direction dir = RotationUtils.getServerDirection();
        if (dir == null) return original;
        return dir.getYaw();
    }

    @ModifyExpressionValue(
            method = {"sendPosition", "tick"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"
            )
    )
    private float silentRotationPitch(float original) {
        Direction dir = RotationUtils.getServerDirection();
        if (dir == null) return original;
        return dir.getPitch();
    }
}
