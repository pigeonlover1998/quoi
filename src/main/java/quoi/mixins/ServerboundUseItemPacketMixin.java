package quoi.mixins;

import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.api.world.Direction;
import quoi.utils.skyblock.player.RotationUtils;

@Mixin(ServerboundUseItemPacket.class)
public class ServerboundUseItemPacketMixin {

    @Mutable
    @Shadow
    @Final
    private float yRot;

    @Mutable
    @Shadow
    @Final
    private float xRot;

    @Inject(
            method = "<init>(Lnet/minecraft/world/InteractionHand;IFF)V",
            at = @At("RETURN")
    )
    private void applySilentRot(InteractionHand hand, int sequence, float yaw, float pitch, CallbackInfo ci) {
        Direction dir = RotationUtils.getServerDirection();
        if (dir == null) return;
        this.yRot = dir.getYaw();
        this.xRot = dir.getPitch();
    }
}
