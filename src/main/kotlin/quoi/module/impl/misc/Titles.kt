package quoi.module.impl.misc

import quoi.api.events.ChatEvent
import quoi.api.skyblock.SkyblockPlayer.AUTOPET_REGEX
import quoi.api.skyblock.dungeon.Dungeon
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.ui.createSoundSettings

// THIS IS A TEMP MODULE KYLEAN.
// will be possible to customise in custom triggers
object Titles : Module("Titles", desc = "temp module") {

    private val autoPet by switch("Petrules", desc = "Show title upon petrule chat message")
    private val invincibilityProc by switch("Invincibility", desc = "Show title upon bonzo/spirit/phoenix proc")

    private val titleSettings by text("Settings")
    private val dungeonsOnly by switch("Dungeons only").childOf(::titleSettings)
    private val bossOnly by switch("Boss only").childOf(::titleSettings)
    private val asSubtitle by switch("Use subtitles", true, desc = "Shows the text as a subtitle instead of the main title.").childOf(::titleSettings)
    private val titleDuration by slider("Title duration", 2.0, 0.5, 5.0, 0.1, desc = "How long the title stays on screen.", "s").childOf(::titleSettings)

    private val playSound by switch("Play sound", desc = "Plays a sound when title pops up").childOf(::titleSettings)
    private val soundSettings = createSoundSettings("Title", ::titleSettings) { playSound }

    init {
        on<ChatEvent.Packet> {
            if (dungeonsOnly && !Dungeon.inDungeons) return@on
            if (bossOnly && !Dungeon.inBoss) return@on
            val cleanMessage = message.noControlCodes

            if (autoPet) {
                AUTOPET_REGEX.find(message)?.groupValues?.get(1)?.let { stupid(it.trim()) }
            }

            if (invincibilityProc) {
                when (cleanMessage) {
                    "Second Wind Activated! Your Spirit Mask saved your life!" ->
                        stupid("§fSpirit")
                    "Your ⚚ Bonzo's Mask saved your life!", "Your Bonzo's Mask saved your life!" ->
                        stupid("§cBonzo")
                    "Your Phoenix Pet saved you from certain death!" ->
                        stupid("§6Phoenix")
                }
            }
        }
    }

    private fun stupid(text: String) {
        val (sound, volume, pitch) = soundSettings()

        PlayerUtils.setTitle(
            title = if (!asSubtitle) text else "",
            subtitle = if (asSubtitle) text else "",
            playSound = playSound,
            sound = sound,
            volume = volume,
            pitch = pitch,
            fadeIn = 0,
            stayAlive = (titleDuration * 20).toInt(),
            fadeOut = 0
        )
    }
}