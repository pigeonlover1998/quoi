package quoi.mixins;

import quoi.module.impl.player.Tweaks;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static quoi.module.impl.render.RenderOptimiser.should;

@Mixin(Camera.class)
public class CameraMixin {
    @Unique
    private boolean wasSneaking = false;

    @Shadow
    private float eyeHeight;

    @Shadow
    private Entity entity;

    @Shadow
    private float eyeHeightOld;

    @Redirect(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/Camera;eyeHeight:F",
                    opcode = Opcodes.PUTFIELD
            )
    )
    public void legacySneak(Camera obj, float value) {
        if (entity instanceof Player && should(Tweaks.getInstantSneak())) {
            if (entity.getPose() == Pose.CROUCHING) {
                wasSneaking = true;
                eyeHeightOld = eyeHeight = entity.getEyeHeight();
                return;
            } else if (wasSneaking) {
                wasSneaking = false;
                eyeHeightOld = eyeHeight = entity.getEyeHeight();
                return;
            }
        }
        this.eyeHeight = value;
    }
}
