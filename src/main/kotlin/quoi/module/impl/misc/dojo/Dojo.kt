package quoi.module.impl.misc.dojo

import net.minecraft.world.phys.Vec3
import quoi.api.events.AreaEvent
import quoi.api.events.ChatEvent
import quoi.api.events.RenderEvent
import quoi.api.events.core.on
import quoi.api.events.core.trackedBy
import quoi.module.Module
import quoi.module.impl.misc.dojo.impl.*
import quoi.module.impl.misc.dojo.impl.Discipline
import quoi.utils.render.DrawContextUtils.drawText

object Dojo : Module(
    "Dojo",
    subarea = "dojo",
    tag = Tag.BETA
) {

    private val features = setOf(
        Force,
//        Stamina,
        Mastery,
        Discipline,
        Swiftness,
        Control,
        Tenacity
    )

    init {


        on<RenderEvent.Overlay> { // temp
            ctx.drawText(dojoType.name, 0, 0)
        }

//        on<AreaEvent.Sub> {
//            if (subarea?.contains("dogo") == false)
//                dojoType = DojoType.NONE
//        }
    }

    val centre = Vec3(-207.0, 99.0, -598.0)

    var dojoType by trackedBy<ChatEvent.Packet, DojoType>(DojoType.NONE) { prev ->
        if (message.contains("Your Rank:")) {
            features.forEach { it.onDisable() }
            return@trackedBy DojoType.NONE
        }
        if (!message.contains("OBJECTIVES")) prev
        else DojoType.fromString(message.uppercase())
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