package quoi.module.settings.impl

import quoi.api.abobaui.constraints.impl.positions.Centre
import quoi.api.abobaui.constraints.impl.size.AspectRatio
import quoi.api.abobaui.constraints.impl.size.Copying
import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.abobaui.elements.impl.Popup
import quoi.api.colour.*
import quoi.api.input.CursorShape
import quoi.module.settings.Saving
import quoi.module.settings.UISetting
import quoi.utils.ThemeManager.theme
import quoi.utils.ui.cursor
import quoi.utils.ui.elements.colourPicker
import quoi.utils.ui.popupX
import quoi.utils.ui.popupY
import quoi.utils.ui.watch
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive

class ColourSetting(
    name: String,
    colour: Colour,
    private var allowAlpha: Boolean = false,
    desc: String = ""
) : UISetting<Colour.HSB>(name, desc), Saving {

    override val default: Colour.HSB = colour.toHSB()

    override var value: Colour.HSB = default.copy()
        set(value) {
            storedValue.saturation = value.saturation
            storedValue.brightness = value.brightness
            storedValue.alpha = value.alpha

            if (!rainbow) {
                storedValue.hue = value.hue
                field = value
            }
        }

    private var storedValue: Colour.HSB = default
    var rainbow: Boolean = false
        private set

    override fun reset() {
        value = default.copy()
    }

    override fun write() =
        if (!rainbow) JsonPrimitive(storedValue.toHexString(allowAlpha))
        else JsonObject().apply {
            addProperty("colour", storedValue.toHexString(allowAlpha))
            addProperty("rainbow", true)
        }

    override fun read(element: JsonElement) {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            storedValue = Colour.RGB(hexToRGBA(obj["colour"].asString)).toHSB()
            rainbow = obj["rainbow"]?.asBoolean ?: false
            value = if (rainbow) hsb { Colour.RAINBOW.withAlpha(storedValue.alpha).toHSB() } else storedValue
            return
        }

        if (element.asString?.startsWith("#") == true) {
            value = Colour.RGB(hexToRGBA(element.asString)).toHSB()
        } else {
            element.asInt.let { value = Colour.RGB(it).toHSB() }
        }
    }

    private var popup: Popup? = null

    override fun ElementScope<*>.draw(asSub: Boolean): ElementScope<*> = row(size(Copying), gap = 4.px) {
        fun label() = text(
            string = name,
            size = theme.textSize,
            colour = theme.textSecondary,
            pos = at(y = Centre)
        )

        val constraints =
            if (asSub) size(w = AspectRatio(1.5f), h = 15.px)
            else constrain(x = 0.px.alignOpposite, w = AspectRatio(1.5f), h = 20.px)

        fun box() = block(
            constraints = constraints,
            colour { value.rgb },
            5.radius()
        ) {
            outline(theme.accent, thickness = 2.px)
            cursor(CursorShape.HAND)

            onClick {
                popup?.closePopup()
                popup = colourPicker(
                    ::value,
                    allowAlpha = allowAlpha,
                    isRainbow = ::rainbow,
                    pos = at(popupX(gap = 0f), popupY(corner = true))
                )
                true
            }

            watch(::rainbow, immediate = true) { enabled ->
                if (enabled) { // schizo but works ig
                    val provider = hsb { Colour.RAINBOW.withAlpha(storedValue.alpha).toHSB() }
                    rainbow = false
                    value = provider
                    rainbow = true
                } else {
                    rainbow = false
                    value = storedValue
                }
            }
        }

        if (asSub) {
            box()
            label()
        } else {
            label()
            box()
        }
    }
}