package quoi.module.impl.dungeon.puzzlesolvers

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.decoration.ArmorStand
import quoi.QuoiMod.logger
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.utils.EntityUtils
import quoi.utils.StringUtils.startsWithOneOf
import quoi.utils.aabb
import quoi.utils.render.drawFilledBox
import quoi.utils.skyblock.player.AuraManager
import quoi.utils.vec3
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/QuizSolver.kt
 */
object QuizSolver {
    private var answers: MutableMap<String, List<String>>
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val isr = this::class.java.getResourceAsStream("/assets/quoi/puzzles/quizAnswers.json")?.let { InputStreamReader(it, StandardCharsets.UTF_8) }
    private var triviaAnswers: List<String>? = null

    private var lastClick = 0L

    private var triviaOptions: MutableList<TriviaAnswer> = MutableList(3) { TriviaAnswer(null, false) }
    private data class TriviaAnswer(var blockPos: BlockPos?, var isCorrect: Boolean)

    init {
        try {
            val text = isr?.readText()
            answers = gson.fromJson(text, object : TypeToken<MutableMap<String, List<String>>>() {}.type)
            isr?.close()
        } catch (e: Exception) {
            logger.error("Error loading quiz answers", e)
            answers = mutableMapOf()
        }
    }

    fun onMessage(msg: String) {
        if (msg.startsWith("[STATUE] Oruo the Omniscient: ") && msg.endsWith("correctly!")) {
            if (msg.contains("answered the final question")) {
//                onPuzzleComplete("Quiz")
                reset()
                return
            }
            if (msg.contains("answered Question #")) triviaOptions.forEach { it.isCorrect = false }
        }

        if (msg.trim().startsWithOneOf("ⓐ", "ⓑ", "ⓒ", ignoreCase = true) && triviaAnswers?.any { msg.endsWith(it) } == true) {
            when (msg.trim()[0]) {
                'ⓐ' -> triviaOptions[0].isCorrect = true
                'ⓑ' -> triviaOptions[1].isCorrect = true
                'ⓒ' -> triviaOptions[2].isCorrect = true
            }
        }

        triviaAnswers = when {
            msg.trim() == "What SkyBlock year is it?" -> listOf("Year ${(((System.currentTimeMillis() / 1000) - 1560276000) / 446400).toInt() + 1}")
            else -> answers.entries.find { msg.contains(it.key) }?.value ?: return
        }
    }

    fun onRoomEnter(room: OdonRoom?) = with(room) {
        if (this?.name != "Quiz") return@with

        triviaOptions[0].blockPos = getRealCoords(BlockPos(20, 70, 6))
        triviaOptions[1].blockPos = getRealCoords(BlockPos(15, 70, 9))
        triviaOptions[2].blockPos = getRealCoords(BlockPos(10, 70, 6))
    }

    fun onRenderWorld(ctx: WorldRenderContext, colour: Colour, depth: Boolean) {
        if (triviaAnswers == null || triviaOptions.isEmpty()) return
        triviaOptions.forEach { answer ->
            if (!answer.isCorrect) return@forEach
            answer.blockPos?.offset(0, -1, 0)?.let {
                ctx.drawFilledBox(it.aabb, colour, depth = depth)
//                ctx.drawBeaconBeam(it, colour)
            }
        }
    }

    fun onTick(player: LocalPlayer) {
        if (Dungeon.currentRoom?.name != "Quiz") return
        if (System.currentTimeMillis() - lastClick < 500L) return
        if (EntityUtils.entities.none { it is ArmorStand && it.name.string.contains("ⓒ") }) return

        val answerPos = triviaOptions.firstOrNull { it.isCorrect }?.blockPos ?: return
        if (player.eyePosition.distanceToSqr(answerPos.vec3) > 30) return

        AuraManager.auraBlock(answerPos)
        lastClick = System.currentTimeMillis()
    }

    fun reset() {
        triviaOptions = MutableList(3) { TriviaAnswer(null, false) }
        triviaAnswers = null
        lastClick = 0L
    }
}