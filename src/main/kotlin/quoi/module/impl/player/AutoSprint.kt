package quoi.module.impl.player

import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.module.Module

object AutoSprint : Module(
    "Auto Sprint",
    desc = "Automatically sprints."
) {
    private var wasUnderWater = false

    init {
        on<TickEvent.End> {
            if (player.isInWater || player.isUnderWater) {
                if (!wasUnderWater) mc.options.keySprint.isDown = false
                wasUnderWater = true
            } else {
                mc.options.keySprint.isDown = true
                wasUnderWater = false
            }
        }
    }
}