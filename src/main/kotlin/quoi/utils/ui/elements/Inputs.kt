package quoi.utils.ui.elements

import quoi.api.abobaui.constraints.Constraint
import quoi.api.abobaui.constraints.Positions
import quoi.api.abobaui.constraints.Sizes
import quoi.api.abobaui.constraints.impl.measurements.Animatable
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.constraints.impl.size.Fill
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Popup
import quoi.api.abobaui.elements.impl.Scrollable.Companion.scroll
import quoi.api.abobaui.elements.impl.Text.Companion.string
import quoi.api.abobaui.elements.impl.TextInput
import quoi.api.abobaui.elements.impl.TextInput.Companion.maxWidth
import quoi.api.abobaui.elements.impl.TextInput.Companion.onTextChanged
import quoi.api.abobaui.elements.impl.popup
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.input.CursorShape
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor
import quoi.utils.ui.data.Radii
import quoi.utils.ui.delegateClick
import quoi.utils.ui.hud.GroupHeight
import quoi.utils.ui.popupY
import quoi.utils.ui.rendering.Font
import quoi.utils.ui.rendering.NVGRenderer
import quoi.utils.ui.watch
import kotlin.math.roundToInt
import kotlin.reflect.KMutableProperty0

@Suppress("UNCHECKED_CAST")
fun <T : Number> ElementScope<*>.numberInput(
    ref: KMutableProperty0<T>,
    min: T? = null,
    max: T? = null,
    placeholder: String = "",
    unit: String = "",
    font: Font = NVGRenderer.defaultFont,
    colour: Colour = theme.onSurface,
    holderColour: Colour = theme.onSurfaceVariant,
    caretColour: Colour = theme.primary,
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

            val converted: T = @Suppress("UNCHECKED_CAST") when (ref.get()) {
                is Int -> num.toInt()
                is Float -> num.toFloat()
                is Long -> num.toLong()
                is Double -> num
                is Short -> num.toInt().toShort()
                is Byte -> num.toInt().toByte()
                else -> num
            } as T

            ref.set(converted)
            string = format(converted)
        }
    }
}

fun ElementScope<*>.hexInput(
    value: () -> String,
    allowAlpha: Boolean,
    placeholder: String = if (allowAlpha) "#FFFFFFFF" else "#FFFFFF",
    font: Font = NVGRenderer.defaultFont,
    colour: Colour = theme.onSurface,
    placeHolderColour: Colour = theme.onSurfaceVariant,
    caretColour: Colour = theme.primary,
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
    radius: Radii = 5.radius(),
    content: ElementScope<*>.() -> ElementScope<TextInput>
): ElementScope<TextInput> {
    val thickness = Animatable(2.px, 3.px)

    val outlineCol = Colour.Animated(
        from = theme.outline,
        to = theme.primary
    )

    lateinit var input: ElementScope<TextInput>

    block(
        constrain(x = pos.x, y = pos.y, w = size.width, h = size.height),
        colour = theme.surfaceContainerHighest,
        radius = radius
    ) {
        outline(outlineCol, thickness = thickness)
//        hoverEffect(factor = 1.15f)
        tonalHover()

        input = content().apply {
            cursor(CursorShape.IBEAM)

            onFocusChanged {
                thickness.animate(0.25.seconds, style = Animation.Style.EaseInOutQuint)
                outlineCol.animate(0.25.seconds, style = Animation.Style.EaseInOutQuint)
            }
        }

        delegateClick(input)
    }

    return input
}

