package quoi.utils

import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import quoi.QuoiMod.MOD_ID
import quoi.QuoiMod.mc
import quoi.utils.SoundUtils.register

object SoundUtils {

    internal fun register(name: String): SoundEvent {
        val id = Identifier.fromNamespaceAndPath(MOD_ID, name)
        return Registry.register(
            BuiltInRegistries.SOUND_EVENT,
            id,
            SoundEvent.createVariableRangeEvent(id)
        )
    }

    fun play(sound: SoundEvent, volume: Float = 1.0f, pitch: Float = 1.0f) = mc.execute {
        mc.player?.playSound(sound, volume, pitch)
    }

    fun play(soundSettings: () -> Triple<SoundEvent, Float, Float>) {
        val (sound, volume, pitch) = soundSettings()
        play(sound, volume, pitch)
    }

    enum class SoundSetting(val sound: SoundEvent) {
        BlazeHurt(SoundEvents.BLAZE_HURT),
        Pling(SoundEvents.NOTE_BLOCK_PLING.value()),
        OrbPickup(SoundEvents.EXPERIENCE_ORB_PICKUP),
        LevelUp(SoundEvents.PLAYER_LEVELUP),
        AnvilLand(SoundEvents.ANVIL_LAND),
//        WitherSpawn(SoundEvents.WITHER_SPAWN),
//        Explosion(SoundEvents.GENERIC_EXPLODE.value()),
        Sweet(Sounds.MISC.SWEET),
        Custom(SoundEvents.BLAZE_HURT)
    }
}

object Sounds {
    object MISC {
        val SWEET = register("sweet")
    }
}