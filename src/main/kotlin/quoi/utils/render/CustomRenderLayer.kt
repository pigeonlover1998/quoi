package quoi.utils.render

import com.mojang.blaze3d.pipeline.RenderPipeline
import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import quoi.mixins.accessors.RenderSetupAccessor
import quoi.mixins.accessors.RenderTypeAccessor

/**
 * 1.21.11 hides the RenderType factory, so use mixin invokers to keep custom
 * no-depth pipelines working instead of falling back to vanilla depth-tested
 * layers like linesTranslucent/debugQuads.
 *
 * from jcnlk's quoi
 * original: https://github.com/jcnlk/quoi/blob/26.1.x/src/main/kotlin/quoi/utils/render/CustomRenderLayer.kt
 */
object CustomRenderLayer {
    private fun create(
        name: String,
        pipeline: RenderPipeline,
        configure: RenderSetup.RenderSetupBuilder.() -> Unit = {}
    ): RenderType {
        val setup = RenderSetupAccessor.invokeBuilder(pipeline)
            .bufferSize(RenderType.TRANSIENT_BUFFER_SIZE)
            .apply(configure)
            .createRenderSetup()
        return RenderTypeAccessor.invokeCreate(name, setup)
    }

    val LINE_LIST: RenderType = create("quoi_lines", CustomRenderPipelines.LINE_LIST) {
        setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
    }
    val LINE_LIST_ESP: RenderType = create("quoi_lines_esp", CustomRenderPipelines.LINE_LIST_ESP)
    val TRIANGLE_STRIP: RenderType = create("quoi_debug_quads", CustomRenderPipelines.TRIANGLE_STRIP) {
        setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
        sortOnUpload()
    }
    val TRIANGLE_STRIP_ESP: RenderType = create("quoi_debug_quads_esp", CustomRenderPipelines.TRIANGLE_STRIP_ESP) {
        sortOnUpload()
    }
}