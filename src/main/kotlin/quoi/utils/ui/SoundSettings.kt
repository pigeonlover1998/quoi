package quoi.utils.ui

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import quoi.api.events.core.AreaBoundListener
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.module.settings.group.SettingGroup
import quoi.module.settings.impl.SelectorComponent
import quoi.utils.SoundUtils

// todo docs when I'm not lazy
class SoundSettings(
    parent: AreaBoundListener,
    name: String = "Sound",
) : SettingGroup(parent, SelectorComponent(name, SoundUtils.SoundSetting.BlazeHurt)) {

    @Suppress("unchecked_cast")
    val sound: SoundUtils.SoundSetting
        get() = (component as SelectorComponent<SoundUtils.SoundSetting>).selected

    private val custom by textInput("Custom sound", "entity.blaze.hurt", length = 64)
        .visibleIf { sound == SoundUtils.SoundSetting.Custom }

    private val volume by slider("Volume", 1.0f, 0.1f, 2.0f, 0.01f, desc = "Volume of the sound to play.")

    private val pitch by slider("Pitch", 1.0f, 0.1f, 2.0f, 0.01f, desc = "Pitch of the sound to play.")

    private val test by button("Test sound") { play() }

    private val soundEvent get() =
        if (sound == SoundUtils.SoundSetting.Custom)
            BuiltInRegistries.SOUND_EVENT.getOptional(Identifier.parse(custom)).orElse(null)
                ?: SoundEvent.createVariableRangeEvent(Identifier.parse(custom))
        else
            sound.sound


    private var lastPlayed = 0L


    fun play(interval: Long = 0) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPlayed > interval) return
        lastPlayed = currentTime

        SoundUtils.play(soundEvent, volume, pitch)
    }
}