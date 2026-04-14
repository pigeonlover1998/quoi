package quoi.utils.render

import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType

/**
 * from OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/render/CustomRenderLayer.kt
 */
object CustomRenderLayer {

    val LINE_LIST: RenderType = RenderType.create(
        "line-list",
        RenderSetup.builder(CustomRenderPipelines.LINE_LIST)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
            .createRenderSetup()
    )

    val LINE_LIST_ESP: RenderType = RenderType.create(
        "line-list-esp",
        RenderSetup.builder(CustomRenderPipelines.LINE_LIST_ESP)
            .createRenderSetup()
    )

    val TRIANGLE_STRIP: RenderType = RenderType.create(
        "triangle_strip",
        RenderSetup.builder(CustomRenderPipelines.TRIANGLE_STRIP)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .createRenderSetup()
    )

    val TRIANGLE_STRIP_ESP: RenderType = RenderType.create(
        "triangle_strip_esp",
        RenderSetup.builder(CustomRenderPipelines.TRIANGLE_STRIP_ESP)
            .createRenderSetup()
    )
}