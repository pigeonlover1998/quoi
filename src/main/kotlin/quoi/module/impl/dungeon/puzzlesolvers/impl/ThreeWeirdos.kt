package quoi.module.impl.dungeon.puzzlesolvers.impl

import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
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
import quoi.utils.SoundUtils
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.render.drawStyledBox
import quoi.utils.skyblock.player.interact.AuraAction
import quoi.utils.skyblock.player.interact.AuraManager
import quoi.utils.vec3
import java.util.concurrent.CopyOnWriteArraySet

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/WeirdosSolver.kt
 */
object ThreeWeirdos : SettingGroup(PuzzleSolvers, "Three weirdos") {
    private val solver by switch("Solver", desc = "Shows the solution for the Weirdos puzzle.")
    private val colour by colourPicker("Correct colour", Colour.MINECRAFT_GREEN.withAlpha(0.7f), true, desc = "Colour for the Weirdos solver.").childOf(::solver)
    private val wrongColour by colourPicker("Wrong colour", Colour.MINECRAFT_RED.withAlpha(0.7f), true,  desc = "Colour for the incorrect Weirdos.").childOf(::solver)
    private val style by selector("Style", "Box", arrayListOf("Box", "Filled box"), desc = "Whether or not the box should be filled.").childOf(::solver)
    private val auto by switch("Auto").asParent()

    private var correctPos: BlockPos? = null
    private var wrongPositions = CopyOnWriteArraySet<BlockPos>()
    private val clickedNPCs = mutableSetOf<Int>()

    private var lastClick = 0L
    private var clickedChest = false

    init {
        on<DungeonEvent.Room.Enter> {
            if (room?.name == "Three Weirdos") reset()
        }

        on<ChatEvent.Packet> {
            if (!solver && !auto) return@on

            weirdosRegex.find(message.noControlCodes)?.destructured?.let { (npc, msg) ->
                val solution = solutions.any { it.matches(msg) }
                val wrong = wrong.any { it.matches(msg) }
                if (!solution && !wrong) return@on

                val room = Dungeon.currentRoom ?: return@on

                val correctNPC = getEntities<ArmorStand>().find { it.name.string == npc } ?: return@on
                val relativePos = room.getRelativeCoords(BlockPos(correctNPC.x.toInt() - 1, 69, correctNPC.z.toInt() - 1))
                val pos = room.getRealCoords(relativePos.offset(1, 0, 0))

                if (solution) {
                    correctPos = pos
                    SoundUtils.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2f, 1f)
                } else wrongPositions.add(pos)
            }
        }

        on<RenderEvent.World> {
            if (!solver) return@on
            correctPos?.let { ctx.drawStyledBox(style.selected, AABB(it), colour) }
            wrongPositions.forEach {
                ctx.drawStyledBox(style.selected, AABB(it), wrongColour)
            }
        }

        on<TickEvent.End> {
            if (!auto || clickedChest || ClearExecutor.active) return@on

            val currentTime = System.currentTimeMillis()

            if (clickedNPCs.size < 3) {
                getEntities<ArmorStand>(10.0).any { entity ->
                    if ("CLICK" !in entity.name.string) return@any false
                    if (entity.id in clickedNPCs || entity.distanceToSqr(player) > 30 || currentTime - lastClick < 200L) return@any false

                    AuraManager.interactEntity(entity, AuraAction.INTERACT_AT)
                    clickedNPCs.add(entity.id)
                    lastClick = currentTime
                    true
                }
                return@on
            }

            val pos = correctPos ?: return@on
            if (wrongPositions.size == 2 &&player.eyePosition.distanceToSqr(pos.vec3) < 30 && currentTime - lastClick >= 150L) {
                AuraManager.interactBlock(pos)
                clickedChest = true
                lastClick = currentTime
            }
        }

        on<WorldEvent.Change> {
            reset()
        }
    }

    override fun shouldHandle(event: Event): Boolean {
        if (!super.shouldHandle(event)) return false

        if (event is DungeonEvent.Room.Enter) return true

        return Dungeon.currentRoom?.name == "Three Weirdos"
    }

    private fun reset() {
        correctPos = null
        wrongPositions.clear()
        clickedNPCs.clear()
        lastClick = 0L
        clickedChest = false
    }

    private val weirdosRegex = Regex("\\[NPC] (.+): (.+).?")

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