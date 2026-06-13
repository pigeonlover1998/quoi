package quoi.module.settings

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import quoi.api.colour.Colour
import quoi.api.input.CatKeys
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.module.settings.impl.*
import quoi.utils.SoundUtils
import kotlin.reflect.KProperty0

abstract class SettingsDSL {

    abstract fun <K : Setting<*>> register(setting: K): K

    protected operator fun <K : Setting<*>> K.unaryPlus(): K = register(this)

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

    protected fun sound(name: String, ): SoundSetting {
        val sound = +selector("$name sound", SoundUtils.SoundSetting.BlazeHurt)

        val customSound = +textInput("Custom sound", "entity.blaze.hurt", length = 64)
            .json("$name custom sound")
            .childOf(sound) { sound.selected == SoundUtils.SoundSetting.Custom }

        val soundVolume = +slider("Volume", 1.0f, 0.1f, 2.0f, 0.01f, desc = "Volume of the sound to play.")
            .json("$name volume")
            .childOf(sound)

        val soundPitch = +slider("Pitch", 1.0f, 0.1f, 2.0f, 0.01f, desc = "Pitch of the sound to play.")
            .json("$name pitch")
            .childOf(sound)

        val settings = {
            val soundEvent =
                if (sound.selected == SoundUtils.SoundSetting.Custom)
                    BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse(customSound.value)).orElse(null)
                        ?: SoundEvent.createVariableRangeEvent(Identifier.parse(customSound.value))
                else
                    sound.selected.sound
            Triple(soundEvent ?: SoundEvents.BLAZE_HURT, soundVolume.value, soundPitch.value)
        }

        +button("Test sound") { SoundUtils.play(settings) }
            .childOf(sound)

        return SoundSetting(sound, settings)
    }
}

class SoundSetting(
    val main: UIComponent<*>,
    private val settings: () -> Triple<SoundEvent, Float, Float>
) : () -> Triple<SoundEvent, Float, Float> {
    override fun invoke(): Triple<SoundEvent, Float, Float> = settings()

    fun visibleIf(condition: () -> Boolean) = apply { main.visibleIf(condition) }

    fun childOf(parent: UIComponent<*>?, condition: () -> Boolean = { true }) = apply { main.childOf(parent, condition) }

    @JvmName("childOfAny")
    fun childOf(parent: KProperty0<*>?) = apply { main.childOf(parent) }
    @JvmName("childOfBoolean")
    fun childOf(parent: KProperty0<Boolean>) = apply { main.childOf(parent) }
    fun <P> childOf(parent: KProperty0<P>?, condition: (P) -> Boolean) = apply { main.childOf(parent, condition) }
}