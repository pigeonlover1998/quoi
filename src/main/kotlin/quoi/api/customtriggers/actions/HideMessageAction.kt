package quoi.api.customtriggers.actions

import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.TriggerContext
import quoi.config.TypeName
import quoi.utils.ThemeManager.theme

@TypeName("hide_message")
class HideMessageAction : TriggerAction {
    override fun execute(ctx: TriggerContext) {
        if (ctx is TriggerContext.Chat) ctx.cancelled = true
    }

    override fun displayString() = "Hide message"

    override fun ElementScope<*>.draw() = column(size(w = Copying)) {
        text(
            string = "Hides message",
            size = theme.textSize,
            colour = theme.textSecondary,
        )
    }
}