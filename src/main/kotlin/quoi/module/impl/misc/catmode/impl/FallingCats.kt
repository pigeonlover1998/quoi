package quoi.module.impl.misc.catmode.impl

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier
import quoi.api.events.GuiEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.catmode.CatMode
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.rad
import quoi.utils.render.DrawContextUtils.drawImage
import quoi.utils.render.DrawContextUtils.withMatrix
import kotlin.random.Random

object FallingCats : ToggleableGroup(CatMode, "Falling cats", desc = "THEY'RE EVERYWHERE") {
    private val darken by switch("Darken", desc = "Makes the kittens darker so they don't distract you.")
    private val catTexture by selector("Type", CatImage.Trans, desc = "Texture used for the falling cats.")
    private val catSize by slider("Size", 15, 10, 50, 1, desc = "Size of the falling cats.", unit = "px")
    private val catSpeed by slider("Speed", 1.0f, 0.5f, 3.0f, 0.1f, desc = "Speed of the falling cats.")

    override val running: Boolean
        get() = super.running && inGame

    private val renderer = FallingCatsRenderer()

    init {
        on<GuiEvent.DrawBackground.Post> {
            if (!darken) renderer.draw(ctx, screen.width, screen.height, catTexture.selected.path, catSize, catSpeed)
        }

        on<GuiEvent.DrawBackground> {
            if (darken) renderer.draw(ctx, screen.width, screen.height, catTexture.selected.path, catSize, catSpeed)
        }
    }

    @Suppress("unused")
    private enum class CatImage {
        Trans,
        Flushed,
        Bread,
        Cut,
        Toast;

        val path = Identifier.parse("quoi:ui/fallingkittens/${name.lowercase()}.png")
    }

    private class FallingCatsRenderer {
        private val kittens = List(150) { Kitten() }

        fun draw(
            ctx: GuiGraphicsExtractor,
            width: Int,
            height: Int,
            texture: Identifier,
            size: Int,
            speedMultiplier: Float,
        ) {
            if (width <= 0 || height <= 0) return
            kittens.forEach {
                it.update(width, height, speedMultiplier)
                it.draw(ctx, texture, size)
            }
        }
    }

    private class Kitten {
        private var x = 0f
        private var y = 0f
        private val speed = Random.nextFloat() * 2f + 1f
        private var rotation = Random.nextFloat() * 360f
        private val rotationSpeed = Random.nextFloat() * 2f - 1f
        private var lastUpdateTime = System.nanoTime()
        private var initialised = false

        fun update(width: Int, height: Int, speedMultiplier: Float) {
            if (!initialised) {
                resetPosition(width, height)
                initialised = true
                lastUpdateTime = System.nanoTime()
                return
            }

            val currentTime = System.nanoTime()
            var deltaTime = (currentTime - lastUpdateTime) / 10_000_000.0f

            if (deltaTime > 250f) {
                resetPosition(width, height)
                deltaTime = 0f
            }

            lastUpdateTime = currentTime

            y += speed * deltaTime * 0.25f * speedMultiplier
            rotation += rotationSpeed * deltaTime * 0.2f

            if (y - 50 > height) resetPosition(width, height, isOffscreen = true)
        }

        fun draw(
            ctx: GuiGraphicsExtractor,
            texture: Identifier,
            size: Int,
        ) {
            val offset = size / 2f

            ctx.withMatrix(x - offset, y - offset) {
                ctx.pose().translate(offset, offset)
                ctx.pose().rotate(rotation.rad)
                ctx.pose().translate(-offset, -offset)

                ctx.drawImage(texture, 0, 0, size, size)
            }
        }

        private fun resetPosition(width: Int, height: Int, isOffscreen: Boolean = false) {
            x = Random.nextFloat() * width
            y = if (isOffscreen) -15f else Random.nextFloat() * height
        }
    }
}