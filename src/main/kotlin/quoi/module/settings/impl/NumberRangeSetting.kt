package quoi.module.settings.impl

import quoi.api.abobaui.constraints.impl.measurements.Undefined
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Text.Companion.string
import quoi.utils.ThemeManager.theme
import quoi.module.settings.Saving
import quoi.module.settings.UISetting
import quoi.utils.round
import quoi.utils.ui.elements.rangeSlider
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import kotlin.math.floor
import kotlin.math.round

@Suppress("UNCHECKED_CAST")
class NumberRangeSetting<E>(
    name: String,
    override val default: Pair<E, E>,
    val min: E = -10000 as E,
    val max: E = 10000 as E,
    val increment: Number = 1,
    desc: String = "",
    val unit: String = "",
) : UISetting<Pair<E, E>>(name, desc), Saving where E : Number, E : Comparable<E> {

    override var value: Pair<E, E> = default

    val incrementD = increment.toDouble()

    private val minD = min.toDouble()
    private val maxD = max.toDouble()

    private val text: String get() = "${format(value.first)} - ${format(value.second)}$unit"

    private fun format(n: E): String {
        val d = n.toDouble()
        val num = if (d - floor(d) == 0.0) d.toInt() else d.round(2)
        return "$num"
    }

    private fun clamp(n: Number) = (round((n.toDouble() / incrementD) + 1e-9) * incrementD).coerceIn(minD, maxD)

    private fun cast(n: Number) = when (default.first) {
        is Int -> n.toInt()
        is Float -> n.toFloat()
        is Long -> n.toLong()
        is Double -> n
        else -> n
    } as E

    fun set(first: Number, second: Number) {
        val v1 = clamp(first.toDouble())
        val v2 = clamp(second.toDouble())

        value = if (v1 > v2) {
            cast(v2) to cast(v1)
        } else {
            cast(v1) to cast(v2)
        }
    }

    override fun hide(): NumberRangeSetting<E> {
        super.hide()
        return this
    }

    override fun write(): JsonElement {
        return JsonArray().apply {
            add(value.first)
            add(value.second)
        }
    }

    override fun read(element: JsonElement) {
        val arr = element.asJsonArray
        value = cast(arr[0].asNumber) to cast(arr[1].asNumber)
    }

    override fun ElementScope<*>.draw(asSub: Boolean): ElementScope<*> = column(size(Copying), gap = 2.px) {
        row(size(w = Copying), gap = 2.px) { // todo numberInput
            text(
                string = name + if (asSub) ":" else "",
                size = theme.textSize,
                colour = theme.textSecondary
            )

            text(
                string = text,
                size = theme.textSize,
                colour = theme.textSecondary,
                pos = at(if (asSub) Undefined else 0.px.alignOpposite, y = 0.px)
            ) {
                onValueChanged {
                    string = text
                    redraw()
                }
            }
        }

        rangeSlider(
            ::value,
            min = min,
            max = max,
            increment = increment,
            size = size(Copying, 8.px - if (asSub) 2.px else 0.px)
        )
    }
}