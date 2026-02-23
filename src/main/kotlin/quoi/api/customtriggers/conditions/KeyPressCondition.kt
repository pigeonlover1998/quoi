package quoi.api.customtriggers.conditions

import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.customtriggers.TriggerContext
import quoi.api.input.CatKeyboard
import quoi.api.input.CatKeys
import quoi.config.TypeName
import quoi.utils.ThemeManager.theme

@TypeName("key_pressed")
class KeyPressCondition(var key: Int = CatKeys.KEY_NONE) : TriggerCondition {
    override fun matches(ctx: TriggerContext): Boolean {
        return ctx is TriggerContext.Key && ctx.key == key
    }

    override fun displayString() = "Key [${CatKeyboard.getKeyName(key)}] pressed"

    override fun ElementScope<*>.draw() = row(size(w = Copying)) {
        text(
            string = "Key",
            size = theme.textSize,
            colour = theme.textSecondary
        )
    }
}