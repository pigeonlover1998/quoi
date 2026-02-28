package quoi.mixins;

import quoi.api.events.EntityEvent;
import quoi.mixininterfaces.IEntityGlow;
import quoi.module.impl.player.Tweaks;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static quoi.module.impl.player.Tweaks.shouldSb;

@Mixin(Entity.class)
public class EntityMixin implements IEntityGlow {

    @Unique
    private int glowColour = 0xFFFFFF;
    @Unique
    private boolean forceGlow = false;

    @Override
    public int quoi$getGlowColour() {
        return glowColour;
    }

    @Override
    public void quoi$setGlowColour(int colour) {
        this.glowColour = colour;
    }

    @Override
    public boolean quoi$getForceGlow() {
        return forceGlow;
    }

    @Override
    public void quoi$setForceGlow(boolean value) {
        this.forceGlow = value;
    }

    @ModifyVariable(
            method = "setSwimming",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private boolean disableSwim(boolean value) {
        if (!shouldSb(Tweaks.getDisableCrawling())) return value;
        Entity entity = (Entity) (Object) this;
        if (!(entity instanceof LocalPlayer)) return value;
        if (entity.isSwimming()) return value;
        return false;
    }

    @Inject(
            method = "isCurrentlyGlowing",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity)(Object)this;

        EntityEvent.ForceGlow event = new EntityEvent.ForceGlow(entity);
        event.post();
        forceGlow = event.isGlowing();
        glowColour = event.getGlowColour().getRgb();
        if (forceGlow) cir.setReturnValue(true);
    }

    @Inject(
            method = "getTeamColor",
            at = @At("HEAD"),
            cancellable = true
    )
    public void onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
        if (forceGlow) cir.setReturnValue(glowColour);
//        Entity self = (Entity)(Object)this;
//
//        Integer color = DungeonESP.getTeammateColour(self);
//        if (color != null) cir.setReturnValue(color);
    }
}