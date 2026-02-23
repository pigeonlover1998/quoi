package quoi.utils.ui.elements

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Positions
import quoi.api.abobaui.constraints.Sizes
import quoi.api.abobaui.constraints.impl.measurements.Animatable
import quoi.api.abobaui.constraints.impl.size.Fill
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Text.Companion.string
import quoi.api.abobaui.elements.impl.TextInput
import quoi.api.abobaui.elements.impl.TextInput.Companion.maxWidth
import quoi.api.abobaui.elements.impl.TextInput.Companion.onTextChanged
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.colour.multiply
import quoi.api.input.CursorShape
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor
import quoi.utils.ui.delegateClick
import quoi.utils.ui.rendering.Font
import quoi.utils.ui.rendering.NVGRenderer
import quoi.utils.ui.watch
import kotlin.getValue
import kotlin.math.roundToInt
import kotlin.reflect.KMutableProperty0
import kotlin.text.isDigit

@Suppress("UNCHECKED_CAST")
fun <T : Number> ElementScope<*>.numberInput(
    ref: KMutableProperty0<T>,
    min: T? = null,
    max: T? = null,
    placeholder: String = "",
    unit: String = "",
    font: Font = NVGRenderer.defaultFont,
    colour: Colour = theme.textPrimary,
    holderColour: Colour = theme.textSecondary,
    caretColour: Colour = theme.caretColour,
    pos: Positions = at(),
    maxWidth: Constraint.Size? = null,
    size: Constraint.Size = 50.percent,
): ElementScope<TextInput> {
    val value by ref

    fun format(n: T) = when (unit) {
        "%" if (n.toDouble() <= 1.0f) -> "${(n.toDouble() * 100).roundToInt()}%"
        "°" -> "${(n.toDouble() * 360).roundToInt()}°"
        else -> "$n$unit"
    }

    return textInput(
        string = format(value),
        placeholder = placeholder,
        font = font,
        colour = colour,
        placeHolderColour = holderColour,
        caretColour = caretColour,
        pos = pos,
        size = size
    ) {
        cursor(CursorShape.IBEAM)
        if (maxWidth != null) maxWidth(maxWidth)

        onTextChanged { event ->
            element.parent?.positionChildren()
            if (!event.string.all { it in "0123456789.-" || unit.contains(it, ignoreCase = true) }) event.cancel()
        }

        operation { // todo fix not updating on text changed for some reason...
            element.parent?.positionChildren()
            false
        }

        watch(ref) {
            if (!focused()) string = format(value)
            element.parent?.positionChildren()
        }

        onFocusLost {
            var num = string
                .filter { it.isDigit() || it == '.' || it == '-' }
                .toDoubleOrNull()
                ?: run { string = "$value$unit";return@onFocusLost }

            num = when (unit) {
                "%" -> (num.coerceIn(0.0, 100.0) / 100.0)
                "°" -> (num.coerceIn(0.0, 360.0) / 360.0)
                else -> num.coerceIn(min?.toDouble() ?: Double.MIN_VALUE, max?.toDouble() ?: Double.MAX_VALUE)
            }

            ref.set(
                when (value) {
                    is Int -> num.toInt()
                    is Float -> num.toFloat()
                    is Long -> num.toLong()
                    is Double -> num
                    is Short -> num.toInt().toShort()
                    is Byte -> num.toInt().toByte()
                    else -> num
                } as T
            )

            string = format(num as T)
        }
    }
}

fun ElementScope<*>.hexInput(
    value: () -> String,
    allowAlpha: Boolean,
    placeholder: String = if (allowAlpha) "#FFFFFFFF" else "#FFFFFF",
    font: Font = NVGRenderer.defaultFont,
    colour: Colour = theme.textPrimary,
    placeHolderColour: Colour = theme.textSecondary,
    caretColour: Colour = theme.caretColour,
    pos: Positions = at(),
    maxWidth: Constraint.Size? = null,
    size: Constraint.Size = 50.percent,
    onFocusLost: (String) -> Unit
) = textInput(
    string = value(),
    placeholder = placeholder,
    font = font,
    colour = colour,
    placeHolderColour = placeHolderColour,
    caretColour = caretColour,
    pos = pos,
    size = size
) {
    cursor(CursorShape.IBEAM)
    val hexLength = if (allowAlpha) 8 else 6

    if (maxWidth != null) maxWidth(maxWidth)

    onTextChanged { event ->
        element.parent?.positionChildren()
        val str = event.string.trimStart('#')

        val isValid = str.length <= hexLength &&
                str.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

        if (!isValid) {
            event.cancel()
        }
    }

    operation { // todo fix not updating on text changed for some reason...
        element.parent?.positionChildren()
        false
    }

    watch(value) {
        if (!focused()) string = value()
        element.parent?.positionChildren()
    }

    onFocusLost {
        val hex = string.trimStart('#')

        string = if (hex.length == hexLength && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            "#$hex".uppercase()
        } else {
            value()
        }
        onFocusLost(string)
    }
}

fun ElementScope<*>.themedInput(
    pos: Positions = at(),
    size: Sizes = size(w = Fill, h = 25.px),
    content: ElementScope<*>.() -> ElementScope<TextInput>
): ElementScope<TextInput> {
    val col = Colour.Animated(
        from = theme.panel,
        to = colour { theme.panel.rgb.multiply(1.2f) }
    )
    val thickness = Animatable(2.px, 3.px)

    lateinit var input: ElementScope<TextInput>

    block(
        constrain(x = pos.x, y = pos.y, w = size.width, h = size.height),
        colour = col,
        5.radius()
    ) {
        outline(theme.accent, thickness = thickness)

        input = content().apply {
            cursor(CursorShape.IBEAM)

            onFocusChanged {
                thickness.animate(0.25.seconds, style = Animation.Style.EaseInOutQuint)
            }
        }

        onMouseEnterExit {
            col.animate(0.25.seconds, style = Animation.Style.EaseInOutQuint)
        }

        delegateClick(input)
    }

    return input
}