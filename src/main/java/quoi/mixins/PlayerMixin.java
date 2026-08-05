package quoi.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import quoi.api.world.Direction;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import quoi.utils.skyblock.player.RotationUtils;

@Mixin(Player.class)
public class PlayerMixin {

    @ModifyExpressionValue(
            method = {"causeExtraKnockback", "doSweepAttack"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"
            )
    )
    private float fixRot(float original) {
        if ((Object) this != Minecraft.getInstance().player) return original;
        Direction dir = RotationUtils.getServerDirection();
        if (dir == null) return original;
        return dir.getYaw();
    }
}
