package quoi.module.settings

import quoi.api.colour.Colour
import quoi.api.input.CatKeys
import quoi.module.settings.impl.ButtonComponent
import quoi.module.settings.impl.ColourPickerComponent
import quoi.module.settings.impl.KeybindComponent
import quoi.module.settings.impl.RangeSliderComponent
import quoi.module.settings.impl.SelectorComponent
import quoi.module.settings.impl.SliderComponent
import quoi.module.settings.impl.SwitchComponent
import quoi.module.settings.impl.TextComponent
import quoi.module.settings.impl.TextInputComponent

interface SettingsDsl {
    fun switch(name: String, enabled: Boolean = false, desc: String = "") =
        SwitchComponent(name, enabled, desc)

    fun colourPicker(name: String, colour: Colour, allowAlpha: Boolean = false, desc: String = "") =
        ColourPickerComponent(name, colour, allowAlpha, desc)

    fun keybind(name: String, key: Int = CatKeys.KEY_NONE, desc: String = "") =
        KeybindComponent(name, key, desc)

    fun <T> selector(name: String, default: T, options: List<T>, desc: String = "") =
        SelectorComponent(name, default, options, desc)

    fun <E : Enum<E>> selector(name: String, default: E, desc: String = "") =
        SelectorComponent(name, default, default.declaringJavaClass.enumConstants.toList(), desc)

    fun text(value: String, desc: String = "") =
        TextComponent(value, desc)

    fun textInput(name: String, default: String = "", length: Int = 20, desc: String = "", placeholder: String = "") =
        TextInputComponent(name ,default, length, desc, placeholder)

    fun button(name: String, desc: String = "", block: () -> Unit = {}) =
        ButtonComponent(name, desc, block)

    fun <E> rangeSlider(
        name: String,
        value: Pair<E, E>,
        min: E,
        max: E,
        increment: Number = 1,
        desc: String = "",
        unit: String = "",
    ): RangeSliderComponent<E> where E : Number, E : Comparable<E> =
        RangeSliderComponent(name, value, min, max, increment, desc, unit)

    fun <E> slider(
        name: String,
        value: E,
        min: E,
        max: E,
        increment: Number = 1,
        desc: String = "",
        unit: String = "",
    ): SliderComponent<E> where E : Number, E : Comparable<E> =
        SliderComponent(name, value, min, max, increment, desc, unit)
}