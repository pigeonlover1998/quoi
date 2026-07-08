package quoi.mixins;

import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import quoi.api.events.BlockEvent;
import quoi.module.impl.player.Tweaks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static quoi.module.impl.player.Tweaks.shouldSb;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @WrapOperation(
            method = "useItemOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/border/WorldBorder;isWithinBounds(Lnet/minecraft/core/BlockPos;)Z"
            )
    )
    private boolean fixInteract(WorldBorder instance, BlockPos blockPos, Operation<Boolean> original) {
        if (!shouldSb(Tweaks.getFixInteract())) return original.call(instance, blockPos);
        return true;
    }

    @Inject(
            method = "startDestroyBlock",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onStartDestroyBlock(BlockPos blockPos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (new BlockEvent.Destroy.Start(blockPos, direction).post()) cir.setReturnValue(false);
    }
}
