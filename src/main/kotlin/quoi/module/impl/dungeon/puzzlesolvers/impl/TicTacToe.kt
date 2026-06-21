package quoi.module.impl.dungeon.puzzlesolvers.impl

import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.MapItem
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
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
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.SettingGroup
import quoi.utils.*
import quoi.utils.render.drawWireFrameBox
import quoi.utils.skyblock.player.interact.AuraManager

/**
 * modified Skyblocker (LGPL-3.0) (c) kevinthegreat1
 * original:
 *          https://github.com/SkyblockerMod/Skyblocker/blob/master/src/main/java/de/hysky/skyblocker/skyblock/dungeon/puzzle/TicTacToe.java
 *          https://github.com/SkyblockerMod/Skyblocker/blob/master/src/main/java/de/hysky/skyblocker/utils/tictactoe/TicTacToeUtils.java
 */
object TicTacToe : SettingGroup(PuzzleSolvers, "Tic tac toe") {

    private val solver by switch("Solver", desc = "Shows the solution for the Tic tac toe puzzle.")
    private val colour by colourPicker("Colour", Colour.MINECRAFT_GREEN.withAlpha(0.7f), true, desc = "Colour for the tic tac toe solver").childOf(::solver)
    private val prediction by switch("Prediction", desc = "try and see").childOf(::solver)
    private val pColour by colourPicker("Prediction colour", Colour.MINECRAFT_YELLOW.withAlpha(0.7f), true).childOf(::prediction)
    private val auto by switch("Auto").asParent()

    private var lastBoardHash = 0
    private var bestMove: BlockPos? = null
    private var predictedMove: BlockPos? = null

    private var lastClick = 0L

    init {
        on<DungeonEvent.Room.Enter> {
            if (room?.name == "Tic Tac Toe") reset()
        }

        on<RenderEvent.World> {
            if (!solver) return@on
            bestMove?.let {
                ctx.drawWireFrameBox(it.aabb, colour, depth = true)
            }
            if (prediction) predictedMove?.let {
                ctx.drawWireFrameBox(it.aabb, pColour, depth = true)
            }
        }

        on<TickEvent.End> {
            if (!solver && !auto) return@on
            if (ClearExecutor.active) return@on
            val room = Dungeon.currentRoom ?: return@on

            if (auto) bestMove?.let {
                if (player.eyePosition.distanceToSqr(it.vec3) > 30 || System.currentTimeMillis() - lastClick < 500L) return@let
                AuraManager.interactBlock(it)
                lastClick = System.currentTimeMillis()
            }

            val searchBox = AABB.ofSize(Vec3.atCenterOf(room.getRealCoords(BlockPos(8, 71, 16))), 12.0, 12.0, 12.0)
            val frames = EntityUtils.getEntities<ItemFrame>(searchBox) { it.item.item is MapItem && it.item.has(
                DataComponents.MAP_ID) }

            val board = CharArray(9) { EMPTY }
            var validFrames = 0

            for (frame in frames) {
                val (_, y, z) = room.getRelativeCoords(frame.blockPosition())

                val row = 72 - y
                val col = 17 - z

                if (row !in 0..2 || col !in 0..2) continue

                val mapId = frame.item.get(DataComponents.MAP_ID) ?: continue
                val mapData = level.getMapData(mapId) ?: continue

                val colour = mapData.colors.getOrNull(8256)?.toInt()?.and(0xFF) ?: continue
                if (colour == 114 || colour == 33) {
                    board[row * 3 + col] = if (colour == 114) 'X' else 'O'
                    validFrames++
                }
            }

            if (getScore(board) != 0 || validFrames == 9) return@on reset()

            val boardHash = board.contentHashCode()
            if (boardHash == lastBoardHash) return@on
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

        on<WorldEvent.Change> {
            reset()
        }
    }

    override fun shouldHandle(event: Event): Boolean {
        if (!super.shouldHandle(event)) return false

        if (event is DungeonEvent.Room.Enter) return true

        return Dungeon.currentRoom?.name == "Tic Tac Toe"
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

    private fun reset() {
        lastBoardHash = 0
        bestMove = null
        predictedMove = null
        lastClick = 0L
    }

    private val MOVE_ORDER = intArrayOf(4, 0, 2, 6, 8, 1, 3, 5, 7) // centre -> cornesr -> edges
    private val WIN_SETS = intArrayOf(
        0, 1, 2,  3, 4, 5,  6, 7, 8, // horiz
        0, 3, 6,  1, 4, 7,  2, 5, 8, // vert
        0, 4, 8,  2, 4, 6 // diag
    )
    private const val EMPTY = '\u0000'
}