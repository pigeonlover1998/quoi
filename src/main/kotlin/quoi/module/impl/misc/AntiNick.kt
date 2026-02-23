package quoi.module.impl.misc

import quoi.api.events.WorldEvent
import quoi.module.Module
import quoi.utils.ChatUtils.modMessage
import quoi.utils.ChatUtils.prefix
import quoi.utils.WorldUtils
import quoi.utils.skyblock.player.PlayerUtils.realName

object AntiNick : Module( // untested
    "AntiNick"
) {
    init {
        on<WorldEvent.Load.End> {
            WorldUtils.players.forEach { player ->
                val gp = player.profile
                val real = gp.realName
                if (real != gp.name) {
                    val denicked = real?.let { "&a[DENICKED] $it" } ?: "&c[CANNOT DENICK]"
                    modMessage("${gp.name} &e->&r $denicked", prefix = prefix("AntiNick"))
                }
            }
        }
    }
}