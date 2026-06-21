package quoi.module.impl.dungeon.puzzlesolvers.impl

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.*
import quoi.api.events.core.Event
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
import quoi.module.impl.dungeon.puzzlesolvers.PuzzleSolvers
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.SettingGroup
import quoi.utils.*
import quoi.utils.render.drawFilledBox
import quoi.utils.skyblock.player.RotationUtils.rotate
import java.util.concurrent.CopyOnWriteArraySet

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/TPMazeSolver.kt
 */
object TeleportMaze : SettingGroup(PuzzleSolvers, "Teleport maze") {
    private val solver by switch("Solver", desc = "Shows the solution for the TP maze puzzle.")
    private val colourOne by colourPicker("Colour for one", Colour.MINECRAFT_GREEN.withAlpha(0.5f), true, desc = "Colour for when there is a single solution.").childOf(::solver)
    private val colourMultiple by colourPicker("Colour for multiple", Colour.MINECRAFT_GOLD.withAlpha(0.5f), true, desc = "Colour for when there are multiple solutions.").childOf(::solver)
    private val colourVisited by colourPicker("Colour for visited", Colour.MINECRAFT_RED.withAlpha(0.5f), true, desc = "Colour for the already used TP pads.").childOf(::solver)
    private val auto by switch("Auto").asParent()

    private var tpPads = setOf<BlockPos>()
    private var correctPortals = setOf<BlockPos>()
    private var visited = CopyOnWriteArraySet<BlockPos>()

    private var walking = false
    private var nextMove = false
    private var realCells = listOf<Set<BlockPos>>()

    override fun shouldHandle(event: Event): Boolean {
        if (!super.shouldHandle(event)) return false

        if (event is DungeonEvent.Room.Enter) return true

        return Dungeon.currentRoom?.name == "Teleport Maze"
    }

    init {
        on<DungeonEvent.Room.Enter> {
            if (room?.name != "Teleport Maze") return@on
            reset()
            realCells = cells.map { set -> set.map { room.getRealCoords(it) }.toSet() }
            tpPads = endPortalFrameLocations.map { room.getRealCoords(it) }.toSet()
        }

        on<PacketEvent.Received, ClientboundPlayerPositionPacket> {
            if (!auto && !solver) return@on
            if (tpPads.isEmpty()) return@on

            val (x, y, z) = packet.change.position
            if (x % 0.5 != 0.0 || y != 69.5 || z % 0.5 != 0.0) return@on

            visited.addAll(tpPads.filter {
                AABB.unitCubeFromLowerCorner(Vec3(x, y, z)).inflate(1.0, 0.0, 1.0).intersects(AABB(it)) ||
                player.boundingBox.inflate(1.0, 0.0, 1.0).intersects(AABB(it))
            })

            getCorrectPortals(Vec3(x, y, z), packet.change.yRot, packet.change.xRot)

            stop()
            Scheduler.scheduleTask {
                nextMove = true
            }
        }

        on<RenderEvent.World> {
            if (!solver) return@on
            tpPads.forEach {
                val aabb = it.bounds?.move(it) ?: it.aabb
                when (it) {
                    in correctPortals -> ctx.drawFilledBox(aabb, if (correctPortals.size == 1) colourOne else colourMultiple, false)
                    in visited -> ctx.drawFilledBox(aabb, colourVisited, true)
                    else -> ctx.drawFilledBox(aabb, Colour.WHITE.withAlpha(0.5f), true)
                }
            }
        }

        on<TickEvent.End> {
            if (!auto || ClearExecutor.active || visited.isEmpty()) return@on
            if (mc.screen != null) return@on stop()

            if (nextMove) {
                val targetPos = getPad(player.position())

                if (targetPos != null) {
                    val dir = getDirection(player.eyePosition, Vec3.atCenterOf(targetPos))
                    player.rotate(dir)

                    mc.options.keyUp.isDown = true
                    walking = true
                } else {
                    stop()
                }

                nextMove = false

            } else if (walking) {
                mc.options.keyUp.isDown = true
            }
        }

        on<WorldEvent.Change> {
            reset()
        }
    }

