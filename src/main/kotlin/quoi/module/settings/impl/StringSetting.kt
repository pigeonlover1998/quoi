package quoi.module.settings.impl

import quoi.api.abobaui.constraints.impl.measurements.Animatable
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Text.Companion.string
import quoi.api.abobaui.elements.impl.TextInput.Companion.maxWidth
import quoi.api.abobaui.elements.impl.TextInput.Companion.onTextChanged
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.colour.multiply
import quoi.api.input.CursorShape
import quoi.module.settings.Saving
import quoi.module.settings.UISetting
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor
import quoi.utils.ui.delegateClick
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

class StringSetting(
    name: String,
    override val default: String = "",
    var length: Int = 20,
    desc: String = "",
    val placeholder: String = ""
) : UISetting<String>(name, desc), Saving {

    override var value: String = default
        set(value) {
            field = if (length > 0 && value.length > length) {
                value.take(length)
            } else
                value
        }

    private var censors = false

    fun censors(): StringSetting {
        censors = true
        return this
    }

    override fun write(): JsonElement {
        return JsonPrimitive(value)
    }

    override fun read(element: JsonElement) {
        element.asString?.let {
            value = it
        }
    }

    override fun ElementScope<*>.draw(asSub: Boolean): ElementScope<*> = column(size(w = Copying), gap = 3.px) {
        val col = Colour.Animated(
            from = theme.panel,
            to = colour { theme.panel.rgb.multiply(1.2f) }
        )
        val thickness = Animatable(2.px, 3.px)

        text(
            string = name,
            pos = at(0.px, 0.px),
            size = theme.textSize,
            colour = theme.textSecondary,
        )

        block(
            constrain(w = Copying, h = 25.px),
            colour = col,
            5.radius()
        ) {
            outline(theme.accent, thickness = thickness)

            val lengthText = text(
                string = "${value.length}/$length",
                pos = at(x = 3.percent.alignOpposite),
                colour = getLengthColor(value)
            ).toggle()

            val input = textInput(
                string = value,
                placeholder = placeholder,
                pos = at(x = 3.percent),
                size = theme.textSize,
                colour = theme.textSecondary,
                caretColour = theme.caretColour
            ) {
                val maxWidth = Animatable(from = 90.percent, to = 75.percent)
                maxWidth(maxWidth)

                cursor(CursorShape.IBEAM)

                onValueChanged {
                    string = value
                }

                onTextChanged { event ->
                    var str = event.string
                    if (str.length > length) str = str.take(length)
                    lengthText.string = "${str.length}/$length"
                    lengthText.element.colour = getLengthColor(str)
                    event.string = str
                    value = str
                }
                onFocusChanged {
                    thickness.animate(0.25.seconds, style = Animation.Style.EaseInOutQuint)
                    lengthText.toggle()
                    maxWidth.swap()
                    this@draw.redraw()
                }
            }

            onMouseEnterExit {
                col.animate(0.25.seconds, style = Animation.Style.EaseInOutQuint)
            }

            delegateClick(input)
        }
    }


    private fun getLengthColor(string: String) =
        if (string.length >= length) Colour.RED else theme.textSecondary
}