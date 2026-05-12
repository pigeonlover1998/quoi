package quoi.module.impl.dungeon.puzzlesolvers

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.logger
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.BlockEvent
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.utils.*
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.WorldUtils.state
import quoi.utils.render.drawLine
import quoi.utils.render.drawStyledBox
import quoi.utils.skyblock.item.TeleportUtils.getEtherwarpDirection
import quoi.utils.skyblock.item.ItemUtils.isShortbow
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.PlayerUtils.useItem
import quoi.utils.skyblock.player.SwapManager
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/BeamsSolver.kt
 */
object BeamsSolver {
    private var scanned = false
    private var lanternPairs: List<List<Int>>
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val isr = this::class.java.getResourceAsStream("/assets/quoi/puzzles/creeperBeamsSolutions.json")?.let { InputStreamReader(it, StandardCharsets.UTF_8) }

    private var activePair: LanternPair? = null
    private var lastShotTime = 0L
    private var waitingForUpdate = false
    private var repositionTicker: Ticker? = null
    private var solvedPairs = 0

    init {
        try {
            val text = isr?.readText()
            lanternPairs = gson.fromJson(text, object : TypeToken<List<List<Int>>>() {}.type)
            isr?.close()
        } catch (e: Exception) {
            logger.error("Error loading creeper beams solutions", e)
            lanternPairs = emptyList()
        }
    }

    private var currentLanternPairs = ConcurrentHashMap<BlockPos, Pair<BlockPos, Colour>>()

    fun onRoomEnter(room: OdonRoom?) = with(room) {
        if (this?.name != "Creeper Beams") return@with reset()
        recalculateLanternPairs(this)
    }

    private fun recalculateLanternPairs(room: OdonRoom) {
        currentLanternPairs.clear()
        lanternPairs.forEachIndexed { index, list ->
            val pos = room.getRealCoords(BlockPos(list[0], list[1], list[2]))?.takeIf { mc.level?.getBlockState(it)?.block == Blocks.SEA_LANTERN } ?: return@forEachIndexed
            val pos2 = room.getRealCoords(BlockPos(list[3], list[4], list[5]))?.takeIf { mc.level?.getBlockState(it)?.block == Blocks.SEA_LANTERN } ?: return@forEachIndexed

            currentLanternPairs[pos] = pos2 to colours[index % colours.size]
        }

        val active = activePair ?: return
        if (!currentLanternPairs.containsKey(active.first)) {
            activePair = null
            waitingForUpdate = false
        }
    }

    fun onRenderWorld(ctx: WorldRenderContext, style: String, beamsTracer: Boolean, beamsAlpha: Float) {
        if (Dungeon.currentRoom?.name != "Creeper Beams" || currentLanternPairs.isEmpty()) return

        currentLanternPairs.entries.forEach { positions ->
            val colour = positions.value.second.withAlpha(beamsAlpha)

            ctx.drawStyledBox(style, AABB(positions.key), colour, depth = true)
            ctx.drawStyledBox(style, AABB(positions.value.first), colour, depth = true)

            if (beamsTracer)
                ctx.drawLine(listOf(positions.key.center, positions.value.first.center), colour = colour, depth = false)

            activePair?.let {
                val target = if (it.stage == 0) it.first else it.second
                ctx.drawStyledBox("Box", AABB(target), Colour.RED, depth = false)
            }
        }
    }

    fun onBlockChange(event: BlockEvent.Update, announce: Boolean, auto: Boolean) {
        if (Dungeon.currentRoom?.name != "Creeper Beams") return
        if (
            event.old.block.equalsOneOf(Blocks.PRISMARINE, Blocks.SEA_LANTERN) &&
            event.updated.block.equalsOneOf(Blocks.PRISMARINE, Blocks.SEA_LANTERN)
        ) {
            mc.execute {
                val room = Dungeon.currentRoom ?: return@execute
                val prev = currentLanternPairs.size

                recalculateLanternPairs(room)

                if (currentLanternPairs.size < prev && ++solvedPairs == 4) {
                    if (announce) ChatUtils.command("pc Beams done!")
                    if (auto) mc.options.keyShift.isDown = false
                }
            }

            return
        }
    }

