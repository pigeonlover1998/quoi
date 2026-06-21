package quoi.module.impl.dungeon.puzzlesolvers.impl

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.*
import quoi.api.events.core.Event
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.module.impl.dungeon.autoclear.executor.ClearExecutor
import quoi.module.impl.dungeon.puzzlesolvers.PuzzleSolvers
import quoi.module.impl.dungeon.puzzlesolvers.Repositionable
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.group.SettingGroup
import quoi.utils.*
import quoi.utils.WorldUtils.state
import quoi.utils.render.drawLine
import quoi.utils.render.drawStyledBox
import quoi.utils.skyblock.item.ItemUtils.isShortbow
import quoi.utils.skyblock.item.TeleportUtils.getEtherwarpDirection
import quoi.utils.skyblock.player.PlayerUtils.useItem
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/Odin/blob/main/src/main/kotlin/com/odtheking/odin/features/impl/dungeon/puzzlesolvers/BeamsSolver.kt
 */
object CreeperBeams : SettingGroup(PuzzleSolvers, "Creeper beams"), Repositionable {

    private val solver by switch("Solver", desc = "Shows the solution for the creeper beams puzzle.")
    private val tracer by switch("Tracer").json("Beams tracer").childOf(::solver)
    private val style by selector("Style", "Box", arrayListOf("Box", "Filled box"), desc = "Render style to be used.").childOf(::solver)
    private val alpha by slider("Colour alpha", 0.7f, 0f, 1f, 0.05f).childOf(::solver)
    private val announce by switch("Announce completion", desc = "Sends complete message.").asParent()
    val auto by switch("Auto").asParent()

    private var lanternPairs: List<List<Int>> = PuzzleSolvers.loadSolution("creeperBeamsSolutions.json", emptyList())

    private var activePair: LanternPair? = null
    private var lastShotTime = 0L
    private var waitingForUpdate = false
    override var repositionTicker: Ticker? = null
    private var solvedPairs = 0

    init {
        on<DungeonEvent.Room.Enter> {
            if (room?.name != "Creeper Beams") return@on reset()
            else recalculateLanternPairs(room)
        }

        on<RenderEvent.World> {
            if (!solver || currentLanternPairs.isEmpty()) return@on

            currentLanternPairs.entries.forEach { (pos1, pair) ->
                val (pos2, col) = pair
                val colour = col.withAlpha(alpha)

                ctx.drawStyledBox(style.selected, AABB(pos1), colour, depth = true)
                ctx.drawStyledBox(style.selected, AABB(pos2), colour, depth = true)

                if (tracer)
                    ctx.drawLine(listOf(pos1.center, pos2.center), colour = colour, depth = false)
            }

            activePair?.let {
                val target = if (it.stage == 0) it.first else it.second
                ctx.drawStyledBox("Box", AABB(target), Colour.RED, depth = false)
            }
        }

        on<BlockEvent.Update> {
            if (!solver && !auto) return@on
            if (
                old.block.equalsOneOf(Blocks.PRISMARINE, Blocks.SEA_LANTERN) &&
                updated.block.equalsOneOf(Blocks.PRISMARINE, Blocks.SEA_LANTERN)
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

                return@on
            }
        }

        on<PacketEvent.Received, ClientboundSoundPacket> {
            if (!auto) return@on
            val pair = activePair ?: return@on
            if (packet.sound.registeredName != "minecraft:entity.elder_guardian.hurt") return@on

            val pos = if (pair.stage == 0) pair.first else pair.second
            if (!Vec3(pos).equal(Vec3(packet.x, packet.y, packet.z))) return@on

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

        on<TickEvent.End> {
            if (!auto || ClearExecutor.active || mc.screen != null || solvedPairs >= 4 || currentLanternPairs.isEmpty()) return@on
            val room = Dungeon.currentRoom ?: return@on

            val start = room.getRealCoords(BlockPos(16, 74, 14))
            if (start.state.isAir) return@on

            repositionTicker?.let {
                if (it.tick()) Scheduler.scheduleTask {
                    repositionTicker = null
                }
                return@on
            }

            if (player.y != 75.0) return@on reposition(start)

            if (activePair == null) {
                val entry = currentLanternPairs.entries.firstOrNull() ?: return@on
                activePair = LanternPair(entry.key, entry.value.first)
            }
            val pair = activePair ?: return@on
            val lantern = if (pair.stage == 0) pair.first else pair.second
            val lanternVec = Vec3.atCenterOf(lantern)

            val creeper = room.getRealCoords(BlockPos(15, 74, 15))

            if (isPathBlocked(player.eyePosition, lanternVec, creeper)) {
                val spot = getPositionSpot(room, lanternVec, creeper)
                if (spot != null) return@on reposition(spot)
            }

            val currentTime = System.currentTimeMillis()

            if (waitingForUpdate) {
                if (currentTime - lastShotTime > PuzzleSolvers.missCd)
                    waitingForUpdate = false
                else
                    return@on
            }

            if (!player.mainHandItem.isShortbow) return@on
            if (currentTime - lastShotTime < PuzzleSolvers.shootCd) return@on

            val dir = getEtherwarpDirection(lantern) ?: getDirection(lanternVec)
            player.useItem(dir)

            lastShotTime = currentTime
            waitingForUpdate = true
        }

        on<WorldEvent.Change> {
            reset()
        }
    }

    override fun shouldHandle(event: Event): Boolean {
        if (!super.shouldHandle(event)) return false

        if (event is DungeonEvent.Room.Enter) return true

        return Dungeon.currentRoom?.name == "Creeper Beams"
    }

    private var currentLanternPairs = ConcurrentHashMap<BlockPos, Pair<BlockPos, Colour>>()

    private fun recalculateLanternPairs(room: OdonRoom) {
        currentLanternPairs.clear()
        lanternPairs.forEachIndexed { i, l ->
            val pos = room.getRealCoords(BlockPos(l[0], l[1], l[2])).takeIf { it.state.block == Blocks.SEA_LANTERN } ?: return@forEachIndexed
            val pos2 = room.getRealCoords(BlockPos(l[3], l[4], l[5])).takeIf { it.state.block == Blocks.SEA_LANTERN } ?: return@forEachIndexed

            currentLanternPairs[pos] = pos2 to colours[i % colours.size]
        }

        val active = activePair ?: return
        if (!currentLanternPairs.containsKey(active.first)) {
            activePair = null
            waitingForUpdate = false
        }
    }

    private fun reset() {
        currentLanternPairs.clear()
        activePair = null
        waitingForUpdate = false
        lastShotTime = -1
        repositionTicker = null
        solvedPairs = 0
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
        BlockPos(15, 74, 14), BlockPos(15, 74, 16),
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