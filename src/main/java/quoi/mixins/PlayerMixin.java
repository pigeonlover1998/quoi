package quoi.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import quoi.api.world.Direction;
import quoi.module.impl.player.Tweaks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import quoi.utils.skyblock.player.RotationUtils;

import static quoi.module.impl.player.Tweaks.shouldSb;

@Mixin(Player.class)
public class PlayerMixin {

    @WrapOperation(
            method = "updatePlayerPose",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;setPose(Lnet/minecraft/world/entity/Pose;)V"
            )
    )
    private void disableSwim(Player instance, Pose pose, Operation<Void> original) {
        if (shouldSb(Tweaks.getDisableCrawling()) && pose == Pose.SWIMMING && !instance.isInWater()) return;
        original.call(instance, pose);
    }

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
