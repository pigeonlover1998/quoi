package quoi.module.impl.dungeon.puzzlesolvers.impl

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.events.DungeonEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.Event
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
import quoi.module.impl.dungeon.puzzlesolvers.PuzzleSolvers
import quoi.module.impl.dungeon.puzzlesolvers.Repositionable
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.SettingGroup
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Ticker
import quoi.utils.WorldUtils.state
import quoi.utils.blockPos
import quoi.utils.equalsOneOf
import quoi.utils.getDirection
import quoi.utils.render.drawLine
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.MovementUtils.isMoving
import quoi.utils.skyblock.player.PlayerUtils.useItem
import kotlin.math.abs

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/IceFillSolver.kt
 */
object IceFill : SettingGroup(PuzzleSolvers, "Ice fill"), Repositionable {
    private val solver by switch("Solver", desc = "Shows the solution for the ice fill puzzle.")
    private val colour by colourPicker("Colour", Colour.MAGENTA, allowAlpha = true).childOf(::solver)
    private val auto by switch("Auto", desc = "Automatically completes the ice fill puzzle.")
    private val delay by slider("Delay", 2, 1, 10, 1, unit = "t").childOf(::auto)
    private val reposition by switch("Auto reposition").childOf(::auto)

    private var iceFillFloors = PuzzleSolvers.loadSolution("iceFillFloors.json", IceFillData())
    private var currentPatterns: ArrayList<Vec3> = ArrayList()

    override var repositionTicker: Ticker? = null

    init {
        on<RenderEvent.World> {
            if (!solver) return@on
            ctx.drawLine(currentPatterns, colour, true)
        }

        on<DungeonEvent.Room.Enter> {
            if (room?.name != "Ice Fill") return@on
            repositionTicker = null
            if (currentPatterns.isNotEmpty()) return@on
            val patterns = /*if (optimizePatterns) iceFillFloors.hard else */iceFillFloors.easy

            repeat(3) { index ->
                val floorIdentifiers = iceFillFloors.identifier[index]

                for (patternIndex in floorIdentifiers.indices) {
                    if (room.isRealAir(floorIdentifiers[patternIndex][0]) && !room.isRealAir(floorIdentifiers[patternIndex][1])) {
                        currentPatterns.addAll(patterns[index][patternIndex].map { Vec3(room.getRealCoords(it)).add(0.5, 0.1, 0.5) })
                        return@repeat
                    }
                }
                modMessage("§cFailed to scan floor $index")
            }

            if (currentPatterns.isNotEmpty()) {
                stupidStairs()
                fillGaps()
            }
        }

        on<TickEvent.End> {
            if (!auto || ClearExecutor.active || mc.screen != null) return@on
            if (Dungeon.currentRoom?.getRealCoords(BlockPos(15, 71, 26))?.state?.block == Blocks.PACKED_ICE) return@on

            repositionTicker?.let {
                if (it.tick()) repositionTicker = null
                return@on
            }

            if (reposition && player.y !in (69.5..72.5) ) { // untested
                if (player.isMoving) return@on
                val spot = getBlock()
                if (spot != null) {
                    reposition(spot, bow = false, stand = true, awaitStand = true)
                    return@on
                }
            }
            if (player.mainHandItem.skyblockId?.equalsOneOf("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END") == false) return@on

            val index = currentPatterns.indexOfFirst {
                player.x == it.x && (player.y + 0.1) == it.y && player.z == it.z
            }

            if (index == -1 || index >= currentPatterns.size - 1) {
                lastIndex = -1
                return@on
            }

            if (lastIndex == -1 || index > lastIndex) {
                lastIndex = index
                ticks = 0
            }

            if (lastIndex >= currentPatterns.size - 1) return@on

            if (++ticks == delay) {
                val current = currentPatterns[lastIndex]
                val next = currentPatterns[lastIndex + 1]

                val from = Vec3(current.x, current.y - 0.1 + player.eyeHeight, current.z)

                val dir = getDirection(from, next)
                player.useItem(dir)

                lastIndex++
                ticks = 0
            }
        }

        on<WorldEvent.Change> {
            currentPatterns.clear()
            ticks = 0
            lastIndex = -1

            repositionTicker = null
        }
    }

    override fun shouldHandle(event: Event): Boolean {
        if (!super.shouldHandle(event)) return false

        if (event is DungeonEvent.Room.Enter) return true

        return Dungeon.currentRoom?.name == "Ice Fill" && currentPatterns.isNotEmpty()
    }

    private var ticks = 0
    private var lastIndex = -1

    private fun getBlock(): BlockPos? = currentPatterns.firstNotNullOfOrNull { vec ->
        vec.blockPos.below().takeIf { it.state.block == Blocks.ICE }
    }


    private fun fillGaps() {
        val updated = ArrayList<Vec3>(currentPatterns.size * 2)

        for (i in 0 until currentPatterns.size - 1) {
            val p1 = currentPatterns[i]; val p2 = currentPatterns[i + 1]
            updated.add(p1)

            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            val dz = p2.z - p1.z

            val steps = maxOf(abs(dx), abs(dy), abs(dz)).toInt()

            for (s in 1 until steps) {
                val r = s.toDouble() / steps
                updated.add(Vec3(p1.x + dx * r, p1.y + dy * r, p1.z + dz * r))
            }
        }
        updated.add(currentPatterns.last())
        currentPatterns = updated
    }

    private fun stupidStairs() {
        val updatedPatterns = ArrayList<Vec3>(currentPatterns.size + 2)
        var lastPoint: Vec3 = currentPatterns[0]
        var added71 = false
        var added72 = false

        for (point in currentPatterns) {
            val y = point.y
            if (!added71 && y == 71.1) {
                updatedPatterns.add(Vec3((lastPoint.x + point.x) / 2, 71.1, (lastPoint.z + point.z) / 2))
                added71 = true
            } else if (!added72 && y == 72.1) {
                updatedPatterns.add(Vec3((lastPoint.x + point.x) / 2, 72.1, (lastPoint.z + point.z) / 2))
                added72 = true
            }
            updatedPatterns.add(point)
            lastPoint = point
        }
        currentPatterns = updatedPatterns
    }
    private fun OdonRoom.isRealAir(pos: BlockPos): Boolean = getRealCoords(pos).state.isAir

    private data class IceFillData(
        val identifier: List<List<List<BlockPos>>> = emptyList(),
        val easy: List<List<List<BlockPos>>> = emptyList(),
        val hard: List<List<List<BlockPos>>> = emptyList()
    )
}