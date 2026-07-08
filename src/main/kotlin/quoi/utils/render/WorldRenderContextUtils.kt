package quoi.utils.render

import com.mojang.blaze3d.vertex.ByteBufferBuilder
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3fc
import org.joml.Vector3f
import quoi.QuoiMod.mc
import quoi.api.colour.*
import quoi.utils.EntityUtils.renderPos
import quoi.utils.player
import quoi.utils.skyblock.player.PlayerUtils.eyeHeight
import quoi.utils.unaryMinus
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tan

/**
 * from OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: no longer exists, fuck off
 *
 * from jcnlk's quoi
 * original: https://github.com/jcnlk/quoi/blob/26.1.x/src/main/kotlin/quoi/utils/render/WorldRenderContextUtils.kt
 */
private val ALLOCATOR = ByteBufferBuilder(1536)

private fun camera() = mc.gameRenderer.mainCamera

private fun VertexConsumer.addQuad(
    pose: com.mojang.blaze3d.vertex.PoseStack.Pose,
    a: Vec3,
    b: Vec3,
    c: Vec3,
    d: Vec3,
    colour: Colour
) {
    addVertex(pose, a.x.toFloat(), a.y.toFloat(), a.z.toFloat()).setColor(colour.rgb)
    addVertex(pose, b.x.toFloat(), b.y.toFloat(), b.z.toFloat()).setColor(colour.rgb)
    addVertex(pose, c.x.toFloat(), c.y.toFloat(), c.z.toFloat()).setColor(colour.rgb)
    addVertex(pose, d.x.toFloat(), d.y.toFloat(), d.z.toFloat()).setColor(colour.rgb)
}

private fun Vector3fc.toVec3() = Vec3(x().toDouble(), y().toDouble(), z().toDouble())

private fun lineHalfWidth(cameraPos: Vec3, point: Vec3, thickness: Float): Double {
    val camera = camera()
    val depth = point.subtract(cameraPos).dot(camera.forwardVector().toVec3()).coerceAtLeast(0.05)
    val fovDegrees = (mc.options.fov().get() as Number).toDouble()
    val halfFovRadians = Math.toRadians(fovDegrees) / 2.0
    return ((thickness / 2.0) * (2.0 * depth * tan(halfFovRadians)) / mc.window.height.toDouble()).coerceAtLeast(0.0015)
}

private fun perpendicular(direction: Vec3): Vec3 {
    val camera = camera()
    val left = camera.leftVector().toVec3()
    val up = camera.upVector().toVec3()
    val normalizedDirection = direction.normalize()
    val screenX = normalizedDirection.dot(left)
    val screenY = normalizedDirection.dot(up)
    val perpendicular = left.scale(-screenY).add(up.scale(screenX))

    return if (perpendicular.lengthSqr() > 1.0E-6) perpendicular.normalize() else up
}

private fun addLineSegment(buffer: VertexConsumer, pose: com.mojang.blaze3d.vertex.PoseStack.Pose, start: Vec3, end: Vec3, cameraPos: Vec3, colour: Colour, thickness: Float) {
    val direction = end.subtract(start)
    if (direction.lengthSqr() <= 1.0E-6) return

    val primary = perpendicular(direction)
    val secondary = direction.cross(primary).normalize()
    val startPrimary = primary.scale(lineHalfWidth(cameraPos, start, thickness))
    val endPrimary = primary.scale(lineHalfWidth(cameraPos, end, thickness))
    val startSecondary = secondary.scale(lineHalfWidth(cameraPos, start, thickness))
    val endSecondary = secondary.scale(lineHalfWidth(cameraPos, end, thickness))
    val translatedStart = start.subtract(cameraPos)
    val translatedEnd = end.subtract(cameraPos)

    // Match the old lineWidth(thickness) behavior by keeping width in screen space instead of world space.
    buffer.addQuad(pose, translatedStart.subtract(startPrimary), translatedStart.add(startPrimary), translatedEnd.add(endPrimary), translatedEnd.subtract(endPrimary), colour)
    buffer.addQuad(pose, translatedStart.subtract(startSecondary), translatedStart.add(startSecondary), translatedEnd.add(endSecondary), translatedEnd.subtract(endSecondary), colour)
}

