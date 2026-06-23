package quoi.module.impl.dungeon.puzzlesolvers.impl

import com.google.gson.JsonObject
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.events.*
import quoi.api.events.core.Event
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
import quoi.module.impl.dungeon.puzzlesolvers.PuzzleSolvers
import quoi.module.impl.dungeon.puzzlesolvers.Repositionable
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.SettingGroup
import quoi.utils.ChatUtils
import quoi.utils.EntityUtils.renderPos
import quoi.utils.Scheduler
import quoi.utils.Scheduler.scheduleLoop
import quoi.utils.StringUtils.toFixed
import quoi.utils.Ticker
import quoi.utils.WorldUtils.state
import quoi.utils.render.drawLine
import quoi.utils.render.drawText
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.interact.AuraManager

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/WaterSolver.kt
 */
object WaterBoard : SettingGroup(PuzzleSolvers, "Water board"), Repositionable {

    private val solver by switch("Solver", desc = "Shows the solution to the water board puzzle.")
    private val tracer by switch("Tracer", true, desc = "Shows a tracer to the next lever.").childOf(::solver)
    private val tracerFirst by colourPicker("First", Colour.MINECRAFT_GREEN, true, desc = "Colour for the first tracer.").childOf(::tracer)
    private val tracerSecond by colourPicker("Second", Colour.MINECRAFT_GOLD, true, desc = "Colour for the second tracer.").childOf(::tracer)
    private val optimised by switch("Optimised solutions", desc = "Uses optimised solutions for the water board puzzle.").asParent()
    private val auto by switch("Auto").asParent()

    private var waterSolutions: JsonObject = PuzzleSolvers.loadSolution("waterSolutions.json", JsonObject())

    init {
        scheduleLoop(10) {
            if (!module.running) return@scheduleLoop
            if (solver || auto) scan(optimised)
        }

        on<DungeonEvent.Room.Enter> {
            if (room?.name == "Water Board") reset()
        }

        on<RenderEvent.World> {
            if (!solver) return@on
            if (patternIdentifier == -1 || solutions.isEmpty()) return@on

            val solutionList = solutions
                .flatMap { (lever, times) -> times.drop(lever.i).map { Pair(lever, it) } }
                .sortedBy { (lever, time) -> time + if (lever == LeverBlock.WATER) 0.01 else 0.0 }

            if (tracer) {
                val firstSolution = solutionList.firstOrNull()?.first ?: return@on
                ctx.drawLine(listOf(player.renderPos, Vec3(firstSolution.leverPos).add(.5, .5, .5)), colour = tracerFirst, depth = true)

                if (solutionList.size > 1 && firstSolution.leverPos != solutionList[1].first.leverPos) {
                    ctx.drawLine(
                        listOf(Vec3(firstSolution.leverPos).add(.5, .5, .5), Vec3(solutionList[1].first.leverPos).add(.5, .5, .5)),
                        colour = tracerSecond, depth = true
                    )
                }
            }

            solutions.forEach { (lever, times) ->
                times.drop(lever.i).forEachIndexed { index, time ->
                    val timeInTicks = (time * 20).toInt()
                    val str = when (openedWaterTicks) {
                        -1 if timeInTicks == 0 -> "§a§lCLICK ME!"
                        -1 -> "§e${time}s"
                        else -> (openedWaterTicks + timeInTicks - tickCounter).takeIf { it > 0 }?.let { "§e${(it / 20f).toFixed()}s" } ?: "§a§lCLICK ME!"
                    }
                    ctx.drawText(ChatUtils.literal(str), Vec3(lever.leverPos).add(0.5, (index + lever.i) * 0.5 + 1.5, 0.5), scale = 1f, depth = true)
                }
            }
        }

        on<PacketEvent.Sent, ServerboundUseItemOnPacket> {
            if (!solver && !auto) return@on
            if (packet.hand == InteractionHand.OFF_HAND) return@on
            if (solutions.isEmpty()) return@on
            LeverBlock.entries.find { it.leverPos == packet.hitResult.blockPos }?.let {
                if (it == LeverBlock.WATER && openedWaterTicks == -1) openedWaterTicks = tickCounter
                it.i++
            }
        }

        on<TickEvent.Server> {
            if (solver || auto) tickCounter++
        }

        on<TickEvent.End> {
            if (!auto) return@on
            if (ClearExecutor.active) return@on
            val room = Dungeon.currentRoom ?: return@on
            if (patternIdentifier == -1 || solutions.isEmpty()) return@on
            if (player.y != 59.0 || mc.screen != null || atChest) return@on

            repositionTicker?.let {
                if (it.tick()) Scheduler.scheduleTask {
                    repositionTicker = null
                }
                return@on
            }

            val solutionList = solutions
                .flatMap { (lever, times) -> times.drop(lever.i).map { lever to it } }
                .sortedBy { (lever, time) -> time + if (lever == LeverBlock.WATER) 0.01 else 0.0 }

            val first = solutionList.firstOrNull() ?: return@on chest(player, room)

            val (lever, time) = first

            val z = when (lever.relativePosition.z) {
                20, 15 -> lever.relativePosition.z
                10, 5 -> 9
                else -> return@on
            }

            val spot = room.getRealCoords(BlockPos(15, 58, z))

            if (!player.at(spot)) {
                if (System.currentTimeMillis() - lastClick < 200) return@on
                reposition(spot, bow = false)
                return@on
            }

            val remaining = openedWaterTicks - tickCounter + time * 20
            val water = lever == LeverBlock.WATER

            if ((water && (openedWaterTicks == -1 || remaining <= 0)) || (!water && remaining <= 0)) {
                AuraManager.interactBlock(lever.leverPos)
                lastClick = System.currentTimeMillis()
            }
        }

        on<WorldEvent.Change> {
            reset()
        }
    }

