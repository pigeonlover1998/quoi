package quoi.module.impl.dungeon.puzzlesolvers.impl

import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.monster.Silverfish
import net.minecraft.world.phys.AABB
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
import quoi.api.vec.Vec2i
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
import quoi.module.impl.dungeon.puzzlesolvers.PuzzleSolvers
import quoi.module.impl.dungeon.puzzlesolvers.Repositionable
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.SettingGroup
import quoi.utils.*
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.WorldUtils.state
import quoi.utils.render.drawLine
import quoi.utils.render.drawWireFrameBox
import quoi.utils.skyblock.item.TeleportUtils
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.PlayerUtils.useItem

/**
 * modified Skyblocker (LGPL-3.0) (c) kevinthegreat1
 * original: https://github.com/SkyblockerMod/Skyblocker/blob/master/src/main/java/de/hysky/skyblocker/skyblock/dungeon/puzzle/IcePath.java
 */
object IcePath : SettingGroup(PuzzleSolvers, "Ice path"), Repositionable {

    private val solver by switch("Solver", desc = "Shows the solution for the ice path puzzle.")
    private val colour by colourPicker("Colour", Colour.MINECRAFT_GREEN, desc = "Colour for the solver.").childOf(::solver)
    val auto by switch("Auto").asParent()

    private const val BOARD_SIZE = 17
    private val DIRECTIONS = setOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    private val silverfishBoard = Array(BOARD_SIZE) { BooleanArray(BOARD_SIZE) }
    private var silverfishPos: Vec2i? = null
    private var silverfishPath = mutableListOf<Vec3>()

    override var repositionTicker: Ticker? = null
    private var lastShotTime = 0L
    private var waitingForUpdate = false

    private var lastPos = Vec3.ZERO
    private var ticks = 0
    private var isMoving = false

    override fun shouldHandle(event: Event): Boolean {
        if (!super.shouldHandle(event)) return false

        if (event is DungeonEvent.Room.Enter) return true

        return Dungeon.currentRoom?.name == "Ice Path"
    }

    init {
        on<DungeonEvent.Room.Enter> {
            if (room?.name == "Ice Path") reset()
        }

        on<TickEvent.End> {
            if (!auto && !solver) return@on
            if (ClearExecutor.active) return@on
            val room = Dungeon.currentRoom ?: return@on

            val boardChanged = updateBoard(room)

            val searchBox = AABB.ofSize(Vec3.atCenterOf(room.getRealCoords(BlockPos(15, 66, 16))), 16.0, 16.0, 16.0)
            val silverfish = EntityUtils.getEntities<Silverfish>(searchBox).firstOrNull() ?: return@on

            val pos = silverfish.position()
            if (pos.distanceToSqr(lastPos) > 0.0001) {
                ticks = 0
                isMoving = true
            } else {
                ticks++
                if (ticks > 1) isMoving = false
            }
            lastPos = pos


            if (!isMoving) {
                val relPos = room.getRelativeCoords(silverfish.blockPosition())
                val newSilverfishPos = Vec2i((24 - relPos.z), (23 - relPos.x))

                if (newSilverfishPos.x !in 0 until BOARD_SIZE || newSilverfishPos.z !in 0 until BOARD_SIZE) return@on
                if (newSilverfishPos != silverfishPos || boardChanged) {
                    silverfishPos = newSilverfishPos
                    solve(room)
                }
            }

            if (auto) auto(player, silverfish, PuzzleSolvers.shootCd, PuzzleSolvers.missCd)
        }

        on<RenderEvent.World> {
            if (!solver) return@on
            if (silverfishPath.isEmpty()) return@on

            val points = silverfishPath.map { it.addVec(0.5, 1.0, 0.5) }
            ctx.drawLine(points, colour, depth = true)
            if (silverfishPath.size > 2) ctx.drawWireFrameBox(silverfishPath[1].aabb, Colour.RED, depth = true)
        }

        on<WorldEvent.Change> {
            reset()
        }
    }

    private fun updateBoard(room: OdonRoom): Boolean {
        var boardChanged = false

        for (row in 0 until BOARD_SIZE) {
            for (col in 0 until BOARD_SIZE) {
                val isBlock = !room.getRealCoords(BlockPos(23 - col, 67, 24 - row)).state.isAir
                if (silverfishBoard[row][col] != isBlock) {
                    silverfishBoard[row][col] = isBlock
                    boardChanged = true
                }
            }
        }
        return boardChanged
    }

    private fun solve(room: OdonRoom) {
        val start = silverfishPos ?: return

        val visited = mutableSetOf<Vec2i>()
        val queue = ArrayDeque<MutableList<Vec2i>>()

        queue.add(mutableListOf(start))
        visited.add(start)

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val pos = path.last()

            if (pos.x == 0 && pos.z in 7..9) {
                silverfishPath = path.mapTo(mutableListOf()) { (row, col) ->
                    room.getRealCoords(BlockPos(23 - col, 66, 24 - row)).vec3
                }
                return
            }

            for ((dr, dc) in DIRECTIONS) {
                var nextRow = pos.x
                var nextCol = pos.z

                while (
                    nextRow + dr in 0 until BOARD_SIZE &&
                    nextCol + dc in 0 until BOARD_SIZE &&
                    !silverfishBoard[nextRow + dr][nextCol + dc]
                ) {
                    nextRow += dr
                    nextCol += dc
                }

                val nextPos = Vec2i(nextRow, nextCol)

                if (visited.add(nextPos)) {
                    val newPath = ArrayList(path).apply { add(nextPos) }
                    queue.add(newPath)
                }
            }
        }
    }

    private fun auto(player: LocalPlayer, silverfish: Silverfish, shootCd: Long, missCd: Long) {
        if (mc.screen != null || silverfishPath.size < 2) return

        repositionTicker?.let {
            if (it.tick()) scheduleTask { repositionTicker = null }
            return
        }

        val currentTime = System.currentTimeMillis()
        val nextSpot = silverfishPath[1].blockPos

        if (isMoving) {
            waitingForUpdate = false
            if (!player.at(nextSpot)) reposition(nextSpot)
            return
        }

        if (waitingForUpdate) {
            if (currentTime - lastShotTime > missCd) waitingForUpdate = false
            else return
        }

        if (currentTime - lastShotTime < shootCd) return

        val currSpot = silverfish.blockPosition().atY(66)
        if (TeleportUtils.getEtherwarpDirection(currSpot) == null) return

        if (!player.at(currSpot)) {
            reposition(currSpot)
            return
        }

        val dir = TeleportUtils.getEtherwarpDirection(nextSpot) ?: return
        player.useItem(yaw = dir.yaw, pitch = 90)

        lastShotTime = currentTime
        waitingForUpdate = true
    }

    private fun reset() {
        silverfishBoard.forEach { it.fill(false) }
        silverfishPos = null
        silverfishPath.clear()

        repositionTicker = null
        lastShotTime = 0L
        waitingForUpdate = false

        lastPos = Vec3.ZERO
        ticks = 0
        isMoving = false
    }
}