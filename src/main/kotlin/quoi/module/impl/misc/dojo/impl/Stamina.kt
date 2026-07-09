package quoi.module.impl.misc.dojo.impl

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.on
import quoi.module.impl.misc.dojo.Dojo
import quoi.module.impl.misc.dojo.Dojo.centrePos
import quoi.module.impl.misc.dojo.DojoType
import quoi.module.settings.group.ToggleableGroup
import quoi.utils.BlockPos
import quoi.utils.WorldUtils.state

// jump in a hole shit
object Stamina : ToggleableGroup(Dojo, "Stamina", subarea = "dojo arena") { // The server is too laggy for dojo challenges. Try again in a bit! (20)

    private val highlight by switch("Highlight holes")
    private val style = highlight(glow = false).childOf(::highlight)

//    private val auto by switch("Auto") // todo

    private var holes: List<AABB> = emptyList()

    init {
        on<TickEvent.Start> {
            val topWalls = BlockPos.betweenClosed( // scan for wall blocks
                centrePos.x - 17, centrePos.y + 1, centrePos.z - 17,
                centrePos.x + 17, centrePos.y + 1, centrePos.z + 17
            ).map { it.immutable() }.filter { !it.state.isAir }

            val walls = (topWalls.groupBy { it.x }.values + topWalls.groupBy { it.z }.values) // aabbs for each wall
                .filter { it.size >= 13 } // needs to be long enough
                .map { it.toAABB().setMaxY(centrePos.y + 6.0) } // expand to top

            val foundHoles = mutableListOf<AABB>()

            for (wall in walls) {
                val min = BlockPos(wall.minX, wall.minY, wall.minZ)
                val max = BlockPos(wall.maxX - 1, wall.maxY, wall.maxZ - 1)

                val air = BlockPos.betweenClosed(min, max)
                    .map { it.immutable() }
                    .filter { it.state.isAir }
                    .toMutableSet()

                while (air.isNotEmpty()) {
                    foundHoles.add(floodFill(air.first(), air).toAABB())
                }
            }

            holes = foundHoles
        }

        on<RenderEvent.World> {
            if (!highlight) return@on
            holes.forEach {
                style.draw(ctx, it)
            }
        }
    }

    private fun floodFill(first: BlockPos, air: MutableSet<BlockPos>): List<BlockPos> {
        val group = mutableListOf<BlockPos>()
        val stupid = ArrayDeque<BlockPos>().apply { add(first) }
        air.remove(first)

        while (stupid.isNotEmpty()) {
            val pos = stupid.removeLast()
            group.add(pos)

            Direction.entries.forEach {
                val neighbour = pos.relative(it)
                if (air.remove(neighbour)) stupid.add(neighbour)
            }
        }

        return group
    }

    private fun Iterable<BlockPos>.toAABB(): AABB {
        val min = BlockPos(minOf { it.x }, minOf { it.y }, minOf { it.z })
        val max = BlockPos(maxOf { it.x }, maxOf { it.y }, maxOf { it.z })
        return AABB.encapsulatingFullBlocks(min, max)
    }

    override fun onDisable() {
        holes = emptyList()
    }

    override val running: Boolean
        get() = super.running && Dojo.dojoType == DojoType.STAMINA
}