    fun onSound(packet: ClientboundSoundPacket) {
        if (Dungeon.currentRoom?.name != "Creeper Beams" || activePair == null) return
        if (packet.sound.registeredName != "minecraft:entity.elder_guardian.hurt") return

        val pair = activePair ?: return

        val pos = if (pair.stage == 0) pair.first else pair.second
        if (!Vec3(pos).equal(Vec3(packet.x, packet.y, packet.z))) return

        if (pair.stage == 0 && packet.pitch == 1.3968254f) {
            pair.stage = 1
            waitingForUpdate = false
        } else if (pair.stage == 1 && packet.pitch == 2.0f) {
            pair.stage = 2
            waitingForUpdate = false
            currentLanternPairs.remove(pair.first)
            activePair = null
        }

    }

    fun onTick(player: LocalPlayer, shootCd: Long, missCd: Long) {
        val room = Dungeon.currentRoom ?: return
        if (room.name != "Creeper Beams" || currentLanternPairs.isEmpty()) return
        if (mc.screen != null) return
        if (solvedPairs >= 4) return

        val start = room.getRealCoords(BlockPos(16, 74, 14))
        if (start.state.isAir) return

        repositionTicker?.let {
            if (it.tick()) scheduleTask {
                repositionTicker = null
            }
            return
        }

        if (player.y != 75.0) {
            reposition(player, start)
            return
        }

        if (activePair == null) {
            val entry = currentLanternPairs.entries.firstOrNull() ?: return
            activePair = LanternPair(entry.key, entry.value.first)
        }
        val pair = activePair ?: return
        val lantern = if (pair.stage == 0) pair.first else pair.second
        val lanternVec = Vec3.atCenterOf(lantern)

        val creeper = room.getRealCoords(BlockPos(15, 74, 15))

        if (isPathBlocked(player.eyePosition, lanternVec, creeper)) {
            val spot = getPositionSpot(room, lanternVec, creeper)
            if (spot != null) {
                reposition(player, spot)
                return
            }
        }

        val currentTime = System.currentTimeMillis()

        if (waitingForUpdate) {
            if (currentTime - lastShotTime > missCd)
                waitingForUpdate = false
            else
                return
        }

        if (!player.mainHandItem.isShortbow) return
        if (currentTime - lastShotTime < shootCd) return

//        val dir = getArrowDirection(lantern) ?: return
        val dir = getEtherwarpDirection(lantern) ?:
        getDirection(lanternVec)
        player.useItem(dir)

        lastShotTime = currentTime
        waitingForUpdate = true
    }

    fun reset() {
        scanned = false
        currentLanternPairs.clear()
        activePair = null
        waitingForUpdate = false
        lastShotTime = -1
        repositionTicker = null
        solvedPairs = 0
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
            action {
                if (!r) cancel()
                val dir = getEtherwarpDirection(spot) ?: cancel()
                player.useItem(dir)
            }
            await { player.at(spot) }
            action {
                SwapManager.swapByLore("Shortbow: Instantly shoots!")
            }
        }
    }

    private fun isPathBlocked(from: Vec3, lantern: Vec3, creeper: BlockPos): Boolean {
        val x = lantern.x - from.x
        val z = lantern.z - from.z
        val dist = x * x + z * z
        if (dist < 0.1) return false

        val cx = creeper.x + 0.5 - from.x
        val cz = creeper.z + 0.5 - from.z

        val dot = (cx * x + cz * z) / dist
        if (dot !in 0.0..1.0) return false

        val offset = abs(cx * z - cz * x) / sqrt(dist)

        return offset < 1.0
    }

    private fun getPositionSpot(room: OdonRoom, lantern: Vec3, creeper: BlockPos) =
        platformSpots.map { room.getRealCoords(it) }.firstOrNull { spot ->
            !isPathBlocked(Vec3.atCenterOf(spot).add(0.0, 1.5, 0.0), lantern, creeper)
        }

    private val platformSpots = listOf(
        BlockPos(14, 74, 14), BlockPos(14, 74, 15), BlockPos(14, 74, 16),
        BlockPos(15, 74, 14),                       BlockPos(15, 74, 16),
        BlockPos(16, 74, 14), BlockPos(16, 74, 15), BlockPos(16, 74, 16)
    )

    private val colours = listOf(
        Colour.MINECRAFT_GOLD,
        Colour.MINECRAFT_GREEN,
        Colour.MINECRAFT_LIGHT_PURPLE,
        Colour.MINECRAFT_DARK_AQUA,
        Colour.MINECRAFT_YELLOW,
        Colour.MINECRAFT_DARK_RED,
        Colour.WHITE,
        Colour.MINECRAFT_DARK_PURPLE
    )

    private data class LanternPair(
        val first: BlockPos,
        val second: BlockPos,
        var stage: Int = 0 // 0 = not hit, 1 = first hit, 2 = both hit
    )
}