package quoi.mixins;

import quoi.module.impl.dungeon.CancelInteract;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Redirect(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    public InteractionResult shouldCancelInteract(MultiPlayerGameMode instance, LocalPlayer player, InteractionHand hand, BlockHitResult hitResult) {
        if (CancelInteract.INSTANCE.cancelInteractHook(hitResult.getBlockPos())) {
            return InteractionResult.PASS;
        }
        return instance.useItemOn(player, hand, hitResult);
    }
}