package quoi.module.settings.impl

import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.AspectRatio
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.module.settings.Saving
import quoi.module.settings.UISetting
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.elements.switch
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive


class BooleanSetting(
    name: String,
    override val default: Boolean = false,
    desc: String = "",
) : UISetting<Boolean>(name, desc), Saving{

    override var value: Boolean = default
    var enabled: Boolean by this::value

    override fun write(): JsonElement = JsonPrimitive(enabled)

    override fun read(element: JsonElement) {
        enabled = element.asBoolean
    }

    override fun ElementScope<*>.draw(asSub: Boolean): ElementScope<*> = row(size(Copying), gap = 4.px) {
        val col = Colour.Animated(
            from = theme.panel,
            to = theme.accentBrighter,
            swapIf = value
        )

        onValueChanged {
            col.animate(0.25.seconds, Animation.Style.EaseInOutQuint)
        }

        fun label() = text(
            string = name,
            size = theme.textSize,
            colour = theme.textSecondary,
            pos = at(y = Centre)
        )

        val constraints =
            if (asSub) size(w = AspectRatio(1f), h = 15.px)
            else constrain(x = 0.px.alignOpposite, w = AspectRatio(1f), h = 20.px)

        if (asSub) {
            block(
                size(w = AspectRatio(1f), h = 15.px),
                colour = col,
                radius = 5.radius()
            ) {
                outline(theme.accent, 2.px)
                hoverEffect(factor = 1.15f)

                onClick {
                    value = !value
                }
            }
            label()
        } else {
            label()
            switch(::value, size = 20.px, pos = at(x = 0.px.alignOpposite, y = Centre))
        }
    }

}