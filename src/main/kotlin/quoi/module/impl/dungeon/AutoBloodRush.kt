package quoi.module.impl.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.world.phys.Vec3
import quoi.api.events.*
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.api.skyblock.dungeon.Dungeon.isDead
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomType
import quoi.api.vec.MutableVec3
import quoi.module.Module
import quoi.utils.*
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.toFixed
import quoi.utils.WorldUtils.state
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.PlayerUtils.useItem
import quoi.utils.skyblock.player.RotationUtils.rotate
import quoi.utils.skyblock.player.SwapManager
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin

object AutoBloodRush : Module( // maybe works on everyone else's machines. fixme: tping somewhere in the fucking corner for no reason.
    "Auto Blood Rush",
    desc = "Automatically blood rushes.",
    area = Island.Dungeon,
    tag = Tag.BETA
) {
    private val minTicksBeforeDeath by slider("Minimum ticks before death", 30, 15, 40, unit = "t", desc = "Triggers when remaining ticks until death are at least this value. Higher values make the macro slower (in some cases), but more consistent.")
    private val consistent by switch("I want consistency man.", desc = "Very slow.")
    private val debug by switch("Debug")

    private var bloodCoords: Vec3? = null
    private var tickerThing: Ticker? = null

    private var tpsReceived = 0
    private var tpsAmount = 0
    private var doneTeleporting = false

    private var firstScan = true
    private var goingMid = false
    private var runStarted = false

    private val mid = Vec3(-104.0, 0.0, -104.0)

    private val directions = mapOf(
        (0 to -2) to 180f,
        (0 to 2) to 0f,
        (-2 to 0) to 90f,
        (2 to 0) to -90f
    )

    private val pendingTps = mutableListOf<Stupid>()

    private inline val etherBlock: BlockPos
        get() {
            val room = currentRoom!!

            val relativePos = if (!room.getRealCoords(BlockPos(15, 73, 24)).state.isAir) {
                BlockPos(2, 82, 18)
            } else {
                when (room.rotation.deg) {
                    0, 90 -> BlockPos(2, 81, 15)
                    else -> BlockPos(2, 81, 16)
                }
            }

            return room.getRealCoords(relativePos)
        }

    private var position: MutableVec3? = null

    init {
        on<TickEvent.Start> {
            if (isDead) return@on
            if (pendingTps.isNotEmpty()) {
                pendingTps.toList().forEach { a ->
                    repeat(a.times) {
                        player.useItem(a.yaw, a.pitch)
                    }
                    pendingTps.remove(a)
                }
            }

            tickerThing?.let {
                if (it.tick()) tickerThing = null
            }
        }

        on<TickEvent.End> {
            if (isDead) return@on

//            tickerThing?.let {
//                if (it.tick()) tickerThing = null
//            }
        }

        on<DungeonEvent.Start> {
            if (currentRoom?.name != "Entrance") return@on
            if (bloodCoords == null) return@on
            debug("run started")
            runStarted = true
        }

        on<PacketEvent.Received> {
            if (goingMid && packet is ClientboundPlayerPositionPacket) {
                if (packet.change.position.y in 75.0..77.0) {
                    goingMid = false
                    tickerThing = null
                    debug("tped back from mid")
                    scheduleTask(2) {
                        tickerThing = ticker {
                            position()
                            roof()
                            br()
                        }
                    }
                }
            }

            val flag = when (packet) {
                is ClientboundSystemChatPacket -> packet.content.string.noControlCodes == "There are blocks in the way!" // if it goes in the bedrock
                is ClientboundPlayerPositionPacket -> true
                else -> false
            }

            if (!flag) return@on
            if (tickerThing == null || tpsAmount == 0) return@on
            if (++tpsReceived == tpsAmount) {
                doneTeleporting = true
                tpsReceived = 0
                tpsAmount = 0
            }
        }

        on<DungeonEvent.Room.Scan> {
            if (room.data.type == RoomType.BLOOD) {
                bloodCoords = room.getRealCoords(Vec3(15.0, 70.0, 15.0))
                debug("Found blood at $bloodCoords")
            }

            if (currentRoom == null || !firstScan) return@on

            firstScan = false
            tickerThing = ticker {
                position()
                roof()
                action(1) {
                    if (bloodCoords == null) {
                        debug("no blood")
                        goingMid = true
                        tickerThing = findBlood()
                    }
                }
                br()
            }
        }

        on<WorldEvent.Change> {
            tickerThing = null
            bloodCoords = null

            doneTeleporting = false
            tpsReceived = 0
            tpsAmount = 0

            firstScan = true
            goingMid = false
            runStarted = false
            position = null

            pendingTps.clear()
        }
    }

    private fun TickerScope.position() {
        await { player.onGround() }
        action {
            val swap = SwapManager.swapById("ASPECT_OF_THE_VOID")
            if (currentRoom?.name != "Entrance" || !swap.success) {
                cancel()
            }

            mc.options.keyShift.isDown = true
        }
        action {
            val dir = getEtherwarpDirection(etherBlock) ?: cancel()
            player.useItem(dir)
        }
        await {
            if (player.at(etherBlock)) {
                mc.options.keyShift.isDown = false
                return@await true
            }
            false
        }
    }

    private fun TickerScope.roof() {
        action {
            if (SwapManager.swapByName("pearl").success) {
                awaitTp(4)
            } else cancel()
        }
        repeat(4) { // split otherwise it gets fucked
            action { PlayerUtils.interact() }
        }

        await { doneTeleporting() }

        action {
            awaitTp(3)
        }

        repeat(3) {
            action { PlayerUtils.interact() }
        }

        await { doneTeleporting() }

        await { // throws an extra pearl sometimes
            if (player.blockPosition().above(1).state.isAir) {
                SwapManager.swapById("ASPECT_OF_THE_VOID").success
                return@await true
            }

            if (doneTeleporting()) return@await false

            if (tpsAmount == 0) {
                awaitTp(1)
                PlayerUtils.interact()
            }

            false
        }
    }

    private fun findBlood() = ticker {
        action {
            if (player.y < 95 || !SwapManager.swapById("ASPECT_OF_THE_VOID").success) {
                cancel()
            }
            mc.options.keyShift.isDown = false

            debug("looking for blood..")
        }

        action {
            qTp(0, -90, 6)
        }

        action {
            val to = mid
            val dir = getDirection(from = player.position(), to = to)
            val times = (player.position().distanceTo2D(to) / 12).roundToInt()
            awaitTp(6 + times)
            qTp(dir.yaw, 0, times)
        }
    }

    private fun TickerScope.br() {
        action {
            if (player.y < 95 || !SwapManager.swapById("ASPECT_OF_THE_VOID").success) {
                cancel()
            }
            mc.options.keyShift.isDown = false
        }

        await { runStarted && Dungeon.deathTick >= minTicksBeforeDeath }

        action { // out
            val yaw = getFreeDirection(currentRoom!!) ?: cancel()

            val moved = 4 * 12.0
            val px = player.x + when (yaw) {
                90f -> -moved
                -90f -> moved
                else -> 0.0
            }

            val pz = player.z + when (yaw) {
                180f -> -moved
                0f -> moved
                else -> 0.0
            }

            position = MutableVec3(px, player.y, pz)

            if (consistent) {
                repeat(4) { player.useItem(yaw, -10) }
            } else {
                awaitTp(12)
                qTp(yaw, -10, 4)
            }
            debug("""
                GOING OUT
                YAW: $yaw
                PREDICTED POS: ${position!!.x} z: ${position!!.z}
            """.trimIndent())
        }

        await { return@await if (consistent) doneTeleporting() else true }

        action { // down
            if (consistent) {
                awaitTp(8)
                repeat(8) { player.useItem(0, 90) }
            } else {
                qTp(0, 90, 8)
            }
        }

        await { doneTeleporting() }

        action { // blood
            val to = bloodCoords ?: cancel()
            val from = position?.immutable() ?: cancel()
            val dir = getDirection(from = from, to = to)
            val times = (from.distanceTo2D(to) / 12).roundToInt()

            val moved = times * 12.0
            val rad = dir.yaw.rad
            val predX = from.x + (-sin(rad) * moved)
            val predZ = from.z + (cos(rad) * moved)

//            awaitTp(4 + 8 + times + 8)

//            qTp(dir.yaw, 0, times)
            awaitTp(times)
            player.rotate(dir.yaw, 0)
            repeat(times) {
//                player.useItem(dir.yaw, 0)
                PlayerUtils.interact()
            }

            debug(
                """
                GOING TO BLOOD $bloodCoords
                FROM: x: ${from.x} z: ${from.z}
                TO PRED: x: ${predX.toFixed()} z: ${predZ.toFixed()}
                TIMES: $times
                DIR: $dir
            """.trimIndent())
        }

        await { return@await if (consistent) doneTeleporting() else true }

        action { // up
            if (consistent) {
                awaitTp(8)
                repeat(8) { player.useItem(0, -90) }
            } else {
                qTp(0, -90, 8)
            }
        }

        await { pendingTps.isEmpty() }

        action {
            SwapManager.swapByName("pearl")
            awaitTp(2)

            debug("PEARLING UP")
        }

        repeat(2) {
            action { player.useItem(0, -90) }
        }

        action { player.useItem(0, 45) }

//        await { return@await if (consistent) doneTeleporting() else true }
//
//        await {
//            if (player.y >= 67 || !consistent) {
//                return@await true
//            }
//
//            if (tpsAmount == 0 || doneTeleporting()) {
//                awaitTp(1)
//                player.useItem(0, -90)
//            }
//            false
//        }

    }

    private fun doneTeleporting(): Boolean {
        if (doneTeleporting) {
            doneTeleporting = false
            return true
        }
        return false
    }

    private fun awaitTp(amount: Int) {
        tpsReceived = 0
        tpsAmount = amount
        doneTeleporting = false
    }

    private fun qTp(yaw: Number, pitch: Number, times: Int = 1) {
        pendingTps.add(Stupid(yaw.toFloat(), pitch.toFloat(), times))
    }

    private fun getFreeDirection(entrance: OdonRoom): Float? { // I hate this shit
        val comp = entrance.roomTiles.firstOrNull() ?: return null

        val gx = (round((comp.x - -185) / 32.0).toInt() * 2)
        val gz = (round((comp.z - -185) / 32.0).toInt() * 2)

        return directions.entries.firstOrNull { (offset, _) ->
            val nx = gx + offset.first
            val nz = gz + offset.second

            nx !in 0..10 || nz !in 0..10 || ScanUtils.grid[nz * 11 + nx] == null
        }?.value
    }

    private fun debug(text: String) {
        if (debug) modMessage(text, prefix = "&b[autobr]&r")
    }

    private data class Stupid(val yaw: Float, val pitch: Float, val times: Int)
}