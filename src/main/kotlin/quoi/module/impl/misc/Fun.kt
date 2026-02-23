package quoi.module.impl.misc

import quoi.api.events.ChatEvent
import quoi.api.skyblock.Island
import quoi.module.Module
import quoi.module.settings.impl.BooleanSetting
import quoi.utils.ChatUtils
import quoi.utils.StringUtils.noControlCodes

// Kyleen
object Fun : Module("Fun", area = Island.Dungeon) {

    private val downtime by BooleanSetting("Downtime message")
    private val gettingCarried by BooleanSetting("Milestone announce", desc = "RIP HealerFunny")

    init {
        on<ChatEvent.Packet> {
            val message = message.noControlCodes
            if (downtime && message.trim() == "> EXTRA STATS <") {
                ChatUtils.command("pc soshimee needs downtime") //if ykyk
            }
            if (gettingCarried && message.contains("Milestone ❸")) {
                ChatUtils.command("pc Milestone 3")
            }
        }
    }
}