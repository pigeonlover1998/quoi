package quoi.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import quoi.api.world.Direction;
import quoi.module.impl.render.NameTags;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import quoi.utils.skyblock.player.RotationUtils;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {

    @Inject(
            method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",
            at = @At(value = "HEAD"),
            cancellable = true
    )
    private void shouldShowName(T livingEntity, double d, CallbackInfoReturnable<Boolean> cir) {
        if (NameTags.getShouldCancelTag()) cir.cancel();
    }

    @ModifyExpressionValue(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/Mth;rotLerp(FFF)F"
            )
    )
    private float headYaw(float original, LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
        if (entity != Minecraft.getInstance().player) return original;

        Direction dir = RotationUtils.getServerDirection();
        return dir != null ? dir.getYaw() : original;
    }

    @ModifyExpressionValue(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getXRot(F)F"
            )
    )
    private float headPitch(float original, LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
        if (entity != Minecraft.getInstance().player) return original;

        Direction dir = RotationUtils.getServerDirection();
        return dir != null ? dir.getPitch() : original;
    }
}
