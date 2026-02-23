package quoi.api.customtriggers.conditions

import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.TriggerContext
import quoi.config.TypeName
import quoi.utils.ThemeManager.theme

@TypeName("sound_played")
class SoundCondition(
    var soundName: String = "",
    var volume: Float? = null,
    var pitch: Float? = null
) : TriggerCondition {
    override fun matches(ctx: TriggerContext): Boolean {
        if (ctx !is TriggerContext.Sound) return false

        if (ctx.name != soundName) return false
        if (volume != null && ctx.volume != volume) return false
        if (pitch != null && ctx.pitch != pitch) return false

        return true
    }

    override fun displayString() = buildString {
        append("Sound \"$soundName\"")

        val params = listOfNotNull(
            volume?.let { "volume = $it" },
            pitch?.let { "pitch = $it" }
        )

        if (params.isNotEmpty()) {
            append("(${params.joinToString(", ")})")
        }

        append(" played")
    }

    override fun ElementScope<*>.draw() = column(size(w = Copying)) {
        text(
            string = "Sound",
            size = theme.textSize,
            colour = theme.textSecondary,
        )
    }
}