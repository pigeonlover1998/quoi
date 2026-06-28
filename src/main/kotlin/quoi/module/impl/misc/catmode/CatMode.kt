package quoi.module.impl.misc.catmode

import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.FormattedCharSequence
import quoi.api.events.PacketEvent
import quoi.api.events.core.on
import quoi.module.Module
import quoi.module.impl.misc.catmode.impl.*

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

    @Suppress("unused")
    private val feat = listOf(
        FallingCats, //CatModel
    )

    init {
        on<PacketEvent.Received, ClientboundSoundPacket> {
            if (!meowSound || packet.sound == SoundEvents.CAT_AMBIENT_BABY) return@on

            cancel()
            level.playLocalSound(
                packet.x,
                packet.y,
                packet.z,
                SoundEvents.CAT_AMBIENT_BABY.value(),
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
}