    override fun shouldHandle(event: Event): Boolean {
        if (!super.shouldHandle(event)) return false

        if (event is DungeonEvent.Room.Enter) return true

        return Dungeon.currentRoom?.name == "Water Board"
    }

    private var solutions = HashMap<LeverBlock, Array<Double>>()
    private var patternIdentifier = -1
    private var openedWaterTicks = -1
    private var tickCounter = 0

    override var repositionTicker: Ticker? = null
    private var lastClick = 0L
    private var atChest = false

    private fun scan(optimized: Boolean) = with (Dungeon.currentRoom) {
        if (this?.name != "Water Board" || patternIdentifier != -1) return@with
        val extendedSlots = WoolColour.entries.joinToString("") { if (it.isExtended) it.ordinal.toString() else "" }.takeIf { it.length == 3 } ?: return@with

        patternIdentifier = when {
            getRealCoords(BlockPos(14, 77, 27)).state.block == Blocks.TERRACOTTA -> 0 // right block == clay
            getRealCoords(BlockPos(16, 78, 27)).state.block == Blocks.EMERALD_BLOCK -> 1 // left block == emerald
            getRealCoords(BlockPos(14, 78, 27)).state.block == Blocks.DIAMOND_BLOCK -> 2 // right block == diamond
            getRealCoords(BlockPos(14, 78, 27)).state.block == Blocks.QUARTZ_BLOCK  -> 3 // right block == quartz
            else -> return@with ChatUtils.modMessage("§cFailed to get Water Board pattern. Was the puzzle already started?")
        }

        ChatUtils.modMessage(
            "$patternIdentifier || ${
                WoolColour.entries.filter { it.isExtended }.joinToString(", ") { it.name.lowercase() }
            }"
        )

        solutions.clear()
        waterSolutions[optimized.toString()].asJsonObject[patternIdentifier.toString()].asJsonObject[extendedSlots].asJsonObject.entrySet().forEach { entry ->
            solutions[
                when (entry.key) {
                    "diamond_block" -> LeverBlock.DIAMOND
                    "emerald_block" -> LeverBlock.EMERALD
                    "hardened_clay" -> LeverBlock.CLAY
                    "quartz_block"  -> LeverBlock.QUARTZ
                    "gold_block"    -> LeverBlock.GOLD
                    "coal_block"    -> LeverBlock.COAL
                    "water"         -> LeverBlock.WATER
                    else -> LeverBlock.NONE
                }
            ] = entry.value.asJsonArray.map { it.asDouble }.toTypedArray()
        }
    }

    private fun reset() {
        LeverBlock.entries.forEach { it.i = 0 }
        patternIdentifier = -1
        solutions.clear()
        openedWaterTicks = -1
        tickCounter = 0

        repositionTicker = null
        lastClick = 0L
        atChest = false
    }

    private fun chest(player: LocalPlayer, room: OdonRoom) {
        val spot = room.getRealCoords(BlockPos(15, 58, 22))
        if (!player.at(spot)) return reposition(spot, bow = false)
        mc.options.keyShift.isDown = false
        atChest = true
    }

    private enum class WoolColour(val relativePosition: BlockPos) {
        PURPLE(BlockPos(15, 56, 19)),
        ORANGE(BlockPos(15, 56, 18)),
        BLUE(BlockPos(15, 56, 17)),
        GREEN(BlockPos(15, 56, 16)),
        RED(BlockPos(15, 56, 15));

        inline val isExtended: Boolean get() =
            Dungeon.currentRoom?.getRealCoords(relativePosition)?.state?.isAir == false
    }

    private enum class LeverBlock(val relativePosition: BlockPos, var i: Int = 0) {
        QUARTZ(BlockPos(20, 61, 20)),
        GOLD(BlockPos(20, 61, 15)),
        COAL(BlockPos(20, 61, 10)),
        DIAMOND(BlockPos(10, 61, 20)),
        EMERALD(BlockPos(10, 61, 15)),
        CLAY(BlockPos(10, 61, 10)),
        WATER(BlockPos(15, 60, 5)),
        NONE(BlockPos(0, 0, 0));

        inline val leverPos: BlockPos
            get() = Dungeon.currentRoom?.getRealCoords(relativePosition) ?: BlockPos(0, 0, 0)
    }
}