package quoi.module.impl.misc

import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.RedstoneLampBlock
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.Vec3
import quoi.api.events.BlockEvent
import quoi.api.events.TickEvent
import quoi.module.Module
import quoi.utils.EntityUtils.getEntities
import quoi.utils.getDirection
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.PlayerUtils.useItem

object AutoCarnival : Module(
    "Auto Carnival",
    desc = "Auto zombie shootout in carnival area",
    subarea = "carnival"
) {
    private val clickDelay by slider("Click delay", 100L, 50L, 500L, 50L, desc = "Dart tube shooting delay")

    private var lastClick = 0L
    private var lamp: Vec3? = null

    init {
        on<TickEvent.End> {
            if (player.mainHandItem.skyblockId != "CARNIVAL_DART_TUBE") return@on
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClick < clickDelay) return@on
            val target = getTargets().firstOrNull() ?: return@on
            val dir = getDirection(to = target)
            player.useItem(dir)
            lastClick = currentTime
        }

        on<BlockEvent.Update> {
            if (old.block !is RedstoneLampBlock || updated.block !is RedstoneLampBlock) return@on
            val old = old.getValue(BlockStateProperties.LIT)
            val new = updated.getValue(BlockStateProperties.LIT)
            lamp = when {
                !old && new -> pos.center
                old && !new -> null
                else -> lamp
            }
        }
    }

    private fun getTargets(): List<Vec3> {
        val zombies = getEntities<Zombie>(50.0) { !it.isDeadOrDying }
            .groupBy({ it.getItemBySlot(EquipmentSlot.HEAD).item }) { z ->
                val m = z.deltaMovement
                Vec3(z.x + m.x * 8.0, z.y + z.eyeHeight, z.z + m.z * 8.0)
            }

        return buildList {
            lamp?.let(::add)
            addAll(zombies[Items.DIAMOND_HELMET].orEmpty())
            addAll(zombies[Items.GOLDEN_HELMET].orEmpty())
            addAll(zombies[Items.IRON_HELMET].orEmpty())
            addAll(zombies[Items.LEATHER_HELMET].orEmpty())
        }
    }
}