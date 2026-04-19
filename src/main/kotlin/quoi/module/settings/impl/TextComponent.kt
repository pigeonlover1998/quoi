package quoi.module.settings.impl

import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.at
import quoi.api.abobaui.dsl.constrain
import quoi.api.abobaui.dsl.minus
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.elements.ElementScope
import quoi.module.settings.UIComponent
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.rendering.NVGRenderer
import quoi.utils.ui.rendering.NVGRenderer.defaultFont

class TextComponent(
    override val default: String,
    desc: String = "",
) : UIComponent<String>(default, desc) {

    override var value: String = default

    override fun ElementScope<*>.draw(asSub: Boolean): ElementScope<*> = group(size(w = Copying)) {

        if (children.isEmpty()) {
            val lines = NVGRenderer.wrapText(name, this@draw.element.width, theme.textSize.pixels, defaultFont)

            column(constrain(x = 0.px, y = Centre, w = Copying)) {
                lines.forEach {
                    text(
                        string = it,
                        size = theme.textSize,
                        colour = theme.onSurfaceVariant,
                    )
                }
            }
            return@group
        }

        text(
            string = name,
            size = theme.textSize,
            colour = theme.onSurfaceVariant,
            pos = at(x = 0.px, y = Centre)
        )
    }
}