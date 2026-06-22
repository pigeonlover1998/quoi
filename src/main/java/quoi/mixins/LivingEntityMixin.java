package quoi.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import quoi.api.world.Direction;
import quoi.utils.skyblock.player.RotationUtils;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @ModifyExpressionValue(
            method = "jumpFromGround",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"
            )
    )
    private float fixJumpRot(float original) {
        if ((Object) this != Minecraft.getInstance().player) return original;
        Direction dir = RotationUtils.getServerDirection();
        return dir != null ? dir.getYaw() : original;
    }
}
