package quoi.module.settings.impl

import quoi.api.abobaui.constraints.impl.size.Bounding
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Popup
import quoi.api.abobaui.elements.impl.Text.Companion.string
import quoi.api.input.CursorShape
import quoi.module.settings.Saving
import quoi.module.settings.UISetting
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor
import quoi.utils.ui.popupX
import quoi.utils.ui.popupY
import quoi.utils.ui.elements.selector
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

class SelectorSetting<T>(
    name: String,
    val defaultSelected: T,
    var options: List<T>,
    desc: String = ""
) : UISetting<SelectorSetting<T>>(name, desc), Saving {

    override val default: SelectorSetting<T> = this

    override var value: SelectorSetting<T>
        get() = this
        set(value) { index = value.index }

    var index: Int = optionIndex(defaultSelected)
        set(value) {
            field = if (options.isEmpty()) 0
            else if (value > options.size - 1) 0
            else if (value < 0) options.size - 1
            else value
        }

    var selected: T
        get() = if (options.isNotEmpty()) options[index] else defaultSelected
        set(value) {
            index = optionIndex(value)
        }

    private val selectedName get() = nameOf(selected)

    private fun nameOf(item: T) =
        if (item is Enum<*>) item.name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ") else item.toString()

    private fun optionIndex(value: T): Int = options.indexOf(value).coerceAtLeast(0)

    override fun write(): JsonElement = JsonPrimitive(selectedName)

    override fun read(element: JsonElement) {
        element.asString?.let { str ->
            val found = options.find { nameOf(it).equals(str, ignoreCase = true) }
            if (found != null) selected = found
        }
    }

    override fun hashCode(): Int = index

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelectorSetting<*>) return false
        return name == other.name && options == other.options
    }

    var popup: Popup? = null
    override fun ElementScope<*>.draw(asSub: Boolean): ElementScope<*> = column(size(w = Copying)) {
        row(size(w = Copying)) {
            text(
                string = name,
                size = theme.textSize,
                colour = theme.textSecondary
            )
            block(
                constrain(x = 0.px.alignOpposite, w = Bounding + 5.px, h = Bounding),
                colour = theme.background,
                5.radius()
            ) {
                outline(theme.accent, thickness = 2.px)
                cursor(CursorShape.HAND)

                text(
                    string = selectedName,
                    size = theme.textSize,
                    colour = theme.textSecondary
                ) {
                    onValueChanged {
                        string = selectedName
                    }
                }

                onClick {
                    popup?.closePopup()
                    val (x, y) = popupX(gap = -130f) to popupY(gap = 5f, corner = true)
                    popup = selector(
                        entries = options,
                        selected = index,
                        displayString = { nameOf(it) },
                        colour = theme.panel,
                        pos = at(x, y)
                    ) { new ->
                        selected = new
                    }
                    true
                }

                onClick(button = 1) {
                    index++
                    true
                }
            }
        }
    }
}

inline fun <reified E : Enum<E>> SelectorSetting(name: String, default: E, desc: String = ""): SelectorSetting<E> =
    SelectorSetting(name, default, default.declaringJavaClass.enumConstants.toList(), desc)