package quoi.module.impl.dungeon.puzzlesolvers.impl

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.decoration.ArmorStand
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
import quoi.utils.EntityUtils.getEntities
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.startsWithOneOf
import quoi.utils.aabb
import quoi.utils.render.drawFilledBox
import quoi.utils.skyblock.player.interact.AuraManager
import quoi.utils.vec3

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/QuizSolver.kt
 */
object Quiz : SettingGroup(PuzzleSolvers, "Quiz") {
    private val solver by switch("Solver", desc = "Solver for the trivia puzzle.")
    private val colour by colourPicker("Colour", Colour.MINECRAFT_GREEN.withAlpha(0.75f), true, desc = "Color for the quiz solver.").childOf(::solver)
    private val depth by switch("Depth", desc = "Depth check for the trivia puzzle.").childOf(::solver)
    private val auto by switch("Auto").asParent()

    private var answers: Map<String, List<String>> = PuzzleSolvers.loadSolution("quizAnswers.json", emptyMap())
    private var triviaAnswers: List<String>? = null

    private var lastClick = 0L

    private var triviaOptions = List(3) { TriviaAnswer(null, false) }

    init {

        on<ChatEvent.Packet> {
            if (!solver && !auto) return@on
            val msg = unformatted.trim()
            if (msg.startsWith("[STATUE] Oruo the Omniscient: ") && msg.endsWith("correctly!")) {
                if (msg.contains("answered the final question")) return@on reset()
                if (msg.contains("answered Question #")) triviaOptions.forEach { it.correct = false }
                return@on
            }

            if (msg.startsWithOneOf("ⓐ", "ⓑ", "ⓒ", ignoreCase = true) && triviaAnswers?.any { msg.endsWith(it) } == true) {
                when (msg[0]) {
                    'ⓐ' -> triviaOptions[0].correct = true
                    'ⓑ' -> triviaOptions[1].correct = true
                    'ⓒ' -> triviaOptions[2].correct = true
                }
            }

            triviaAnswers = when {
                msg == "What SkyBlock year is it?" -> listOf("Year ${(((System.currentTimeMillis() / 1000) - 1560276000) / 446400).toInt() + 1}")
                else -> answers.entries.find { msg.contains(it.key) }?.value ?: return@on
            }
        }

        on<DungeonEvent.Room.Enter> {
            if (room?.name != "Quiz") return@on
            triviaOptions[0].pos = room.getRealCoords(BlockPos(20, 70, 6))
            triviaOptions[1].pos = room.getRealCoords(BlockPos(15, 70, 9))
            triviaOptions[2].pos = room.getRealCoords(BlockPos(10, 70, 6))
        }

        on<RenderEvent.World> {
            if (!solver || triviaAnswers == null) return@on
            triviaOptions.forEach { answer ->
                if (!answer.correct) return@forEach
                answer.pos?.below()?.let {
                    ctx.drawFilledBox(it.aabb, colour, depth = depth)
                }
            }
        }

        on<TickEvent.End> {
            if (!auto || ClearExecutor.active) return@on
            if (System.currentTimeMillis() - lastClick < 500L) return@on
            if (getEntities<ArmorStand>(20.0) { it.name.string.contains("ⓒ") }.isEmpty()) return@on

            val answerPos = triviaOptions.firstOrNull { it.correct }?.pos ?: return@on
            if (player.eyePosition.distanceToSqr(answerPos.vec3) > 36) return@on

            AuraManager.interactBlock(answerPos)
            lastClick = System.currentTimeMillis()
        }

        on<WorldEvent.Change> {
            reset()
        }
    }

    override fun shouldHandle(event: Event): Boolean {
        if (!super.shouldHandle(event)) return false

        if (event is DungeonEvent.Room.Enter || event is RenderEvent.World) return true

        return true //Dungeon.currentRoom?.name == "Quiz"
    }

    private fun reset() {
        triviaOptions = List(3) { TriviaAnswer(null, false) }
        triviaAnswers = null
        lastClick = 0L
    }

    private data class TriviaAnswer(var pos: BlockPos?, var correct: Boolean)
}