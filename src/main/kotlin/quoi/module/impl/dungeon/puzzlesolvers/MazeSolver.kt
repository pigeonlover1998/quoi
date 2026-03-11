package quoi.module.impl.dungeon.puzzlesolvers

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.aabb
import quoi.utils.getDirection
import quoi.utils.isXZInterceptable
import quoi.utils.render.drawFilledBox
import quoi.utils.skyblock.player.PlayerUtils.rotate
import java.util.concurrent.CopyOnWriteArraySet

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/TPMazeSolver.kt
 */
object MazeSolver {
    private var tpPads = setOf<BlockPos>()
    private var correctPortals = listOf<BlockPos>()
    private var visited = CopyOnWriteArraySet<BlockPos>()

    private var walking = false
    private var nextMove = false
    private var realCells = listOf<Set<BlockPos>>()

    fun onRoomEnter(room: OdonRoom?) = with(room) {
        if (this?.name != "Teleport Maze") return@with
        reset()
        realCells = cells.map { set -> set.map { getRealCoords(it) }.toSet() }
        tpPads = endPortalFrameLocations.map { getRealCoords(it) }.toSet()
    }

    fun onPosition(packet: ClientboundPlayerPositionPacket) = with (packet.change.position) {
        if (Dungeon.currentRoom?.name != "Teleport Maze" || x % 0.5 != 0.0 || y != 69.5 || z % 0.5 != 0.0 || tpPads.isEmpty()) return@with
        visited.addAll(tpPads.filter { AABB.unitCubeFromLowerCorner(Vec3(x, y, z)).inflate(1.0, 0.0, 1.0).intersects(AABB(it)) ||
                mc.player?.boundingBox?.inflate(1.0, 0.0, 1.0)?.intersects(AABB(it)) == true })
        getCorrectPortals(Vec3(x, y, z), packet.change.yRot, packet.change.xRot)

        stop()
        scheduleTask {
            nextMove = true
        }
    }

    private fun getCorrectPortals(pos: Vec3, yaw: Float, pitch: Float) {
        if (correctPortals.isEmpty()) correctPortals = correctPortals.plus(tpPads)

        correctPortals = correctPortals.filter {
            it !in visited &&
                    isXZInterceptable(
                        AABB(it.x.toDouble(), it.y.toDouble(), it.z.toDouble(), it.x + 1.0, it.y + 4.0, it.z + 1.0).inflate(0.75, 0.0, 0.75),
                        32.0, pos, yaw, pitch
                    ) && !AABB(it).inflate(0.5, 0.0, 0.5).intersects(mc.player?.boundingBox ?: return@filter false)
        }
    }

    fun onRenderWorld(ctx: WorldRenderContext, mazeColourOne: Colour, mazeColourMultiple: Colour, mazeColourVisited: Colour) {
        if (Dungeon.currentRoom?.name != "Teleport Maze") return
        tpPads.forEach {
            val aabb = it.aabb.move(it)
            when (it) {
                in correctPortals -> ctx.drawFilledBox(aabb, if (correctPortals.size == 1) mazeColourOne else mazeColourMultiple, false)
                in visited -> ctx.drawFilledBox(aabb, mazeColourVisited, true)
                else -> ctx.drawFilledBox(aabb, Colour.WHITE.withAlpha(0.5f), true)
            }
        }
    }

    fun onTick(player: LocalPlayer) {
        if (Dungeon.currentRoom?.name != "Teleport Maze") return
        if (visited.isEmpty()) return
        if (mc.screen != null) stop().also { return }

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

    fun reset() {
        stop()
        correctPortals = listOf()
        visited = CopyOnWriteArraySet()
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