package quoi.module.impl.misc.dojo.impl

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.item.Items
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
import quoi.utils.equalsOneOf
import quoi.utils.items
import quoi.utils.render.drawText
import quoi.utils.skyblock.player.SwapManager

// sword shit
object Discipline : ToggleableGroup(Dojo, "Discipline", subarea = "dojo arena") { // maybe add timers when mobs will despawn..

    private val highlight by switch("Highlight correct", /*desc = "Highlights mobs based on held weapon."*/)
    private val style = highlight().childOf(::highlight)
    private val timers by switch("Timers")

    private val block by switch("Block wrong", desc = "Blocks attacks on wrong mobs.")
    private val swap by switch("Auto swap", desc = "Automatically swaps to correct weapon.")

    private val stupid = mapOf(
        Items.WOODEN_SWORD to Items.LEATHER_HELMET,
        Items.IRON_SWORD to Items.IRON_HELMET,
        Items.GOLDEN_SWORD to Items.GOLDEN_HELMET,
        Items.DIAMOND_SWORD to Items.DIAMOND_HELMET
    )

    private val zombies = mutableMapOf<Zombie, Int>()

    init {
        on<RenderEvent.World> {
            if (!highlight && !timers) return@on

            val held = player.mainHandItem.item

            val currentTime = totalTicks
            zombies.forEach { (zombie, time) ->
                if (highlight && style.style != "Glow") {
                    if (zombie.helmet.item == stupid[held]) style.draw(ctx, zombie.interpolatedBox)
                }
                if (!timers) return@forEach
                val seconds = (time - currentTime) / 20.0
                val pos = zombie.interpolatedBox.center.addVec(y = 1.4)
                ctx.drawText(seconds.f, pos, scale = 1f)
            }
        }

        on<EntityEvent.ForceGlow> {
            if (!highlight) return@on
            if (style.style != "Glow") return@on
            val held = player.mainHandItem.item
            zombies.forEach { (zombie, time) ->
                if (zombie.helmet.item == stupid[held]) style.draw(this)
            }
        }

        on<EntityEvent.Attack> {
            if (entity !is LivingEntity || (!block && !swap)) return@on

            val held = player.mainHandItem.item
            val helmet = entity.helmet.item

            val sword = stupid.entries.find { it.value == helmet }?.key ?: return@on

            if (block && held.equalsOneOf(stupid.keys) && stupid[held] != helmet) {
                cancel()
            }

            if (swap && held != sword) {
                val slot = player.inventory.items.indexOfFirst { it.item == sword }
                SwapManager.swapToSlot(slot)
            }
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

    override val running: Boolean
        get() = super.running && Dojo.dojoType == DojoType.DISCIPLINE
}