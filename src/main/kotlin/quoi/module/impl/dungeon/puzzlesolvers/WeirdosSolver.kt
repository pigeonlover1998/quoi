package quoi.module.impl.dungeon.puzzlesolvers

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.utils.ChatUtils.modMessage
import quoi.utils.EntityUtils.getEntities
import quoi.utils.SoundUtils
import quoi.utils.render.drawStyledBox
import quoi.utils.skyblock.player.AuraAction
import quoi.utils.skyblock.player.AuraManager
import quoi.utils.vec3
import java.util.concurrent.CopyOnWriteArraySet

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/WeirdosSolver.kt
 */
object WeirdosSolver {
    private var correctPos: BlockPos? = null
    private var wrongPositions = CopyOnWriteArraySet<BlockPos>()
    private var clickedNPCs = mutableSetOf<Int>()

    private var lastClick = 0L
    private var clickedChest = false

    fun onRoomEnter(room: OdonRoom?) {
        if (room?.name == "Three Weirdos") reset()
    }

    fun onMessage(npc: String, msg: String) {
        if (solutions.none { it.matches(msg) } && wrong.none { it.matches(msg) }) return
        val correctNPC = mc.level?.entitiesForRendering()?.find { it is ArmorStand && it.name.string == npc } ?: return
        val room = Dungeon.currentRoom ?: return
        val relativePos = room.getRelativeCoords(BlockPos(correctNPC.x.toInt() - 1, 69, correctNPC.z.toInt() - 1))
        val pos = room.getRealCoords(relativePos.offset(1, 0, 0))

        if (solutions.any { it.matches(msg) }) {
            correctPos = pos
            SoundUtils.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2f, 1f)
        } else wrongPositions.add(pos)
    }

    fun onRenderWorld(ctx: WorldRenderContext, colour: Colour, wrongColour: Colour, style: String) {
        if (Dungeon.currentRoom?.name != "Three Weirdos") return
        correctPos?.let { ctx.drawStyledBox(style, AABB(it), colour) }
        wrongPositions.forEach {
            ctx.drawStyledBox(style, AABB(it), wrongColour)
        }
    }

    fun onTick(player: LocalPlayer) {
        if (Dungeon.currentRoom?.name != "Three Weirdos" || clickedChest) return

        val currentTime = System.currentTimeMillis()

        if (clickedNPCs.size < 3) {
            getEntities<ArmorStand>(10.0).any { entity ->
                if (!entity.name.string.contains("CLICK")) return@any false
                if (entity.id in clickedNPCs || entity.distanceToSqr(player) > 30 || currentTime - lastClick < 200L) return@any false
                modMessage(entity.name)

                AuraManager.auraEntity(entity, AuraAction.INTERACT_AT)
                clickedNPCs.add(entity.id)
                lastClick = currentTime
                true
            }
            return
        }

        val pos = correctPos ?: return
        if (wrongPositions.size == 2 &&player.eyePosition.distanceToSqr(pos.vec3) < 30 && currentTime - lastClick >= 150L) {
            AuraManager.interactBlock(pos)
            clickedChest = true
            lastClick = currentTime
        }
    }

    fun reset() {
        correctPos = null
        wrongPositions.clear()
        clickedNPCs.clear()
        lastClick = 0L
        clickedChest = false
    }

    private val solutions = listOf(
        Regex("The reward is not in my chest!"),
        Regex("At least one of them is lying, and the reward is not in .+'s chest.?"),
        Regex("My chest doesn't have the reward. We are all telling the truth.?"),
        Regex("My chest has the reward and I'm telling the truth!"),
        Regex("The reward isn't in any of our chests.?"),
        Regex("Both of them are telling the truth. Also, .+ has the reward in their chest.?"),
    )

    private val wrong = listOf(
        Regex("One of us is telling the truth!"),
        Regex("They are both telling the truth. The reward isn't in .+'s chest."),
        Regex("We are all telling the truth!"),
        Regex(".+ is telling the truth and the reward is in his chest."),
        Regex("My chest doesn't have the reward. At least one of the others is telling the truth!"),
        Regex("One of the others is lying."),
        Regex("They are both telling the truth, the reward is in .+'s chest."),
        Regex("They are both lying, the reward is in my chest!"),
        Regex("The reward is in my chest."),
        Regex("The reward is not in my chest. They are both lying."),
        Regex(".+ is telling the truth."),
        Regex("My chest has the reward.")
    )
}