fun ElementScope<*>.lengthInput(
    ref: KMutableProperty0<String>,
    pos: Positions = at(),
    size: Sizes = size(w = Fill, h = 25.px),
    length: Int,
    placeholder: String = "",
): ElementScope<TextInput> {
    var value by ref

    fun lenCol(string: String) = if (string.length >= length) theme.error else theme.onSurfaceVariant

    lateinit var input: ElementScope<TextInput>

    group(constrain(x = pos.x, y = pos.y, w = size.width, h = size.height)) {

        val lengthText = text(
            string = "${value.length}/$length",
            pos = at(x = 3.percent.alignOpposite),
            colour = lenCol(value)
        ).toggle()

        input = textInput(
            string = value,
            placeholder = placeholder,
            pos = at(x = 3.percent),
            size = theme.textSize,
            colour = theme.onSurface,
            caretColour = theme.primary,
            placeHolderColour = theme.onSurfaceVariant
        ) {
            val maxWidth = Animatable(from = 94.percent, to = 75.percent)
            maxWidth(maxWidth)

            cursor(CursorShape.IBEAM)

            onTextChanged { event ->
                var str = event.string
                if (str.length > length) str = str.take(length)

                lengthText.string = "${str.length}/$length"
                lengthText.element.colour = lenCol(str)

                event.string = str
                value = str
            }

            onFocusChanged {
                lengthText.toggle()
                maxWidth.swap()
            }
        }
    }

    return input
}

// use with [themedInput] only for now
fun ElementScope<*>.suggestionInput( // todo make it not look like shit. also add scrollbar maybe
    suggestions: () -> List<String>,
    content: ElementScope<*>.() -> ElementScope<TextInput>
): ElementScope<TextInput> {
    var popup: Popup? = null
    lateinit var mainBlock: ElementScope<Block>

    val items = mutableMapOf<String, ElementScope<Block>>()

    fun update(string: String) {
        val matching = items.filter { (text, _) ->
            string.isEmpty() || text.contains(string, true)
        }

        val hideExact = matching.size == 1 && matching.keys.any { it.equals(string, true) }

        var visible = 0
        items.forEach { (text, block) ->
            val match = string.isEmpty() || text.contains(string, true)
            val exact = text.equals(string, true)

            val show = match && !(exact && hideExact)

            if (block.element.enabled != show) block.toggle()
            if (show) visible++
        }

        if ((visible == 0) == mainBlock.element.enabled) mainBlock.toggle()

        mainBlock.element.redraw()
    }

    return content().apply {
        onFocus {
            popup?.closePopup()
            items.clear()

            val y = popupY(gap = 10f, corner = true)
            val thickness = 2.px

            popup = popup(copies(), smooth = false) {
                onClick {
                    closePopup()
                    popup = null
                    true
                }

                mainBlock = block(
                    constrain( // try not to hardcode shit challenge // FIXME
                        x = this@apply.element.x.px - 5.px,
                        y = y,
                        w = this@apply.element.width.px + thickness - 0.5.px + 10.px,
                        h = GroupHeight + thickness - 0.5.px
                    ),
                    colour = theme.surfaceContainerHighest,
                    radius = 5.radius()
                ) {
                    outline(theme.outline, thickness = thickness)
                    dropShadow(
                        colour = Colour.BLACK.withAlpha(0.1f),
                        blur = 10f,
                        spread = 5f,
                        radius = 5.radius()
                    )
                    onClick { true }

                    val scrollable = scrollable(constrain(w = Copying - thickness, h = Bounding.coerceAtMost(112.5.px))) {
                        column {
                            suggestions().forEach { suggestion ->
                                items[suggestion] = block(size(w = Fill, h = 25.px), colour = theme.surfaceContainerHighest, radius = 3.radius()) {
//                                    hoverEffect(1.1f)
                                    tonalHover()
//                                    cursor(CursorShape.HAND)
                                    text(
                                        string = suggestion,
                                        colour = theme.onSurface,
                                        size = 14.px,
                                        pos = at(x = 5.px, y = Centre)
                                    )

                                    onClick {
                                        string = suggestion
                                        ui.unfocus()
                                        closePopup()
                                        true
                                    }
                                }
                            }
                        }
                    }
                    onScroll { (amount) -> scrollable.scroll(amount * -50f) }
                }
            }
            update(string)
        }

        onTextChanged {
            update(it.string)
        }
    }
}