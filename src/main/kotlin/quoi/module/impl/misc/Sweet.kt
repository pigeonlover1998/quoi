package quoi.module.impl.misc

import quoi.api.events.ChatEvent
import quoi.module.Module
import quoi.utils.SoundUtils
import quoi.utils.Sounds
import quoi.utils.StringUtils.noControlCodes

object Sweet : Module("Sweet") {
    private val soundVolume by slider("Volume", 1.0f, 0.1f, 10.0f, 0.01f, "Volume of the sound.")
    private val soundPitch by slider("Pitch", 1.0f, 0.1f, 2.0f, 0.01f, "Pitch of the sound.")

    init {
        on<ChatEvent.Packet> {
            if (message.noControlCodes.contains("sweet", ignoreCase = true)) {
                SoundUtils.play(Sounds.MISC.SWEET, volume = soundVolume, pitch = soundPitch)
            }
        }
    }
}