package quoi.module.impl.general

import quoi.api.events.WorldEvent
import quoi.api.events.core.on
import quoi.module.Module
import quoi.utils.ChatUtils
import quoi.utils.WorldUtils
import quoi.utils.skyblock.player.PlayerUtils.realName

object AntiNick : Module(
    "AntiNick",
    desc = "Detects nicked players."
) {
    init {
        on<WorldEvent.Load.End> {
            WorldUtils.players.forEach { player ->
                val gp = player.profile
                val real = gp.realName
                if (real != gp.name) {
                    val denicked = real?.let { "&a[DENICKED] $it" } ?: "&c[CANNOT DENICK]"
                    ChatUtils.modMessage("${gp.name} &e->&r $denicked", prefix = ChatUtils.prefix("AntiNick"))
                }
            }
        }
    }
}