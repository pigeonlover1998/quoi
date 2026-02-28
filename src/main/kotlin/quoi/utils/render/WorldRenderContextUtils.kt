package quoi.utils.render

import quoi.QuoiMod.mc
import quoi.api.colour.*
import quoi.utils.EntityUtils.renderPos
import quoi.utils.unaryMinus
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.ShapeRenderer
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.pow

/**
 * from OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: no longer exists, fuck off
 */
private val ALLOCATOR = ByteBufferBuilder(1536)

private fun camera() = mc.gameRenderer.mainCamera

fun WorldRenderContext.drawLine(points: Collection<Vec3>, colour: Colour, depth: Boolean, thickness: Float = 3f) {
    if (points.size < 2) return
    val matrix = matrices() ?: return
    val bufferSource = consumers() as? MultiBufferSource.BufferSource ?: return
    val layer = if (depth) CustomRenderLayer.LINE_LIST else CustomRenderLayer.LINE_LIST_ESP
    RenderSystem.lineWidth(thickness)

    matrix.pushPose()
    with(camera().position) { matrix.translate(-x, -y, -z) }

    val pointList = points.toList()
    for (i in 0 until pointList.size - 1) {
        val start = pointList[i]
        val end = pointList[i + 1]
        val startOffset = Vector3f(start.x.toFloat(), start.y.toFloat(), start.z.toFloat())
        val direction = end.subtract(start)
        ShapeRenderer.renderVector(
            matrix,
            bufferSource.getBuffer(layer),
            startOffset,
            direction,
            colour.rgb
        )
    }

    matrix.popPose()
    bufferSource.endBatch(layer)
}

fun WorldRenderContext.drawTracer(to: Vec3, colour: Colour, thickness: Float = 6f, depth: Boolean = false) {
    val from = mc.player?.let { player ->
        player.renderPos.add(player.forward.add(0.0, player.eyeHeight.toDouble(), 0.0))
    } ?: return
    drawLine(listOf(from, to), colour, depth, thickness)
}

fun WorldRenderContext.drawWireFrameBox(aabb: AABB, colour: Colour, thickness: Float = 6f, depth: Boolean = false) {
    val matrix = matrices() ?: return
    val bufferSource = consumers() as? MultiBufferSource.BufferSource ?: return
    val layer = if (depth) CustomRenderLayer.LINE_LIST else CustomRenderLayer.LINE_LIST_ESP
    val camera = camera() ?: return
    RenderSystem.lineWidth((thickness / camera.position.distanceToSqr(aabb.center).pow(0.15)).toFloat())

    matrix.pushPose()
    with(camera.position) { matrix.translate(-x, -y, -z) }
    ShapeRenderer.renderLineBox(
        matrix.last(),
        bufferSource.getBuffer(layer),
        aabb,
        colour.redFloat,
        colour.greenFloat,
        colour.blueFloat,
        colour.alphaFloat
    )

    matrix.popPose()
    bufferSource.endBatch(layer)
}

fun WorldRenderContext.drawFilledBox(box: AABB, colour: Colour, depth: Boolean = false) {
    val matrix = matrices() ?: return
    val bufferSource = consumers() as? MultiBufferSource.BufferSource ?: return
    val layer = if (depth) CustomRenderLayer.TRIANGLE_STRIP else CustomRenderLayer.TRIANGLE_STRIP_ESP

    matrix.pushPose()
    with(camera().position) { matrix.translate(-x, -y, -z) }
    ShapeRenderer.addChainedFilledBoxVertices(
        matrix,
        bufferSource.getBuffer(layer),
        box.minX,
        box.minY,
        box.minZ,
        box.maxX,
        box.maxY,
        box.maxZ,
        colour.redFloat,
        colour.greenFloat,
        colour.blueFloat,
        colour.alphaFloat
    )

    matrix.popPose()
    bufferSource.endBatch(layer)
}