    private fun getCorrectPortals(pos: Vec3, yaw: Float, pitch: Float) {
        if (correctPortals.isEmpty()) correctPortals = correctPortals.plus(tpPads)

        correctPortals = correctPortals.filterTo(mutableSetOf()) {
            it !in visited &&
            isXZInterceptable(
                AABB(
                    it.x.toDouble(),
                    it.y.toDouble(),
                    it.z.toDouble(),
                    it.x + 1.0,
                    it.y + 4.0,
                    it.z + 1.0
                ).inflate(0.75, 0.0, 0.75),
                32.0, pos, yaw, pitch
            ) && !it.aabb.inflate(0.5, 0.0, 0.5).intersects(player.boundingBox)
        }
    }

    private fun getPad(pos: Vec3): BlockPos? {

        val currentPad = tpPads.minByOrNull { pos.distanceToSqr(Vec3.atCenterOf(it)) } ?: return null
        val currentCell = realCells.find { currentPad in it } ?: return null

        if (correctPortals.size == 1) {
            val correctPad = correctPortals.first()
            if (correctPad in currentCell) return correctPad
        }

        val unvisited = currentCell.filter { it !in visited }
        return unvisited.find { it.x != currentPad.x && it.z != currentPad.z }
            ?: unvisited.maxByOrNull { pos.distanceToSqr(Vec3.atCenterOf(it)) }
    }

    private fun stop() {
        if (walking) {
            mc.options.keyUp.isDown = false
            walking = false
        }
    }

    private fun reset() {
        stop()
        correctPortals = emptySet()
        visited.clear()
        nextMove = false
    }


    private val endPortalFrameLocations = setOf(
        BlockPos(4, 69, 14), BlockPos(10, 69, 14), BlockPos(10, 69, 20), BlockPos(4, 69, 20), // emerald

        BlockPos(4, 69, 12), BlockPos(4, 69, 6), BlockPos(10, 69, 6), BlockPos(10, 69, 12), //

        BlockPos(12, 69, 28), BlockPos(12, 69, 22), BlockPos(18, 69, 22), BlockPos(18, 69, 28), // lapis

        BlockPos(26, 69, 14), BlockPos(20, 69, 20), BlockPos(20, 69, 14), BlockPos(26, 69, 20), // iron

        BlockPos(26, 69, 28), BlockPos(26, 69, 22), BlockPos(20, 69, 28), BlockPos(20, 69, 22), // coal

        BlockPos(10, 69, 22), BlockPos(10, 69, 28), BlockPos(4, 69, 28), BlockPos(4, 69, 22), // diamond

        BlockPos(20, 69, 6), BlockPos(20, 69, 12), BlockPos(26, 69, 12), BlockPos(26, 69, 6), // gold

        BlockPos(15, 69, 14), // end
        BlockPos(15, 69, 12), // start
    )

    private val cells = listOf(
        setOf(BlockPos(4, 69, 14), BlockPos(10, 69, 14), BlockPos(10, 69, 20), BlockPos(4, 69, 20)),
        setOf(BlockPos(4, 69, 12), BlockPos(4, 69, 6), BlockPos(10, 69, 6), BlockPos(10, 69, 12)),
        setOf(BlockPos(12, 69, 28), BlockPos(12, 69, 22), BlockPos(18, 69, 22), BlockPos(18, 69, 28)),
        setOf(BlockPos(26, 69, 14), BlockPos(20, 69, 20), BlockPos(20, 69, 14), BlockPos(26, 69, 20)),
        setOf(BlockPos(26, 69, 28), BlockPos(26, 69, 22), BlockPos(20, 69, 28), BlockPos(20, 69, 22)),
        setOf(BlockPos(10, 69, 22), BlockPos(10, 69, 28), BlockPos(4, 69, 28), BlockPos(4, 69, 22)),
        setOf(BlockPos(20, 69, 6), BlockPos(20, 69, 12), BlockPos(26, 69, 12), BlockPos(26, 69, 6))
    )

}