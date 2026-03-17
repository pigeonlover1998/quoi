package quoi.module.impl.dungeon.puzzlesolvers

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.logger
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.utils.*
import quoi.utils.ChatUtils.modMessage
import quoi.utils.WorldUtils.state
import quoi.utils.render.drawLine
import quoi.utils.skyblock.ItemUtils.skyblockId
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.PlayerUtils.isMoving
import quoi.utils.skyblock.player.PlayerUtils.stop
import quoi.utils.skyblock.player.PlayerUtils.useItem
import quoi.utils.skyblock.player.SwapManager
import java.io.InputStreamReader
import java.lang.reflect.Type
import java.nio.charset.StandardCharsets
import kotlin.math.abs

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/IceFillSolver.kt
 */
object IceFillSolver {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(BlockPos::class.java, BlockPosDeserializer())
        .create()
    private val isr = this::class.java.getResourceAsStream("/assets/quoi/puzzles/iceFillFloors.json")
        ?.let { InputStreamReader(it, StandardCharsets.UTF_8) }
    private var iceFillFloors = IceFillData(emptyList(), emptyList(), emptyList())
    private var currentPatterns: ArrayList<Vec3> = ArrayList()

    private var repositionTicker: Ticker? = null

    init {
        try {
            val text = isr?.readText()
            iceFillFloors = gson.fromJson(text, IceFillData::class.java)
            isr?.close()
        } catch (e: Exception) {
            logger.error("Error loading ice fill floors", e)
        }
    }

    fun onRenderWorld(ctx: WorldRenderContext, colour: Colour) {
        if (!currentPatterns.isEmpty() && Dungeon.currentRoom?.name == "Ice Fill")
            ctx.drawLine(currentPatterns, colour, true)
    }

    fun onRoomEnter(room: OdonRoom?) = with (room) {
        if (this?.name != "Ice Fill" || currentPatterns.isNotEmpty()) return@with
        val patterns = /*if (optimizePatterns) iceFillFloors.hard else */iceFillFloors.easy

        repeat(3) { index ->
            val floorIdentifiers = iceFillFloors.identifier[index]

            for (patternIndex in floorIdentifiers.indices) {
                if (isRealAir(floorIdentifiers[patternIndex][0]) && !isRealAir(floorIdentifiers[patternIndex][1])) {
                    currentPatterns.addAll(patterns[index][patternIndex].map { Vec3(getRealCoords(it)).add(0.5, 0.1, 0.5) })
                    return@repeat
                }
            }
            modMessage("§cFailed to scan floor $index")
        }

        if (currentPatterns.isNotEmpty()) {
            stupidStairs()
            fillGaps()
        }
    }

    private var ticks = 0
    private var lastIndex = -1

    fun onTick(player: LocalPlayer, delay: Int, reposition: Boolean) {
        if (mc.screen != null) return
        if (currentPatterns.isEmpty() || Dungeon.currentRoom?.name != "Ice Fill") return
        if (Dungeon.currentRoom?.getRealCoords(BlockPos(15, 71, 26))?.state?.block == Blocks.PACKED_ICE) return

        repositionTicker?.let {
            if (it.tick()) repositionTicker = null
            return
        }

        if (reposition && player.y !in (69.5..72.5)) { // untested
            player.stop()
            if (player.isMoving) return
            val spot = getBlock()
            if (spot != null) {
                reposition(player, spot)
                return
            }
        }
        if (player.mainHandItem.skyblockId?.equalsOneOf("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END") == false) return

        val index = currentPatterns.indexOfFirst {
            player.x == it.x && (player.y + 0.1) == it.y && player.z == it.z
        }

        if (index == -1 || index >= currentPatterns.size - 1) {
            lastIndex = -1
            return
        }

        if (lastIndex == -1 || index > lastIndex) {
            lastIndex = index
            ticks = 0
        }

        if (lastIndex >= currentPatterns.size - 1) return

        if (++ticks == delay) {
            val current = currentPatterns[lastIndex]
            val next = currentPatterns[lastIndex + 1]

            val from = Vec3(current.x, current.y - 0.1 + player.eyeHeight, current.z)

            val dir = getDirection(from, next)
            player.useItem(dir)

            lastIndex++
            ticks = 0
        }
    }

    private fun getBlock(): BlockPos? {
        currentPatterns.forEach { vec ->
            val pos = vec.blockPos.above(-1)

            if (pos.state.block == Blocks.ICE) {
                return pos
            }
        }
        return null
    }

    private fun reposition(player: LocalPlayer, spot: BlockPos) {
        if (repositionTicker != null) return

        val dir = getEtherwarpDirection(spot)

        if (dir == null) {
            repositionTicker = null
            return
        }

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
            action { player.useItem(dir) }
            await { player.at(spot) }
            action {
                mc.options.keyShift.isDown = false
            }
            delay(2)
        }
    }


    private fun fillGaps() {
        val updated = ArrayList<Vec3>(currentPatterns.size * 2)

        for (i in 0 until currentPatterns.size - 1) {
            val p1 = currentPatterns[i]; val p2 = currentPatterns[i + 1]
            updated.add(p1)

            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            val dz = p2.z - p1.z

            val steps = maxOf(abs(dx), abs(dy), abs(dz)).toInt()

            for (s in 1 until steps) {
                val r = s.toDouble() / steps
                updated.add(Vec3(p1.x + dx * r, p1.y + dy * r, p1.z + dz * r))
            }
        }
        updated.add(currentPatterns.last())
        currentPatterns = updated
    }

    private fun stupidStairs() {
        val updatedPatterns = ArrayList<Vec3>(currentPatterns.size + 2)
        var lastPoint: Vec3 = currentPatterns[0]
        var added71 = false
        var added72 = false

        for (point in currentPatterns) {
            val y = point.y
            if (!added71 && y == 71.1) {
                updatedPatterns.add(Vec3((lastPoint.x + point.x) / 2, 71.1, (lastPoint.z + point.z) / 2))
                added71 = true
            } else if (!added72 && y == 72.1) {
                updatedPatterns.add(Vec3((lastPoint.x + point.x) / 2, 72.1, (lastPoint.z + point.z) / 2))
                added72 = true
            }
            updatedPatterns.add(point)
            lastPoint = point
        }
        currentPatterns = updatedPatterns
    }
    private fun OdonRoom.isRealAir(pos: BlockPos): Boolean = getRealCoords(pos).state.isAir

    fun reset() {
        currentPatterns.clear()
        ticks = 0
        lastIndex = -1

        repositionTicker = null
    }

    private data class IceFillData(
        val identifier: List<List<List<BlockPos>>>,
        val easy: List<List<List<BlockPos>>>,
        val hard: List<List<List<BlockPos>>>
    )

    private class BlockPosDeserializer : JsonDeserializer<BlockPos> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): BlockPos {
            val obj = json.asJsonObject
            val x = obj.get("x").asInt
            val y = obj.get("y").asInt
            val z = obj.get("z").asInt
            return BlockPos(x, y, z)
        }
    }
}