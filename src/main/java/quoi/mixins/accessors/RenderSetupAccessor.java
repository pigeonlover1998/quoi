package quoi.mixins.accessors;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderSetup.class)
public interface RenderSetupAccessor {
    @Invoker("builder")
    static RenderSetup.RenderSetupBuilder invokeBuilder(RenderPipeline pipeline) {
        throw new AssertionError();
    }
}