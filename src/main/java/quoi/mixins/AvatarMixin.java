package quoi.mixins;

import quoi.module.impl.player.Tweaks;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import static quoi.module.impl.player.Tweaks.shouldSb;

@Mixin(Avatar.class)
public class AvatarMixin {

    @Shadow
    @Final
    protected static EntityDimensions STANDING_DIMENSIONS;

    @ModifyReturnValue(
            method = "getDefaultDimensions",
            at = @At("RETURN")
    )
    private EntityDimensions getDimensions(EntityDimensions original, Pose pose) {
        if (pose == Pose.CROUCHING && shouldSb(Tweaks.getLegacySneakHeight())) {
            return STANDING_DIMENSIONS.withEyeHeight(1.54F);
        }
        return original;
    }
}
