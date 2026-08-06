package quoi.module.settings

import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.core.AreaBoundListener
import quoi.api.input.CatKeys
import quoi.module.settings.impl.*
import quoi.utils.ui.HighlightSettings
import quoi.utils.ui.SoundSettings
import quoi.utils.ui.TracerSettings

abstract class SettingsDSL {

    abstract fun <K : Setting<T>, T> register(setting: K): K

    protected operator fun <K : Setting<T>, T> K.unaryPlus(): K = register(this)

    protected fun switch(name: String, enabled: Boolean = false, desc: String = "") =
        SwitchComponent(name, enabled, desc)

    protected fun colourPicker(name: String, colour: Colour, allowAlpha: Boolean = false, desc: String = "") =
        ColourPickerComponent(name, colour, allowAlpha, desc)

    protected fun keybind(name: String, key: Int = CatKeys.KEY_NONE, desc: String = "") =
        KeybindComponent(name, key, desc)

    protected fun <T> selector(name: String, default: T, options: List<T>, desc: String = "") =
        SelectorComponent(name, default, options, desc)

    protected fun <E : Enum<E>> selector(name: String, default: E, desc: String = "") =
        SelectorComponent(name, default, default.declaringJavaClass.enumConstants.toList(), desc)

    protected fun <T> segmented(name: String, default: T, options: List<T>, desc: String = "") =
        SegmentedComponent(name, default, options, desc)

    protected fun <E : Enum<E>> segmented(name: String, default: E, desc: String = "") =
        SegmentedComponent(name, default, default.declaringJavaClass.enumConstants.toList(), desc)

    protected fun text(value: String, desc: String = "") =
        TextComponent(value, desc)

    protected fun textInput(name: String, default: String = "", length: Int = 20, desc: String = "", placeholder: String = "") =
        TextInputComponent(name ,default, length, desc, placeholder)

    protected fun button(name: String, desc: String = "", block: () -> Unit = {}) =
        ButtonComponent(name, desc, block)

    protected fun <E> rangeSlider(
        name: String,
        value: Pair<E, E>,
        min: E,
        max: E,
        increment: Number = 1,
        desc: String = "",
        unit: String = "",
    ): RangeSliderComponent<E> where E : Number, E : Comparable<E> =
        RangeSliderComponent(name, value, min, max, increment, desc, unit)

    protected fun <E> slider(
        name: String,
        value: E,
        min: E,
        max: E,
        increment: Number = 1,
        desc: String = "",
        unit: String = "",
    ): SliderComponent<E> where E : Number, E : Comparable<E> =
        SliderComponent(name, value, min, max, increment, desc, unit)

    protected fun highlight(
        name: String = "Style",
        desc: String = "",
        colour: Colour? = Colour.WHITE,
        fillColour: Colour? = Colour.WHITE.withAlpha(67),
        glow: Boolean = true,
        customColour: Boolean = false,
        customFillColour: Boolean = false,
        aabbOffset: Boolean = false
    ) = HighlightSettings(this as AreaBoundListener, name, desc, colour, fillColour, glow, customColour, customFillColour, aabbOffset)

    protected fun tracer(
        name: String = "Tracer",
        colour: Colour? = Colour.WHITE,
        customColour: Boolean = false,
        distance: Int? = null
    ) = TracerSettings(this as AreaBoundListener, name, colour, customColour, distance)

    protected fun sound(name: String) = SoundSettings(this as AreaBoundListener, name)
}