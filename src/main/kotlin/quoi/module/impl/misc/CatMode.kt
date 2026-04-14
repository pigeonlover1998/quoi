package quoi.module.impl.misc

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.FormattedCharSequence
import quoi.api.events.GuiEvent
import quoi.api.events.PacketEvent
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.rad
import quoi.utils.render.DrawContextUtils.drawImage
import quoi.utils.render.DrawContextUtils.withMatrix
import kotlin.random.Random

/**
 * https://github.com/Noamm9/CatgirlAddons/blob/main/src/main/kotlin/catgirlroutes/module/impl/misc/CatMode.kt
 * https://github.com/Noamm9/CatgirlAddons/blob/main/src/main/kotlin/catgirlroutes/utils/render/FallingKittens.kt
 * https://github.com/jcnlk/quoi/blob/main/src/main/kotlin/quoi/module/impl/misc/CatMode.kt
 */
object CatMode : Module(
    "Cat Mode",
    desc = "MEOWMEOWMEOWMEOWMEOWMEOWMEOW"
) {
    private val meowSound by switch("Meowound", desc = "Meow sound everywhere")
    private val meowText by switch("Meow meow?", desc = "Meow everywhere")

    private val fallingCats by switch("Catocalypsis", desc = "THEY'RE EVERYWHERE")
    private val darken by switch("Darken", desc = "Makes the kittens darker so they don't distract you.").childOf(::fallingCats)
    private val catTexture by selector("Type", CatImage.Trans, desc = "Texture used for the falling cats.").childOf(::fallingCats)
    private val catSize by slider("Size", 15, 10, 50, 1, desc = "Size of the falling cats.", unit = "px").childOf(::fallingCats)
    private val catSpeed by slider("Speed", 1.0f, 0.5f, 3.0f, 0.1f, desc = "Speed of the falling cats.").childOf(::fallingCats)

//    private val customModel by switch("Kitty kitty") // todo finish
//    private val catModel by selector("Model", CatModel.Tabby).childOf(::customModel)
//    private val self by switch("I'm a cat").childOf(::customModel)
//    private val others by switch("He's a cat").childOf(::customModel)


    private val renderer = FallingCatsRenderer()

    init {
        on<GuiEvent.DrawBackground.Post> {
            if (!fallingCats || mc.level == null) return@on
            if (!darken) renderer.draw(ctx, screen.width, screen.height, catTexture.selected.path, catSize, catSpeed)
        }

        on<GuiEvent.DrawBackground> {
            if (!fallingCats || mc.level == null) return@on
            if (darken) renderer.draw(ctx, screen.width, screen.height, catTexture.selected.path, catSize, catSpeed)
        }

        on<PacketEvent.Received, ClientboundSoundPacket> {
            if (!meowSound || packet.sound == SoundEvents.CAT_AMBIENT) return@on

            cancel()
            mc.level?.playLocalSound(
                packet.x,
                packet.y,
                packet.z,
                SoundEvents.CAT_AMBIENT,
                packet.source,
                packet.volume,
                packet.pitch,
                false
            )
        }
    }

    @JvmStatic
    fun replaceText(text: String): String {
        if (!enabled || !meowText) return text
        return meowify(text)
    }

    @JvmStatic
    fun replaceText(text: FormattedCharSequence): FormattedCharSequence {
        if (!enabled || !meowText) return text

        val original = buildString {
            text.accept { _, _, codePoint ->
                appendCodePoint(codePoint)
                true
            }
        }
        val replaced = meowify(original)
        return if (replaced == original) text else Component.literal(replaced).visualOrderText
    }

    private fun meowify(text: String): String {
        if (text.isBlank()) return text

        val words = "\\S+".toRegex().findAll(text).count()
        return if (words == 0) text else List(words) { "meow" }.joinToString(" ")
    }

    private enum class CatImage {
        Trans,
        Flushed,
        Bread,
        Cut,
        Toast;

        val path = Identifier.parse("quoi:ui/fallingkittens/${name.lowercase()}.png")
    }

    private enum class CatModel(p: String) {
        AllBlack("all_black"),
        Black("black"),
        BritishShorthair("british_shorthair"),
        Calico("calico"),
        Jellie("jellie"),
        Persian("persian"),
        Ragdoll("ragdoll"),
        Red("red"),
        Siamese("siamese"),
        Tabby("tabby"),
        White("white");

        val path = Identifier.withDefaultNamespace("textures/entity/cat/$p.png")
    }

    private class FallingCatsRenderer {
        private val kittens = List(150) { Kitten() }

        fun draw(
            ctx: GuiGraphics,
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
            ctx: GuiGraphics,
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