package quoi.module.impl.misc.dojo.impl

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.item.Items
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.EntityEvent
import quoi.api.events.RenderEvent
import quoi.api.events.core.EventDispatcher.totalTicks
import quoi.api.events.core.on
import quoi.module.impl.misc.dojo.Dojo
import quoi.module.impl.misc.dojo.DojoType
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.EntityUtils.helmet
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.StringUtils.toFixed
import quoi.utils.addVec
import quoi.utils.render.drawText

// sumo shit
object Force : ToggleableGroup(Dojo, "Force", subarea = "dojo arena") {
    private val highlight by switch("Highlight negative", /*desc = "Highlights mobs with negative points."*/)
    private val style = highlight(colour = Colour.RED, fillColour = Colour.RED.withAlpha(67), glow = false).childOf(::highlight)
    private val timers by switch("Timers")

    private val block by switch("Block negative", desc = "Blocks attacks on mobs with negative points.")

    private val zombies = mutableMapOf<Zombie, Int>()

    init {
        on<RenderEvent.World> {
            if (!highlight && !timers) return@on

            val currentTime = totalTicks
            zombies.forEach { (zombie, time) ->
                if (highlight && zombie.negative) style.draw(ctx, zombie.interpolatedBox)
                if (!timers) return@forEach
                val seconds = (time - currentTime) / 20.0
                val pos = zombie.interpolatedBox.center.addVec(y = 1.4)
                ctx.drawText(seconds.f, pos, scale = 1f)
            }
        }

        on<EntityEvent.ForceGlow> {
            if (highlight && entity is Zombie && entity.negative) style.draw(this)
        }

        on<EntityEvent.Attack> {
            if (entity !is Zombie) return@on
            if (block && entity.negative) {
                return@on cancel()
            }
            if (entity in zombies.keys) zombies[entity] = totalTicks + 200
        }

        on<EntityEvent.Spawn> {
            if (entity !is Zombie) return@on
            zombies[entity] = totalTicks + 200
        }

        on<EntityEvent.Despawn> {
            if (entity !is Zombie && entity !in zombies.keys) return@on
            zombies.remove(entity)
        }
    }

    override fun onDisable() {
        zombies.clear()
    }

    private val Double.f: String
        get() {
            val col = when {
                this > 7 -> "a"
                this > 3 -> "e"
                else -> "c"
            }
            return "&$col${this.toFixed()}"
        }

    private val LivingEntity.negative: Boolean
        get() = helmet.item == Items.LEATHER_HELMET

    override val running: Boolean
        get() = super.running && Dojo.dojoType == DojoType.FORCE
}