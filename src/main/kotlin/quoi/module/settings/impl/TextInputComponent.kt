package quoi.module.settings.impl

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.TextInput.Companion.censors
import quoi.module.settings.Saving
import quoi.module.settings.UIComponent
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.elements.lengthInput
import quoi.utils.ui.elements.suggestionInput
import quoi.utils.ui.elements.themedInput

class TextInputComponent(
    name: String,
    override val default: String = "",
    var length: Int = 20,
    desc: String = "",
    val placeholder: String = ""
) : UIComponent<String>(name, desc), Saving {

    override var value: String = default
        set(value) {
            field = if (length > 0 && value.length > length) {
                value.take(length)
            } else
                value
        }

    private var censors = false

    private var suggestions: () -> List<String> = { emptyList() }

    fun censors(): TextInputComponent {
        censors = true
        return this
    }

    fun suggests(supplier: () -> Any): UIComponent<String> {
        suggestions = {
            when (val s = supplier()) {
                is Iterable<*> -> s.map { it.toString() }
                is Array<*> -> s.map { it.toString() }
                else -> listOf(s.toString())
            }
        }

        return this
    }

    fun suggests(values: List<*>) = suggests { values }
    fun suggests(vararg values: Any) = suggests { values.toList() }

    override fun write(): JsonElement {
        return JsonPrimitive(value)
    }

    override fun read(element: JsonElement) {
        element.asString?.let {
            value = it
        }
    }

    override fun ElementScope<*>.draw(asSub: Boolean): ElementScope<*> = column(size(w = Copying), gap = 3.px) {
        if (!asSub) text( // idk
            string = name,
            size = theme.textSize,
            colour = theme.onSurfaceVariant,
        )

        val placeholder = if (asSub && placeholder.isEmpty()) name else placeholder

        suggestionInput(suggestions = suggestions) {
            themedInput(size = size(w = Copying, h = 25.px)) {
                lengthInput(
                    ::value,
                    length = length,
                    placeholder = placeholder,
                )
            }
        }.censors = censors
    }
}