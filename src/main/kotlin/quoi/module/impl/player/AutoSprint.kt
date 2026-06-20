package quoi.module.impl.player

import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.module.Module

object AutoSprint : Module(
    "Auto Sprint",
    desc = "Automatically sprints."
) {
    init {
        on<TickEvent.End> {
            if (player.isInWater || player.isUnderWater) return@on
            mc.options.keySprint.isDown = true
        }
    }
}