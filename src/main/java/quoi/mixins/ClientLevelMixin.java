package quoi.mixins;

import quoi.api.events.EntityEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {

    @Inject(
            method = "removeEntity",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$onRemoveEntity(int entityId, Entity.RemovalReason removalReason, CallbackInfo ci) {
        Entity entity = ((ClientLevel) (Object) this).getEntity(entityId);
        if (entity != null && new EntityEvent.Leave(entity, removalReason).post()) ci.cancel();
    }
}
