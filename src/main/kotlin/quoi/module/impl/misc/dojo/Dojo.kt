package quoi.module.impl.misc.dojo

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import quoi.api.events.ChatEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.trackedBy
import quoi.api.events.core.until
import quoi.module.Module
import quoi.module.impl.misc.dojo.impl.*
import quoi.utils.WorldUtils.nearbyBlocks
import quoi.utils.WorldUtils.state
import quoi.utils.addVec

object Dojo : Module(
    "Dojo",
    desc = "Auto dojo",
    subarea = "dojo",
    tag = Tag.BETA
) {

    private val features = setOf(
        Force,
        Stamina,
        Mastery,
        Discipline,
        Swiftness,
        Control,
        Tenacity
    )

    var centre = Vec3(-207.5, 99.0, -598.5)
        private set

    var centrePos = BlockPos(-207, 99, -598)
        private set

    var dojoType by trackedBy<ChatEvent.Packet, DojoType>(DojoType.NONE) { prev ->
        if (message.contains("Your Rank:")) {
            features.forEach { it.onDisable() }
            return@trackedBy DojoType.NONE
        }
        if (!message.contains("OBJECTIVES")) prev
        else {
            val new = DojoType.fromString(message.uppercase())
            if (new == DojoType.NONE) return@trackedBy new
            until<TickEvent.End> {
                val block = player.position().nearbyBlocks(radius = 5f) { it.state.block == new.block }
                    .firstOrNull() ?: return@until false
                centrePos = block
                centre = block.center.addVec(y = 0.5)
                true
            }
            new
        }
    }
}

/**
 * test of force (sumo shit) -207 99 -598
 *  legit: highlight negative mobs
 *  cheat: cancel entity attack event on negative mobs
 *
 * test of stamina (jump in a hole shit) // todo
 *  legit: highlight doorways
 *  cheat: auto jump
 *
 * test of mastery (bow shit)
 *  legit: timers on wool and tracers to ones that are good to shoot
 *  cheat: auto aim and shoot when needed
 *
 * test of discipline (sword shit)
 *  legit: highlight mobs
 *  cheat: automatically swap swords when looking at mob, cancel attack event if wrong sword
 *
 * test of swiftness (parkour shit)
 *  legit: tracers to green
 *  cheat: pathfind and moveTo (need to impl jumping in pathfinding and moveTo)
 *
 * test of control (skeleton shit)
 *  legit: render actual skeleton location
 *  cheat: auto aim, try to stay on platform when you get pegged by mobs
 *
 * test of tenacity (ghasts shit) // todo
 *  legit: render projectile trajectories paths
 *  cheat: pathfind and moveTo to doge
 *
 */