fun WorldRenderContext.drawStyledBox(style: String, box: AABB, colour: Colour, fillColour: Colour, thickness: Float, depth: Boolean) {
    when (style) {
        "Box" -> drawWireFrameBox(box, colour, thickness, depth)
        "Filled box" -> {
            drawFilledBox(box, fillColour, depth)
            drawWireFrameBox(box, colour, thickness, depth)
        }
    }
}

//fun WorldRenderContext.drawBeaconBeam(position: BlockPos, colour: Colour) {
//    val matrix = matrices() ?: return
//    val bufferSource = consumers() as? MultiBufferSource.BufferSource ?: return
//    val camera = camera()?.position ?: return
//
//    matrix.pushPose()
//    matrix.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z)
//    val length = camera.subtract(position.center).horizontalDistance().toFloat()
//    val scale = if (mc.player != null && mc.player?.isScoping == true) 1.0f else maxOf(1.0f, length / 96.0f)
//
//    BeaconRenderer.renderBeaconBeam(
//        matrix, bufferSource, BeaconRenderer.BEAM_LOCATION,
//        tickCounter().getGameTimeDeltaPartialTick(true), scale, world().gameTime, 0, 319, colour.rgba, 0.2f * scale, 0.25f * scale
//    )
//    matrix.popPose()
//}

fun WorldRenderContext.drawText(text: Component, pos: Vec3, colour: Colour = Colour.TRANSPARENT, shadow: Boolean = true, scale: Float = 0.5f, depth: Boolean = false) {
    val stack = matrices() ?: return

    stack.pushPose()
    val matrix = stack.last().pose()
    with(scale * 0.025f) {
        val cameraPos = -camera().position
        matrix.translate(pos.toVector3f()).translate(cameraPos.x.toFloat() , cameraPos.y.toFloat(), cameraPos.z.toFloat()).rotate(camera().rotation()).scale(this, -this, this)
    }

    val consumers = MultiBufferSource.immediate(ALLOCATOR)

    mc.font?.let {
        it.drawInBatch(
            text, -it.width(text) / 2f, 0f, -1, shadow, matrix, consumers,
            if (depth) Font.DisplayMode.NORMAL else Font.DisplayMode.SEE_THROUGH,
            colour.rgb, LightTexture.FULL_BRIGHT
        )
    }

    consumers.endBatch()
    stack.popPose()
}

fun WorldRenderContext.drawCylinder(
    center: Vec3,
    radius: Float,
    height: Float,
    colour: Colour,
    segments: Int = 32,
    thickness: Float = 5f,
    depth: Boolean = false
) {
    val matrix = matrices() ?: return
    val bufferSource = consumers() as? MultiBufferSource.BufferSource ?: return
    val layer = if (depth) CustomRenderLayer.LINE_LIST else CustomRenderLayer.LINE_LIST_ESP
    val camera = camera()?.position ?: return

    matrix.pushPose()
    matrix.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z)
    RenderSystem.lineWidth((thickness / camera.distanceToSqr(center).pow(0.15)).toFloat())

    val angleStep = 2.0 * Math.PI / segments
    val buffer = bufferSource.getBuffer(layer)

    for (i in 0 until segments) {
        val angle1 = i * angleStep
        val angle2 = (i + 1) * angleStep

        val x1 = (radius * kotlin.math.cos(angle1)).toFloat()
        val z1 = (radius * kotlin.math.sin(angle1)).toFloat()
        val x2 = (radius * kotlin.math.cos(angle2)).toFloat()
        val z2 = (radius * kotlin.math.sin(angle2)).toFloat()

        ShapeRenderer.renderVector(matrix, buffer, Vector3f(x1, height, z1), Vec3((x2 - x1).toDouble(), 0.0, (z2 - z1).toDouble()), colour.rgb)
        ShapeRenderer.renderVector(matrix, buffer, Vector3f(x1, 0f, z1), Vec3((x2 - x1).toDouble(), 0.0, (z2 - z1).toDouble()), colour.rgb)
        ShapeRenderer.renderVector(matrix, buffer, Vector3f(x1, 0f, z1), Vec3(0.0, height.toDouble(), 0.0), colour.rgb)
    }


    matrix.popPose()
    bufferSource.endBatch()
}