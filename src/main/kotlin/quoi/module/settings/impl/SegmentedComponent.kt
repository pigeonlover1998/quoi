package quoi.module.settings.impl

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.animations.Animation
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.input.CursorShape
import quoi.module.settings.Saving
import quoi.module.settings.UIComponent
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor

class SegmentedComponent<T>(
    name: String,
    val defaultSelected: T,
    val options: List<T>,
    desc: String = ""
) : UIComponent<SegmentedComponent<T>>(name, desc), Saving {

    init {
        require(options.size in 2..3) { "segmented component must have 2 or 3 options. current: ${options.size}" }
        this.asParent()
    }

    override val default: SegmentedComponent<T> = this

    override var value: SegmentedComponent<T>
        get() = this
        set(v) { index = v.index }

    var index: Int = options.indexOf(defaultSelected).coerceAtLeast(0)
        set(value) {
            val prev = field
            field = value.coerceIn(options.indices)
            if (prev != field) {
                cols[prev].animate()
                cols[field].animate()
            }
        }

    val selected: T
        get() = options[index]

    private fun nameOf(item: T) =
        if (item is Enum<*>) item.name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ") else item.toString()

    override fun write(): JsonElement = JsonPrimitive(nameOf(selected))

    override fun read(element: JsonElement) {
        element.asString?.let { str ->
            val found = options.find { nameOf(it).equals(str, ignoreCase = true) }
            if (found != null) index = options.indexOf(found)
        }
    }

    override fun hashCode(): Int = index

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SegmentedComponent<*>) return false
        return name == other.name && options == other.options
    }

    private val cols = options.indices.map { Cols(it) }

    override fun ElementScope<*>.draw(asSub: Boolean) = group(size(w = Copying)) { // todo improve. maybe make bg colour move instead of fade in/out
        text(
            string = name,
            size = theme.textSize,
            colour = theme.onSurfaceVariant,
            pos = at(x = 0.px, y = Centre)
        )

        val width = 100f / options.size
        group(constrain(x = 0.px.alignOpposite, w = 60.percent, h = if (asSub) Bounding else 20.px,)) {

            options.reversed().forEachIndexed { i, option -> // tbh I don't remember why I made it reversed

                val originalIndex = options.size - 1 - i
                val col = cols[originalIndex]

                val r = if (asSub) 4 else 5
                val radius = when (originalIndex) {
                    0 -> radius(tl = r, bl = r, tr = 0, br = 0)
                    options.lastIndex ->  radius(tr = r, br = r, tl = 0, bl = 0)
                    else -> 0.radius()
                }

                block(
                    constrain(
                        x = (i * width).percent.alignOpposite,
                        w = width.percent,
                        h = Copying
                    ),
                    colour = col.bg,
                    radius = radius
                ) {
                    outline(col.outline, thickness = 2.px)
                    cursor(CursorShape.HAND)

                    text(
                        string = nameOf(option),
                        size = theme.textSize,
                        colour = col.text,
                    )

                    onAdd {
                        scheduleTask {
                            if (originalIndex == index) {
                                element.moveToTop()
                            }
                        }
                    }

                    onMouseEnterExit {
                        if (originalIndex != index) {
                            col.hovered = !col.hovered
                            col.outline.animate(0.25.seconds, style = Animation.Style.EaseOutQuint)
                        }
                    }

                    onClick {
                        index = originalIndex
                        element.moveToTop()
                        true
                    }
                }
            }
        }
    }

    private inner class Cols(i: Int) {
        var hovered = false

        val isSelected = i == options.indexOf(defaultSelected)
        val bg = Colour.Animated(
            from = colour { theme.surfaceContainerHighest.rgb },
            to = colour { theme.primary.rgb },
            swapIf = isSelected
        )
        val outline = Colour.Animated(
            from = colour { theme.outline.rgb },
            to = colour { theme.primary.rgb },
            swapIf = isSelected
        )
        val text = Colour.Animated(
            from = colour { theme.onSurface.rgb },
            to = colour { theme.onPrimary.rgb },
            swapIf = isSelected
        )

        fun animate() {
            val d = 0.25.seconds
            val s = Animation.Style.EaseOutQuint
            bg.animate(d, s)
            text.animate(d, s)

            if (hovered) {
                hovered = false
            } else {
                outline.animate(d, s)
            }
        }
    }
}

inline fun <reified E : Enum<E>> SegmentedComponent(name: String, default: E, desc: String = ""): SegmentedComponent<E> =
    SegmentedComponent(name, default, default.declaringJavaClass.enumConstants.toList(), desc)