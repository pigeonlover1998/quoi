package quoi.module.impl.misc.dojo.impl

import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.events.EntityEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.dojo.Dojo
import quoi.module.impl.misc.dojo.DojoType
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.getDirection
import quoi.utils.rayCast
import quoi.utils.rayCastVec
import quoi.utils.render.drawLine

object Tenacity : ToggleableGroup(Dojo, "Tenacity", subarea = "dojo arena") { // untested

    private val fireballs = mutableListOf<Fireball>()

    init {
        on<EntityEvent.Spawn> {
            if (entity !is ArmorStand) return@on
            if (Dojo.centre.distanceToSqr(entity.position()) > 625) return@on

            if (fireballs.none { it.entity.id == entity.id }) {
                fireballs.add(Fireball(entity, entity.position()))
            }
        }

        on<TickEvent.End> {
            fireballs.removeIf {
                if (it.entity.isRemoved) return@removeIf true
                if (it.to == null && it.entity.position() != it.from) {
                    it.to = it.entity.position()
                }
                false
            }
        }

        on<RenderEvent.World> {
            fireballs.forEach { (_, from, to) ->
                if (to == null ) return@forEach
                val dir = getDirection(from, to).look()
                val res = rayCastVec(dir, etherwarp = false)
                if (res.pos == null) return@forEach
                //
            }
        }
    }

    private data class Fireball(
        val entity: ArmorStand,
        val from: Vec3,
        var to: Vec3? = null,
    )

    override val running: Boolean
        get() = super.running && Dojo.dojoType == DojoType.TENACITY
}