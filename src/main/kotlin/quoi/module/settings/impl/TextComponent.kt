package quoi.module.settings.impl

import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.at
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.elements.ElementScope
import quoi.module.settings.UIComponent
import quoi.utils.ThemeManager.theme

class TextComponent(
    override val default: String,
    desc: String = "",
) : UIComponent<String>(default, desc) {

    override var value: String = default

    override fun ElementScope<*>.draw(asSub: Boolean): ElementScope<*> = group(size(w = Copying)) {
        text(
            string = name,
            size = theme.textSize,
            colour = theme.textSecondary,
            pos = at(x = 0.px, y = Centre)
        )
    }
}