package quoi.module.settings.impl

import quoi.api.abobaui.constraints.impl.measurements.Undefined
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Text.Companion.string
import quoi.module.settings.Saving
import quoi.module.settings.UISetting
import quoi.utils.ThemeManager.theme
import quoi.utils.round
import quoi.utils.ui.elements.numberInput
import quoi.utils.ui.elements.slider
import quoi.utils.ui.watch
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import kotlin.math.floor
import kotlin.math.round

@Suppress("UNCHECKED_CAST")
class NumberSetting<E>(
    name: String,
    override val default: E = 1.0 as E,
    val min: E = -10000 as E,
    val max: E = 10000 as E,
    val increment: Number = 1,
    desc: String = "",
    val unit: String = "",
) : UISetting<E>(name, desc), Saving where E : Number, E : Comparable<E> {

    override var value: E = default

    val incrementD = increment.toDouble()
    private val minD = min.toDouble()
    private val maxD = max.toDouble()
    private val valueDouble get() = value.toDouble()

    private val text: String
        get() {
            val number = if (valueDouble - floor(valueDouble) == 0.0) value.toInt() else valueDouble.round(2)
            return "$number$unit"
        }

    fun set(new: Number) {
        val n = (round((new.toDouble() / incrementD) + 1e-9) * incrementD).coerceIn(minD, maxD)

        value = when (default) {
            is Int -> n.toInt() as E
            is Float -> n.toFloat() as E
            is Long -> n.toLong() as E
            is Double -> n as E
            else -> throw Exception("no good number setting")
        }
    }

    override fun hide(): NumberSetting<E> {
        super.hide()
        return this
    }

    override fun write(): JsonElement {
        return JsonPrimitive(value)
    }

    override fun read(element: JsonElement) {
        element.asNumber?.let {
            value = when (default) {
                is Int -> it.toInt()
                is Float -> it.toFloat()
                is Long -> it.toLong()
                is Double -> it.toDouble()
                else -> it
            } as E
        }
    }

    override fun ElementScope<*>.draw(asSub: Boolean): ElementScope<*> = column(size(Copying), gap = 2.px) {

        row(size(w = Copying), gap = 2.px) {
            text(
                string = name + if (asSub) ":" else "",
                size = theme.textSize,
                colour = theme.textSecondary
            )

            val input = numberInput(
                ::value,
                min = min,
                max = max,
                unit = unit,
                size = theme.textSize,
                colour = theme.textSecondary,
                caretColour = theme.caretColour,
                pos = at(if (asSub) Undefined else 0.px.alignOpposite, y = 0.px)
            )

            watch(::value) {
                set(it)
                input.string = text
            }
        }

        slider(
            ::value,
            min = min,
            max = max,
            increment = increment,
            size = size(Copying, 8.px - if (asSub) 2.px else 0.px),
        )
    }
}