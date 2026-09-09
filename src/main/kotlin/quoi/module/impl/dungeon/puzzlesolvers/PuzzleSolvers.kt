package quoi.module.impl.dungeon.puzzlesolvers

import com.google.gson.reflect.TypeToken
import quoi.QuoiMod.logger
import quoi.api.skyblock.location.Island
import quoi.api.skyblock.location.invoke
import quoi.config.ConfigSystem.gson
import quoi.module.Module
import quoi.module.impl.dungeon.puzzlesolvers.impl.*
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

@Suppress("unused_expression")
object PuzzleSolvers : Module(
    "Puzzle Solvers",
    desc = "Displays solutions and automatically completes dungeon puzzles: Ice Fill, Teleport Maze, Quiz, Three Weirdos, Tic Tac Toe, Water Board, Creeper Beams, Blaze, and Ice Path.",
    area = Island.Dungeon(inClear = true)
) {

    init {
        IceFillSolver
        TeleportMazeSolver
        QuizSolver
        ThreeWeirdosSolver
        TicTacToeSolver
        WaterBoardSolver
        CreeperBeamsSolver
        BlazeSolver
        IcePathSolver
    }

    private val bowDropdown by text("Bow settings").visibleIf { CreeperBeamsSolver.auto || BlazeSolver.auto || IcePathSolver.auto }
    val shootCd by slider("Shoot cooldown", 500L, 250L, 1000L, 50L, unit = "ms").childOf(::bowDropdown)
    val missCd by slider("Miss cooldown", 550L, 300L, 1050L, 50L, unit = "ms").childOf(::bowDropdown)

    inline fun <reified T> loadSolution(file: String, fallback: T): T {
        val path = "/assets/quoi/puzzles/$file"
        return try {
            val isr = PuzzleSolvers::class.java.getResourceAsStream(path)?.let {
                InputStreamReader(
                    it,
                    StandardCharsets.UTF_8
                )
            }
            if (isr != null) {
                val text = isr.readText()
                isr.close()
                gson.fromJson(text, object : TypeToken<T>() {}.type) ?: fallback
            } else fallback
        } catch (e: Exception) {
            logger.error("Error loading $path", e)
            fallback
        }
    }
}

