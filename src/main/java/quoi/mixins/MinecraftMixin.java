package quoi.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import quoi.api.events.EntityEvent;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Unique
    private static boolean cancelSwing = false;

    @Redirect(
            method = "startAttack()Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V"
            )
    )
    private void redirectAttack(MultiPlayerGameMode gameMode, Player player, Entity entity) {
        if (new EntityEvent.Attack(entity).post()) {
            cancelSwing = true;
        } else {
            gameMode.attack(player, entity);
        }
    }

    @Redirect(
            method = "startAttack()Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"
            )
    )
    private void redirectSwing(LocalPlayer instance, InteractionHand hand) {
        if (cancelSwing) {
            cancelSwing = false;
        } else {
            instance.swing(hand);
        }
    }
}
