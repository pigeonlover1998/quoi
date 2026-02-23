package quoi.utils.render

import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.utils.ChatUtils.literal
import quoi.utils.StringUtils.textRenderer
import quoi.utils.rad
import quoi.utils.ui.data.Gradient
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.entity.player.PlayerSkin
import org.joml.Matrix3x2f
import java.util.Optional
import java.util.UUID
import kotlin.math.*

object DrawContextUtils {

    private val textureCache = mutableMapOf<UUID, PlayerSkin>()
    private var lastCacheClear = System.currentTimeMillis()

    fun GuiGraphics.withMatrix(x: Number? = null, y: Number? = null, scale: Number? = null, block: () -> Unit) {
        require((x == null && y == null) || (x != null && y != null)) {
            "x and y must either both be null or both be not null"
        }

        pose().pushMatrix()
        if (x != null && y != null) {
            pose().translate(x.toFloat(), y.toFloat())
        }
        scale?.let { pose().scale(it.toFloat(), it.toFloat()) }
        block()
        pose().popMatrix()
    }

    fun GuiGraphics.gradientRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        colourStart: Int,
        colourEnd: Int,
        direction: Gradient = Gradient.TopToBottom,
    ) {
        if (direction == Gradient.TopToBottom) {
            fillGradient(x, y, x + width, y + height, colourStart, colourEnd)
        } else {
            withMatrix(x + width / 2f, y + height / 2f) {
                pose().rotate((-90).rad)

                val halfWidth = width / 2
                val halfHeight = height / 2
                fillGradient(-halfHeight, -halfWidth, halfHeight, halfWidth, colourStart, colourEnd)
            }
        }
    }

    fun GuiGraphics.rect(x: Number, y: Number, width: Int, height: Int, colour: Int) {
        withMatrix(x, y) {
            fill(0, 0, width, height, colour)
        }
    }

    fun GuiGraphics.hollowRect(x: Int, y: Int, width: Int, height: Int, thickness: Int, colour: Int) {
        fill(x, y, x + width, y + thickness, colour)
        fill(x, y + height - thickness, x + width, y + height, colour)
        fill(x, y + thickness, x + thickness, y + height - thickness, colour)
        fill(x + width - thickness, y + thickness, x + width, y + height - thickness, colour)
    }

    fun GuiGraphics.dashedRect(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
        lineWidth: Int = 1,
        dashLength: Int = 5,
        gapLength: Int = 3
    ) {
        drawDashedLine(x, y, x + width, y, color, lineWidth, dashLength, gapLength)
        drawDashedLine(x, y + height, x + width, y + height, color, lineWidth, dashLength, gapLength)
        drawDashedLine(x, y, x, y + height, color, lineWidth, dashLength, gapLength)
        drawDashedLine(x + width, y, x + width, y + height, color, lineWidth, dashLength, gapLength)
    }

    fun GuiGraphics.drawLine(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Int,
        lineWidth: Float = 1f
    ) {
        val dx = x2 - x1
        val dy = y2 - y1

        if (dx == 0f) {
            fill(x1.toInt(), min(y1, y2).toInt(), (x1 + lineWidth).toInt(), max(y1, y2).toInt(), color)
        } else if (dy == 0f) {
            fill(min(x1, x2).toInt(), y1.toInt(), max(x1, x2).toInt(), (y1 + lineWidth).toInt(), color)
        } else {
            val half = max(1, (lineWidth / 2f).toInt())

            withMatrix(x1, y1) {
                pose().mul(Matrix3x2f().identity().rotate(atan2(dy, dx)))
                fill(0, -half, ceil(hypot(dx, dy)).toInt(), half, color)
            }
        }
    }

    fun GuiGraphics.drawDashedLine(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Int,
        lineWidth: Int = 1,
        dashLength: Int = 5,
        gapLength: Int = 3
    ) {
        val dx = x2 - x1
        val dy = y2 - y1
        val totalLength = hypot(dx.toDouble(), dy.toDouble())
        val angle = atan2(dy.toDouble(), dx.toDouble())

        var drawn = 0.0
        while (drawn < totalLength) {
            val start = drawn
            val end = minOf(drawn + dashLength, totalLength)

            val sx = x1 + cos(angle) * start
            val sy = y1 + sin(angle) * start
            val ex = x1 + cos(angle) * end
            val ey = y1 + sin(angle) * end

            drawLine(sx.toFloat(), sy.toFloat(), ex.toFloat(), ey.toFloat(), color, lineWidth.toFloat())

            drawn += dashLength + gapLength
        }
    }

    fun GuiGraphics.pushScissor(x: Int, y: Int, width: Int, height: Int) {
        enableScissor(x, y, x + width, y + height)
    }

    fun GuiGraphics.drawString(text: Component, x: Number, y: Number, colour: Int = Colour.WHITE.rgb, scale: Float = 1f, shadow: Boolean = true) {
        withMatrix(x, y) {
            if (scale != 1f) pose().scale(scale, scale)
            drawString(textRenderer, text, 0, 0, colour, shadow)
        }
    }

    fun GuiGraphics.drawString(text: String, x: Number, y: Number, colour: Int = Colour.WHITE.rgb, scale: Float = 1f, shadow: Boolean = true)
        = drawString(literal(text), x, y, colour, scale, shadow)

    fun GuiGraphics.drawString(text: FormattedCharSequence, x: Number, y: Number, colour: Int = Colour.WHITE.rgb, scale: Float = 1f, shadow: Boolean = true) {
        withMatrix(x, y) {
            if (scale != 1f) pose().scale(scale, scale)
            drawString(textRenderer, text, 0, 0, colour, shadow)
        }
    }

    fun GuiGraphics.drawPlayerHead(uuid: UUID, x: Int, y: Int, size: Int) {
        val now = System.currentTimeMillis()
        if (now - lastCacheClear > 300000L) {
            textureCache.clear()
            lastCacheClear = now
        }

        val textures = textureCache.getOrElse(uuid) {
            val profile = mc.connection?.getPlayerInfo(uuid)?.profile
            val skin =
                if (profile != null) mc.skinManager.get(profile).getNow(Optional.empty()).orElseGet { DefaultPlayerSkin.get(uuid) }
                else DefaultPlayerSkin.get(uuid)

            val defaultSkin = DefaultPlayerSkin.get(uuid)
            if (skin.body.texturePath() != defaultSkin.body.texturePath()) textureCache[uuid] = skin
            skin
        }
        withMatrix {
            pose().rotate(180f.rad)
            PlayerFaceRenderer.draw(this, textures, x, y, size)
        }
    }

    fun GuiGraphics.drawImage(image: ResourceLocation, x: Int, y: Int, width: Int, height: Int) {
//        blitSprite(RenderPipelines.GUI_TEXTURED, image, x, y, width, height)
        blit(RenderPipelines.GUI_TEXTURED, image, x, y, 0f, 0f, width, height, width, height)
    }
}