private fun boxEdges(box: AABB): List<Pair<Vec3, Vec3>> {
    val minX = box.minX
    val minY = box.minY
    val minZ = box.minZ
    val maxX = box.maxX
    val maxY = box.maxY
    val maxZ = box.maxZ

    val x0y0z0 = Vec3(minX, minY, minZ)
    val x0y0z1 = Vec3(minX, minY, maxZ)
    val x0y1z0 = Vec3(minX, maxY, minZ)
    val x0y1z1 = Vec3(minX, maxY, maxZ)
    val x1y0z0 = Vec3(maxX, minY, minZ)
    val x1y0z1 = Vec3(maxX, minY, maxZ)
    val x1y1z0 = Vec3(maxX, maxY, minZ)
    val x1y1z1 = Vec3(maxX, maxY, maxZ)

    return listOf(
        x0y0z0 to x1y0z0,
        x0y0z1 to x1y0z1,
        x0y1z0 to x1y1z0,
        x0y1z1 to x1y1z1,
        x0y0z0 to x0y1z0,
        x1y0z0 to x1y1z0,
        x0y0z1 to x0y1z1,
        x1y0z1 to x1y1z1,
        x0y0z0 to x0y0z1,
        x1y0z0 to x1y0z1,
        x0y1z0 to x0y1z1,
        x1y1z0 to x1y1z1
    )
}

private fun legacyDistanceScaledThickness(cameraPos: Vec3, reference: Vec3, thickness: Float): Float {
    val distanceSqr = max(cameraPos.distanceToSqr(reference), 1.0)
    return (thickness / distanceSqr.pow(0.15)).toFloat()
}

private fun addFilledBox(buffer: VertexConsumer, pose: com.mojang.blaze3d.vertex.PoseStack.Pose, box: AABB, cameraPos: Vec3, colour: Colour) {
    val minX = box.minX - cameraPos.x
    val minY = box.minY - cameraPos.y
    val minZ = box.minZ - cameraPos.z
    val maxX = box.maxX - cameraPos.x
    val maxY = box.maxY - cameraPos.y
    val maxZ = box.maxZ - cameraPos.z

    val x0y0z0 = Vec3(minX, minY, minZ)
    val x0y0z1 = Vec3(minX, minY, maxZ)
    val x0y1z0 = Vec3(minX, maxY, minZ)
    val x0y1z1 = Vec3(minX, maxY, maxZ)
    val x1y0z0 = Vec3(maxX, minY, minZ)
    val x1y0z1 = Vec3(maxX, minY, maxZ)
    val x1y1z0 = Vec3(maxX, maxY, minZ)
    val x1y1z1 = Vec3(maxX, maxY, maxZ)

    buffer.addQuad(pose, x0y0z0, x0y1z0, x1y1z0, x1y0z0, colour)
    buffer.addQuad(pose, x1y0z1, x1y1z1, x0y1z1, x0y0z1, colour)
    buffer.addQuad(pose, x0y0z1, x0y1z1, x0y1z0, x0y0z0, colour)
    buffer.addQuad(pose, x1y0z0, x1y1z0, x1y1z1, x1y0z1, colour)
    buffer.addQuad(pose, x0y1z0, x0y1z1, x1y1z1, x1y1z0, colour)
    buffer.addQuad(pose, x0y0z1, x0y0z0, x1y0z0, x1y0z1, colour)
}

fun LevelRenderContext.drawLine(points: Collection<Vec3>, colour: Colour, depth: Boolean = false, thickness: Float = 3f) {
    if (points.size < 2) return
    val matrix = poseStack()
    val bufferSource = bufferSource()
    val layer = if (depth) CustomRenderLayer.TRIANGLE_STRIP else CustomRenderLayer.TRIANGLE_STRIP_ESP
    val cameraPos = camera().position()
    val pose = matrix.last()
    val buffer = bufferSource.getBuffer(layer)
    val pointList = points.toList()

    for (i in 0 until pointList.size - 1) {
        addLineSegment(buffer, pose, pointList[i], pointList[i + 1], cameraPos, colour, thickness)
    }

    bufferSource.endBatch(layer)
}

fun LevelRenderContext.drawTracer(to: Vec3, colour: Colour, thickness: Float = 6f, depth: Boolean = false) {
    val from = player.renderPos.add(player.forward.add(0.0, player.eyeHeight().toDouble(), 0.0))
    drawLine(listOf(from, to), colour, depth, thickness)
}

