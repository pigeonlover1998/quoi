package quoi.module.impl.dungeon.puzzlesolvers

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.monster.Silverfish
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.utils.*
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.WorldUtils.state
import quoi.utils.render.drawLine
import quoi.utils.render.drawWireFrameBox
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.PlayerUtils.useItem
import quoi.utils.skyblock.player.SwapManager

/**
 * modified Skyblocker (LGPL-3.0) (c) kevinthegreat1
 * original: https://github.com/SkyblockerMod/Skyblocker/blob/master/src/main/java/de/hysky/skyblocker/skyblock/dungeon/puzzle/IcePath.java
 */
object IcePathSolver { // todo add pre fire maybe
    private const val BOARD_SIZE = 17
    private val DIRECTIONS = setOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    private val silverfishBoard = Array(BOARD_SIZE) { BooleanArray(BOARD_SIZE) }
    private var silverfishPos: Vec2? = null
    private var silverfishPath = mutableListOf<Vec3>()

    private var repositionTicker: Ticker? = null
    private var lastShotTime = 0L
    private var waitingForUpdate = false

    private var lastPos = Vec3.ZERO
    private var ticks = 0
    private var isMoving = false


    fun onRoomEnter(room: OdonRoom?) = with(room) {
        if (this?.name != "Ice Path") return@with
        reset()
    }

    fun onTick(player: LocalPlayer, auto: Boolean, shootCd: Long, missCd: Long) {
        val room = Dungeon.currentRoom ?: return
        if (room.name != "Ice Path") return

        val boardChanged = updateBoard(room)

        val searchBox = AABB.ofSize(Vec3.atCenterOf(room.getRealCoords(BlockPos(15, 66, 16))), 16.0, 16.0, 16.0)
        val silverfish = EntityUtils.getEntities<Silverfish>(searchBox).firstOrNull() ?: return

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
            val newSilverfishPos = Vec2((24 - relPos.z), (23 - relPos.x))

            if (newSilverfishPos.x !in 0 until BOARD_SIZE || newSilverfishPos.z !in 0 until BOARD_SIZE) return
            if (newSilverfishPos != silverfishPos || boardChanged) {
                silverfishPos = newSilverfishPos
                solve(room)
            }
        }

        if (auto) auto(player, silverfish, shootCd, missCd)
    }

    private fun updateBoard(room: OdonRoom): Boolean {
        var boardChanged = false
        val pos = BlockPos.MutableBlockPos(23, 67, 24)

        for (row in 0 until BOARD_SIZE) {
            for (col in 0 until BOARD_SIZE) {
                val isBlock = !room.getRealCoords(pos).state.isAir
                if (silverfishBoard[row][col] != isBlock) {
                    silverfishBoard[row][col] = isBlock
                    boardChanged = true
                }
                pos.move(Direction.WEST)
            }
            pos.move(BOARD_SIZE, 0, -1)
        }
        return boardChanged
    }

    private fun solve(room: OdonRoom) {
        val start = silverfishPos ?: return
        val visited = mutableSetOf<Vec2>()
        val queue = ArrayDeque<MutableList<Vec2>>()

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

                val nextPos = Vec2(nextRow, nextCol)

                if (visited.add(nextPos)) {
                    val newPath = ArrayList(path).apply { add(nextPos) }
                    queue.add(newPath)
                }
            }
        }
    }

    fun onRenderWorld(ctx: WorldRenderContext, colour: Colour) {
        if (Dungeon.currentRoom?.name != "Ice Path" || silverfishPath.isEmpty()) return

        val points = silverfishPath.map { it.addVec(0.5, 1.0, 0.5) }
        ctx.drawLine(points, colour, depth = true)
        if (silverfishPath.size > 2) ctx.drawWireFrameBox(silverfishPath[1].aabb, Colour.RED, depth = true)
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

            if (!player.at(nextSpot)) {
                reposition(player, nextSpot)
                return
            }

//            if (silverfishPath.size > 2 && currentTime - lastShotTime >= shootCd) { // idk, maybe remove this
//                val pos = silverfish.position()
//
//                val dist = pos.distanceToSqr(Vec3.atCenterOf(nextSpot))
//
//                if (dist < 2.5) {
//                    val nextNextSpot = silverfishPath[2].blockPos
//                    val dir = getEtherwarpDirection(nextNextSpot) ?: return
//
//                    player.useItem(yaw = dir.yaw, pitch = 90)
//                    lastShotTime = currentTime
//                    waitingForUpdate = true
//                }
//            }
            return
        }

        if (waitingForUpdate) {
            if (currentTime - lastShotTime > missCd)
                waitingForUpdate = false
            else
                return
        }

        if (currentTime - lastShotTime < shootCd) return

        val currSpot = silverfish.blockPosition().atY(66)
        if (getEtherwarpDirection(currSpot) == null) return

        if (!player.at(currSpot)) {
            reposition(player, currSpot)
            return
        }

        val dir = getEtherwarpDirection(nextSpot) ?: return
        player.useItem(yaw = dir.yaw, pitch = 90)

        lastShotTime = currentTime
        waitingForUpdate = true
    }

    private fun reposition(player: LocalPlayer, spot: BlockPos) {
        if (repositionTicker != null) return

        repositionTicker = ticker {
            val r = SwapManager.swapById("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END").success
            if (!mc.options.keyShift.isDown) {
                action {
                    mc.options.keyShift.isDown = true
                }
                delay(2)
            }
            await {
                if (r) return@await true
                else return@await false.also {
                    repositionTicker = null
                }
            }
            action {
                val dir = getEtherwarpDirection(spot)
                if (dir == null) {
                    repositionTicker = null
                    return@action
                }
                player.useItem(dir)
            }
            await { player.at(spot) }
            action {
                SwapManager.swapByLore("Shortbow: Instantly shoots!")
            }
        }
    }

    fun reset() {
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