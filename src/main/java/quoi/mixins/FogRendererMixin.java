package quoi.mixins;

import quoi.module.impl.render.RenderOptimiser;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import static quoi.module.impl.render.RenderOptimiser.should;

@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @ModifyVariable(
            method = "getBuffer",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private FogRenderer.FogMode disableFog(FogRenderer.FogMode value) {
        return should(RenderOptimiser.getDisableFog()) ? FogRenderer.FogMode.NONE : value;
    }
}
