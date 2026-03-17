package quoi.module.impl.dungeon.puzzlesolvers

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.MapItem
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.utils.*
import quoi.utils.render.drawWireFrameBox
import quoi.utils.skyblock.player.AuraManager

/**
 * modified Skyblocker (LGPL-3.0) (c) kevinthegreat1
 * original:
 *          https://github.com/SkyblockerMod/Skyblocker/blob/master/src/main/java/de/hysky/skyblocker/skyblock/dungeon/puzzle/TicTacToe.java
 *          https://github.com/SkyblockerMod/Skyblocker/blob/master/src/main/java/de/hysky/skyblocker/utils/tictactoe/TicTacToeUtils.java
 */
object TicTacToeSolver {

    private var lastBoardHash = 0
    private var bestMove: BlockPos? = null
    private var predictedMove: BlockPos? = null

    private var lastClick = 0L

    private val MOVE_ORDER = intArrayOf(4, 0, 2, 6, 8, 1, 3, 5, 7) // centre -> cornesr -> edges
    private val WIN_SETS = intArrayOf(
        0, 1, 2,  3, 4, 5,  6, 7, 8, // horiz
        0, 3, 6,  1, 4, 7,  2, 5, 8, // vert
        0, 4, 8,  2, 4, 6 // diag
    )
    private const val EMPTY = '\u0000'

    fun onRoomEnter(room: OdonRoom?) {
        if (room?.name == "Tic Tac Toe") reset()
    }

    fun onRenderWorld(ctx: WorldRenderContext, colour: Colour, pColour: Colour, prediction: Boolean) {
        if (Dungeon.currentRoom?.name != "Tic Tac Toe") return
        bestMove?.let {
            ctx.drawWireFrameBox(it.aabb, colour, depth = true)
        }
        if (prediction) predictedMove?.let {
            ctx.drawWireFrameBox(it.aabb, pColour, depth = true)
        }
    }

    fun onTick(player: LocalPlayer, level: ClientLevel, prediction: Boolean, auto: Boolean) {
        val room = Dungeon.currentRoom ?: return
        if (room.name != "Tic Tac Toe") return

        if (auto) bestMove?.let {
            if (player.eyePosition.distanceToSqr(it.vec3) > 30 || System.currentTimeMillis() - lastClick < 500L) return@let
            AuraManager.auraBlock(it)
            lastClick = System.currentTimeMillis()
        }

        val searchBox = AABB.ofSize(Vec3.atCenterOf(room.getRealCoords(BlockPos(8, 71, 16))), 12.0, 12.0, 12.0)
        val frames = level.getEntitiesOfClass(ItemFrame::class.java, searchBox) { it.item.item is MapItem && it.item.has(DataComponents.MAP_ID) }

        val board = CharArray(9) { EMPTY }
        var validFrames = 0

        for (frame in frames) {
            val (_, y, z) = room.getRelativeCoords(frame.blockPosition())

            val row = 72 - y
            val col = 17 - z

            if (row !in 0..2 || col !in 0..2) continue

            val colour = level.getMapData(frame.item.get(DataComponents.MAP_ID) ?: continue)?.colors?.get(8256)?.toInt()?.and(0xFF) ?: continue
            if (colour == 114 || colour == 33) {
                board[row * 3 + col] = if (colour == 114) 'X' else 'O'
                validFrames++
            }
        }

        if (getScore(board) != 0 || validFrames == 9) return reset()

        val boardHash = board.contentHashCode()
        if (boardHash == lastBoardHash) return
        lastBoardHash = boardHash

        if (validFrames % 2 != 0) {
            predictedMove = null
            bestMove = getBestMove(board, true)?.let { indexToPos(it, room) }
        } else if (prediction) {
            bestMove = null
            getBestMove(board, false)?.let { i ->
                board[i] = 'X'
                predictedMove = if (getScore(board) == 0) getBestMove(board, true)?.let { indexToPos(it, room) } else null
            } ?: run { predictedMove = null }
        } else {
            bestMove = null
            predictedMove = null
        }
    }

    private fun indexToPos(i: Int, room: OdonRoom) = room.getRealCoords(BlockPos(8, 72 - (i / 3), 17 - (i % 3)))

    private fun getBestMove(board: CharArray, isPlayer: Boolean): Int? {
        var bestScore = if (isPlayer) -1000 else 1000
        var bestIndex: Int? = null

        for (i in MOVE_ORDER) {
            if (board[i] == EMPTY) {
                board[i] = if (isPlayer) 'O' else 'X'
                val score = alphaBeta(board, 0, -1000, 1000, !isPlayer)
                board[i] = EMPTY

                if (isPlayer && score > bestScore) {
                    bestScore = score
                    bestIndex = i
                } else if (!isPlayer && score < bestScore) {
                    bestScore = score
                    bestIndex = i
                }
            }
        }
        return bestIndex
    }

    private fun alphaBeta(board: CharArray, depth: Int, alpha: Int, beta: Int, maximising: Boolean): Int {
        val score = getScore(board)
        if (score != 0) return score + if (score > 0) -depth else depth
        if (EMPTY !in board) return 0

        var a = alpha
        var b = beta
        var best = if (maximising) -1000 else 1000

        for (i in MOVE_ORDER) {
            if (board[i] == EMPTY) {
                board[i] = if (maximising) 'O' else 'X'
                val s = alphaBeta(board, depth + 1, a, b, !maximising)
                board[i] = EMPTY

                if (maximising) {
                    best = maxOf(best, s)
                    a = maxOf(a, best)
                } else {
                    best = minOf(best, s)
                    b = minOf(b, best)
                }

                if (b <= a) break
            }
        }
        return best
    }

    private fun getScore(board: CharArray): Int {
        for (i in WIN_SETS.indices step 3) {
            val c = board[WIN_SETS[i]]
            if (c != EMPTY && c == board[WIN_SETS[i + 1]] && c == board[WIN_SETS[i + 2]])
                return if (c == 'X') -10 else 10
        }
        return 0
    }

    fun reset() {
        lastBoardHash = 0
        bestMove = null
        predictedMove = null
        lastClick = 0L
    }
}