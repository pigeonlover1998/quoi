package quoi.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import quoi.api.events.KeyEvent;
import quoi.api.input.MutableInput;
import quoi.utils.skyblock.player.RotationUtils;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
    @ModifyExpressionValue(
            method = "tick",
            at = @At(
                    value = "NEW",
                    target = "(ZZZZZZZ)Lnet/minecraft/world/entity/player/Input;"
            )
    )
    private Input hookMovementCorrection(Input original) {
        MutableInput mutInput = new MutableInput(original);

        RotationUtils.adjustInputFromDirection(mutInput);

        KeyEvent.Input event = new KeyEvent.Input(original, mutInput);
        event.post();

        return event.getInput().toInput();
    }
}