fun LevelRenderContext.drawWireFrameBox(aabb: AABB, colour: Colour, thickness: Float = 6f, depth: Boolean = false) {
    val matrix = poseStack()
    val bufferSource = bufferSource()
    val layer = if (depth) CustomRenderLayer.TRIANGLE_STRIP else CustomRenderLayer.TRIANGLE_STRIP_ESP
    val cameraPos = camera().position()
    val pose = matrix.last()
    val buffer = bufferSource.getBuffer(layer)
    val legacyThickness = legacyDistanceScaledThickness(cameraPos, aabb.center, thickness)

    boxEdges(aabb).forEach { (start, end) ->
        addLineSegment(buffer, pose, start, end, cameraPos, colour, legacyThickness)
    }

    bufferSource.endBatch(layer)
}

fun LevelRenderContext.drawFilledBox(box: AABB, colour: Colour, depth: Boolean = false) {
    val matrix = poseStack()
    val bufferSource = bufferSource()
    val layer = if (depth) CustomRenderLayer.TRIANGLE_STRIP else CustomRenderLayer.TRIANGLE_STRIP_ESP
    val cameraPos = camera().position()
    val pose = matrix.last()
    val buffer = bufferSource.getBuffer(layer)
    addFilledBox(buffer, pose, box, cameraPos, colour)

    bufferSource.endBatch(layer)
}

fun LevelRenderContext.drawStyledBox(style: String, box: AABB, colour: Colour, fillColour: Colour = colour, thickness: Float = 2.0f, depth: Boolean = false) {
    when (style) {
        "Box" -> drawWireFrameBox(box, colour, thickness, depth)
        "Filled box" -> {
            drawFilledBox(box, fillColour, depth)
            drawWireFrameBox(box, colour, thickness, depth)
        }
        "Filled" -> drawFilledBox(box, fillColour, depth)
    }
}

fun LevelRenderContext.drawText(text: Component, pos: Vec3, colour: Colour = Colour.TRANSPARENT, shadow: Boolean = true, scale: Float = 0.5f, depth: Boolean = false) {
    val stack = poseStack()

    stack.pushPose()
    val matrix = stack.last().pose()
    with(scale * 0.025f) {
        val cameraPos = -camera().position()
        matrix.translate(pos.toVector3f()).translate(cameraPos.x.toFloat(), cameraPos.y.toFloat(), cameraPos.z.toFloat()).rotate(camera().rotation()).scale(this, -this, this)
    }

    val consumers = MultiBufferSource.immediate(ALLOCATOR)

    mc.font.let {
        it.drawInBatch(
            text, -it.width(text) / 2f, 0f, -1, shadow, matrix, consumers,
            if (depth) Font.DisplayMode.NORMAL else Font.DisplayMode.SEE_THROUGH,
            colour.rgb, 15728880
        )
    }

    consumers.endBatch()
    stack.popPose()
}

fun LevelRenderContext.drawCylinder(
    center: Vec3,
    radius: Float,
    height: Float,
    colour: Colour,
    segments: Int = 32,
    thickness: Float = 5f,
    depth: Boolean = false
) {
    val matrix = poseStack()
    val bufferSource = bufferSource()
    val layer = if (depth) CustomRenderLayer.TRIANGLE_STRIP else CustomRenderLayer.TRIANGLE_STRIP_ESP
    val cameraPos = camera().position()
    val pose = matrix.last()
    val buffer = bufferSource.getBuffer(layer)
    val angleStep = 2.0 * Math.PI / segments
    val legacyThickness = legacyDistanceScaledThickness(cameraPos, center, thickness)

    for (i in 0 until segments) {
        val angle1 = i * angleStep
        val angle2 = (i + 1) * angleStep

        val x1 = center.x + radius * kotlin.math.cos(angle1)
        val z1 = center.z + radius * kotlin.math.sin(angle1)
        val x2 = center.x + radius * kotlin.math.cos(angle2)
        val z2 = center.z + radius * kotlin.math.sin(angle2)
        val topY = center.y + height

        val topStart = Vec3(x1, topY, z1)
        val topEnd = Vec3(x2, topY, z2)
        val bottomStart = Vec3(x1, center.y, z1)
        val bottomEnd = Vec3(x2, center.y, z2)

        addLineSegment(buffer, pose, topStart, topEnd, cameraPos, colour, legacyThickness)
        addLineSegment(buffer, pose, bottomStart, bottomEnd, cameraPos, colour, legacyThickness)
        addLineSegment(buffer, pose, bottomStart, topStart, cameraPos, colour, legacyThickness)
    }

    bufferSource.endBatch(layer)
}