package quoi.utils.ui.rendering

import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState
import org.joml.Matrix3x2f
import org.lwjgl.opengl.GL33C
import java.util.OptionalInt

/**
 * from OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/ui/rendering/NVGPIPRenderer.kt
 */
class NVGSpecialRenderer(vertexConsumers: MultiBufferSource.BufferSource)
    : PictureInPictureRenderer<NVGSpecialRenderer.NVGRenderState>(vertexConsumers) {

    private var stencilRenderBuffer = 0
    private var stencilWidth = 0
    private var stencilHeight = 0

    override fun renderToTexture(state: NVGRenderState, poseStack: PoseStack) {
        val colorView = RenderSystem.outputColorTextureOverride ?: return
        val width = colorView.getWidth(0)
        val height = colorView.getHeight(0)

        RenderSystem.getDevice().createCommandEncoder()
            .createRenderPass({ "quoi_nvg_renderer" }, colorView, OptionalInt.empty())
            .use {
                attachStencilBuffer(width, height)
                GL33C.glBindSampler(0, 0)
                NVGRenderer.beginFrame(width.toFloat(), height.toFloat())
                state.renderContent()
                NVGRenderer.endFrame()
            }

        GlStateManager._disableDepthTest()
        GlStateManager._disableCull()
        GlStateManager._enableBlend()
        GlStateManager._blendFuncSeparate(770, 771, 1, 0)
    }

    override fun getTranslateY(height: Int, windowScaleFactor: Int): Float = height / 2f
    override fun getRenderStateClass(): Class<NVGRenderState> = NVGRenderState::class.java
    override fun getTextureLabel(): String = "nvg_renderer"

    override fun close() {
        super.close()
        if (stencilRenderBuffer != 0) {
            GL33C.glDeleteRenderbuffers(stencilRenderBuffer)
            stencilRenderBuffer = 0
        }
    }

    private fun attachStencilBuffer(width: Int, height: Int) {
        if (stencilRenderBuffer == 0) {
            stencilRenderBuffer = GL33C.glGenRenderbuffers()
        }

        GL33C.glBindRenderbuffer(GL33C.GL_RENDERBUFFER, stencilRenderBuffer)
        if (stencilWidth != width || stencilHeight != height) {
            GL33C.glRenderbufferStorage(GL33C.GL_RENDERBUFFER, GL33C.GL_STENCIL_INDEX8, width, height)
            stencilWidth = width
            stencilHeight = height
        }
        GL33C.glFramebufferRenderbuffer(
            GL33C.GL_FRAMEBUFFER,
            GL33C.GL_STENCIL_ATTACHMENT,
            GL33C.GL_RENDERBUFFER,
            stencilRenderBuffer
        )
        GL33C.glClear(GL33C.GL_STENCIL_BUFFER_BIT)
    }

    data class NVGRenderState(
        private val x: Int,
        private val y: Int,
        private val width: Int,
        private val height: Int,
        private val scissor: ScreenRectangle?,
        private val bounds: ScreenRectangle?,
        val renderContent: () -> Unit
    ) : PictureInPictureRenderState {

        override fun scale(): Float = 1f
        override fun x0(): Int = x
        override fun y0(): Int = y
        override fun x1(): Int = x + width
        override fun y1(): Int = y + height
        override fun scissorArea(): ScreenRectangle? = scissor
        override fun bounds(): ScreenRectangle? = bounds
    }

    companion object {
        fun draw(
            context: GuiGraphicsExtractor,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            renderContent: () -> Unit
        ) {
            val scissor = context.scissorStack.peek()
            val pose = Matrix3x2f(context.pose())
            val bounds = createBounds(x, y, x + width, y + height, pose, scissor)

            val state = NVGRenderState(
                x, y, width, height,
                scissor, bounds,
                renderContent
            )
            context.guiRenderState.addPicturesInPictureState(state)
        }

        private fun createBounds(x0: Int, y0: Int, x1: Int, y1: Int, pose: Matrix3x2f, scissorArea: ScreenRectangle?): ScreenRectangle? =
            ScreenRectangle(x0, y0, x1 - x0, y1 - y0)
                .transformMaxBounds(pose)
                .let { if (scissorArea != null) scissorArea.intersection(it) else it }
    }
}