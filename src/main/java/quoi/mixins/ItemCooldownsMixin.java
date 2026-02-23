package quoi.mixins;

import quoi.module.impl.player.Tweaks;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static quoi.module.impl.player.Tweaks.shouldSb;

@Mixin(ItemCooldowns.class)
public class ItemCooldownsMixin {
    @Inject(
            method = "getCooldownPercent",
            at = @At("HEAD"),
            cancellable = true
    )
    private void disableEnderPearlCooldown(ItemStack itemStack, float f, CallbackInfoReturnable<Float> cir) {
        if (!shouldSb(Tweaks.getDisableItemCooldowns())) return;
        if (itemStack.getItem() == Items.ENDER_PEARL) cir.setReturnValue(0f);
    }
}
