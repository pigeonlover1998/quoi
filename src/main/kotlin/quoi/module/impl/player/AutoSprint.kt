package quoi.module.impl.player

import quoi.api.events.TickEvent
import quoi.module.Module

object AutoSprint : Module(
    "Auto Sprint",
    desc = "Automatically sprints."
) {
    init {
        on<TickEvent.Start> {
            if (player.isInWater || player.isUnderWater) return@on
            mc.options.keySprint.isDown = true
        }
    }
}