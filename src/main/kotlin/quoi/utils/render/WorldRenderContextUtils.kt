package quoi.utils.render

import quoi.QuoiMod.mc
import quoi.api.colour.*
import quoi.utils.EntityUtils.renderPos
import quoi.utils.unaryMinus
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
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

    matrix.pushPose()
    with(camera().position()) { matrix.translate(-x, -y, -z) }

    val pointList = points.toList()
    val buffer = bufferSource.getBuffer(layer)
    for (i in 0 until pointList.size - 1) {
        val start = pointList[i]
        val end = pointList[i + 1]
        val dir = end.subtract(start)
        renderVector(matrix, buffer, Vector3f(start.x.toFloat(), start.y.toFloat(), start.z.toFloat()), dir, colour.rgb, thickness)
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
    val camera = camera()

    // La largeur est passée directement à setLineWidth() via addLine/renderLineBox
    val scaledThickness = (thickness / camera.position().distanceToSqr(aabb.center).pow(0.15)).toFloat()

    matrix.pushPose()
    with(camera.position()) { matrix.translate(-x, -y, -z) }
    renderLineBox(
        matrix.last(),
        bufferSource.getBuffer(layer),
        aabb.minX, aabb.minY, aabb.minZ,
        aabb.maxX, aabb.maxY, aabb.maxZ,
        colour.redFloat,
        colour.greenFloat,
        colour.blueFloat,
        colour.alphaFloat,
        scaledThickness
    )

    matrix.popPose()
    bufferSource.endBatch(layer)
}

fun WorldRenderContext.drawFilledBox(box: AABB, colour: Colour, depth: Boolean = false) {
    val matrix = matrices() ?: return
    val bufferSource = consumers() as? MultiBufferSource.BufferSource ?: return
    val layer = if (depth) CustomRenderLayer.TRIANGLE_STRIP else CustomRenderLayer.TRIANGLE_STRIP_ESP

    matrix.pushPose()
    with(camera().position()) { matrix.translate(-x, -y, -z) }
    addFilledBoxVertices(
        matrix.last(),
        bufferSource.getBuffer(layer),
        box.minX, box.minY, box.minZ,
        box.maxX, box.maxY, box.maxZ,
        colour.redFloat,
        colour.greenFloat,
        colour.blueFloat,
        colour.alphaFloat
    )

    matrix.popPose()
    bufferSource.endBatch(layer)
}

fun WorldRenderContext.drawStyledBox(style: String, box: AABB, colour: Colour, fillColour: Colour = colour, thickness: Float = 2.0f, depth: Boolean = false) {
    when (style) {
        "Box" -> drawWireFrameBox(box, colour, thickness, depth)
        "Filled box" -> {
            drawFilledBox(box, fillColour, depth)
            drawWireFrameBox(box, colour, thickness, depth)
        }
    }
}

