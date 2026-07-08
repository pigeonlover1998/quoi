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
import quoi.utils.EntityUtils.colourFromDistance
import quoi.utils.EntityUtils.getEntities
import quoi.utils.EntityUtils.helmet
import quoi.utils.EntityUtils.interpolatedBox
import quoi.utils.equalsOneOf
import quoi.utils.items
import quoi.utils.skyblock.player.SwapManager

// sword shit
object Discipline : ToggleableGroup(Dojo, "Discipline", subarea = "dojo arena") { // maybe add timers when mobs will despawn..

    private val highlight by switch("Highlight correct", /*desc = "Highlights mobs based on held weapon."*/)
    private val style = highlight(glow = false).childOf(::highlight)
    private val tracer = tracer(customColour = true, distance = null).childOf(::highlight) // kinda useless

    private val block by switch("Block wrong", desc = "Blocks attacks on wrong mobs.")
    private val swap by switch("Auto swap", desc = "Automatically swaps to correct weapon.")

    private val stupid = mapOf(
        Items.WOODEN_SWORD to Items.LEATHER_HELMET,
        Items.IRON_SWORD to Items.IRON_HELMET,
        Items.GOLDEN_SWORD to Items.GOLDEN_HELMET,
        Items.DIAMOND_SWORD to Items.DIAMOND_HELMET
    )

    init {
        on<RenderEvent.World> {
            if (!highlight) return@on
            val held = player.mainHandItem.item
            getEntities<Zombie>(Dojo.centre, radius = 16.0) { it.helmet.item == stupid[held] }.forEach {
                style.draw(ctx, it.interpolatedBox)
                tracer.draw(ctx, it, it.colourFromDistance)
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
    }

    override val running: Boolean
        get() = super.running && Dojo.dojoType == DojoType.DISCIPLINE
}