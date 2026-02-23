package quoi.mixins;

import quoi.module.impl.render.RenderOptimiser;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static quoi.module.impl.render.RenderOptimiser.should;

@Mixin(LightTexture.class)
public class LightTextureMixin {

    @ModifyExpressionValue(
            method = "updateLightTexture",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/dimension/DimensionType;ambientLight()F"
            )
    )
    private float getAmbientLight(float original) {
        return should(RenderOptimiser.getFullBright()) ? 1.0f : original;
    }
}
