package quoi.module.impl.misc.dojo.impl

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.item.Items
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.EntityEvent
import quoi.api.events.RenderEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.dojo.Dojo
import quoi.module.impl.misc.dojo.DojoType
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.EntityUtils.getEntities
import quoi.utils.EntityUtils.helmet
import quoi.utils.EntityUtils.interpolatedBox

// sumo shit
object Force : ToggleableGroup(Dojo, "Force", subarea = "dojo arena") {
    private val highlight by switch("Highlight negative", /*desc = "Highlights mobs with negative points."*/)
    private val style = highlight(colour = Colour.RED, fillColour = Colour.RED.withAlpha(67), glow = false).childOf(::highlight)

    private val block by switch("Block negative", desc = "Blocks attacks on mobs with negative points.")

    init {
        on<RenderEvent.World> {
            if (!highlight) return@on

            getEntities<Zombie>(Dojo.centre, radius = 11.0) { it.negative }.forEach {
                style.draw(ctx, it.interpolatedBox)
            }
        }

        on<EntityEvent.ForceGlow> {
            if (highlight && entity is Zombie && entity.negative) style.draw(this)
        }

        on<EntityEvent.Attack> {
            if (block && entity is Zombie && entity.negative) {
                cancel()
            }
        }
    }

    private val LivingEntity.negative: Boolean
        get() = helmet.item == Items.LEATHER_HELMET

    override val running: Boolean
        get() = super.running && Dojo.dojoType == DojoType.FORCE
}