fun WorldRenderContext.drawText(text: Component, pos: Vec3, colour: Colour = Colour.TRANSPARENT, shadow: Boolean = true, scale: Float = 0.5f, depth: Boolean = false) {
    val stack = matrices() ?: return

    stack.pushPose()
    val matrix = stack.last().pose()
    with(scale * 0.025f) {
        val cameraPos = -camera().position()
        matrix.translate(pos.toVector3f()).translate(cameraPos.x.toFloat(), cameraPos.y.toFloat(), cameraPos.z.toFloat()).rotate(camera().rotation()).scale(this, -this, this)
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
    val camera = camera()
    val cameraPos = camera.position()

    // Largeur scalée selon la distance, passée à setLineWidth() via renderVector
    val scaledThickness = (thickness / cameraPos.distanceToSqr(center).pow(0.15)).toFloat()

    matrix.pushPose()
    matrix.translate(center.x - cameraPos.x, center.y - cameraPos.y, center.z - cameraPos.z)

    val angleStep = 2.0 * Math.PI / segments
    val buffer = bufferSource.getBuffer(layer)

    for (i in 0 until segments) {
        val angle1 = i * angleStep
        val angle2 = (i + 1) * angleStep

        val x1 = (radius * kotlin.math.cos(angle1)).toFloat()
        val z1 = (radius * kotlin.math.sin(angle1)).toFloat()
        val x2 = (radius * kotlin.math.cos(angle2)).toFloat()
        val z2 = (radius * kotlin.math.sin(angle2)).toFloat()

        renderVector(matrix, buffer, Vector3f(x1, height, z1), Vec3((x2 - x1).toDouble(), 0.0, (z2 - z1).toDouble()), colour.rgb, scaledThickness)
        renderVector(matrix, buffer, Vector3f(x1, 0f, z1), Vec3((x2 - x1).toDouble(), 0.0, (z2 - z1).toDouble()), colour.rgb, scaledThickness)
        renderVector(matrix, buffer, Vector3f(x1, 0f, z1), Vec3(0.0, height.toDouble(), 0.0), colour.rgb, scaledThickness)
    }

    matrix.popPose()
    bufferSource.endBatch(layer)
}

// ─── helpers privés ──────────────────────────────────────────────────────────

private fun renderVector(matrix: PoseStack, buffer: VertexConsumer, start: Vector3f, direction: Vec3, rgb: Int, lineWidth: Float = 3f) {
    val end = Vector3f(
        start.x + direction.x.toFloat(),
        start.y + direction.y.toFloat(),
        start.z + direction.z.toFloat()
    )
    val r = ((rgb shr 16) and 0xFF) / 255f
    val g = ((rgb shr 8)  and 0xFF) / 255f
    val b = (rgb           and 0xFF) / 255f
    addLine(buffer, matrix.last(), start.x, start.y, start.z, end.x, end.y, end.z, r, g, b, 1f, lineWidth)
}

private fun addFilledBoxVertices(
    pose: PoseStack.Pose, buffer: VertexConsumer,
    x1: Double, y1: Double, z1: Double,
    x2: Double, y2: Double, z2: Double,
    r: Float, g: Float, b: Float, a: Float
) {
    val minX = x1.toFloat(); val minY = y1.toFloat(); val minZ = z1.toFloat()
    val maxX = x2.toFloat(); val maxY = y2.toFloat(); val maxZ = z2.toFloat()

    addQuad(buffer, pose, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a)
    addQuad(buffer, pose, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a)
    addQuad(buffer, pose, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a)
    addQuad(buffer, pose, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a)
    addQuad(buffer, pose, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a)
    addQuad(buffer, pose, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a)
}

private fun addVertex(buffer: VertexConsumer, pose: PoseStack.Pose, x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, a: Float) {
    buffer.addVertex(pose, x, y, z).setColor(r, g, b, a)
}

private fun addQuad(
    buffer: VertexConsumer, pose: PoseStack.Pose,
    x1: Float, y1: Float, z1: Float,
    x2: Float, y2: Float, z2: Float,
    x3: Float, y3: Float, z3: Float,
    x4: Float, y4: Float, z4: Float,
    r: Float, g: Float, b: Float, a: Float
) {
    addVertex(buffer, pose, x1, y1, z1, r, g, b, a)
    addVertex(buffer, pose, x2, y2, z2, r, g, b, a)
    addVertex(buffer, pose, x3, y3, z3, r, g, b, a)
    addVertex(buffer, pose, x1, y1, z1, r, g, b, a)
    addVertex(buffer, pose, x3, y3, z3, r, g, b, a)
    addVertex(buffer, pose, x4, y4, z4, r, g, b, a)
}

private fun renderLineBox(
    pose: PoseStack.Pose, buffer: VertexConsumer,
    x1: Double, y1: Double, z1: Double,
    x2: Double, y2: Double, z2: Double,
    r: Float, g: Float, b: Float, a: Float,
    lineWidth: Float
) {
    val minX = x1.toFloat(); val minY = y1.toFloat(); val minZ = z1.toFloat()
    val maxX = x2.toFloat(); val maxY = y2.toFloat(); val maxZ = z2.toFloat()

    // bottom
    addLine(buffer, pose, minX, minY, minZ, maxX, minY, minZ, r, g, b, a, lineWidth)
    addLine(buffer, pose, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a, lineWidth)
    addLine(buffer, pose, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a, lineWidth)
    addLine(buffer, pose, minX, minY, maxZ, minX, minY, minZ, r, g, b, a, lineWidth)
    // top
    addLine(buffer, pose, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a, lineWidth)
    addLine(buffer, pose, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a, lineWidth)
    addLine(buffer, pose, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a, lineWidth)
    addLine(buffer, pose, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a, lineWidth)
    // verticals
    addLine(buffer, pose, minX, minY, minZ, minX, maxY, minZ, r, g, b, a, lineWidth)
    addLine(buffer, pose, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a, lineWidth)
    addLine(buffer, pose, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a, lineWidth)
    addLine(buffer, pose, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a, lineWidth)
}

private fun addLine(
    buffer: VertexConsumer, pose: PoseStack.Pose,
    x1: Float, y1: Float, z1: Float,
    x2: Float, y2: Float, z2: Float,
    r: Float, g: Float, b: Float, a: Float,
    lineWidth: Float
) {
    val normal = Vector3f(x2 - x1, y2 - y1, z2 - z1)
    if (normal.lengthSquared() > 0f) normal.normalize()
    buffer.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, normal).setLineWidth(lineWidth)
    buffer.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, normal).setLineWidth(lineWidth)
}