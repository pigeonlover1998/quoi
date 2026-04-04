package quoi.module.impl.dungeon

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.phys.Vec3
import quoi.api.events.*
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.api.skyblock.dungeon.Dungeon.isDead
import quoi.api.skyblock.dungeon.odonscanning.ScanUtils
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.dungeon.odonscanning.tiles.RoomType
import quoi.module.Module
import quoi.utils.*
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.WorldUtils.state
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.RotationUtils.rotate
import quoi.utils.skyblock.player.PlayerUtils.useItem
import quoi.utils.skyblock.player.SwapManager
import kotlin.math.roundToInt

object AutoBloodRush : Module( // inconsistent
    "Auto Blood Rush",
    desc = "Automatically blood rushes.",
    area = Island.Dungeon
) {
    private val debug by switch("Debug").hide()

    private val mid = Vec3(-104.0, 0.0, -104.0)

    private var bloodCoords: Vec3? = null
    private var tickerThing: Ticker? = null

    private var tpsReceived = 0
    private var tpsAmount = 0
    private var doneTeleporting = false

    private var firstScan = true
    private var goingMid = false

    private val etherBlock: BlockPos
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

    init {
//        command.sub("br") { stage: Int ->
//            val a = when (stage) {
//                1 -> ticker { position() }
//                2 -> ticker { roof() }
//                3 -> br()
//                else -> return@sub
//            }
//            tickerThing = a
//        }.requires { enabled && debug }

        on<TickEvent.End> {
            if (isDead) return@on

            tickerThing?.let {
                if (it.tick()) tickerThing = null
            }
        }

        on<ChatEvent.Packet> {
            if (debug) return@on
            if (currentRoom?.name != "Entrance") return@on
            if (bloodCoords == null || player.y < 95.0) return@on
            when (message.noControlCodes) {
//                "Starting in 4 seconds." -> tickerThing = leaf()
                "[NPC] Mort: Here, I found this map when I first entered the dungeon." -> tickerThing = br()
            }
        }

        on<PacketEvent.Received, ClientboundPlayerPositionPacket> {
            if (goingMid) {
                if (packet.change.position.y in 75.0..77.0) {
                    goingMid = false
                    scheduleTask(2) {
                        tickerThing = ticker {
                            position()
                            roof()
                            delay(10)
                        }
                    }
                }
            }

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
                modMessage("Found blood at $bloodCoords")
            }

            if (debug || !firstScan) return@on

            if (currentRoom == null) return@on

            firstScan = false
            tickerThing = ticker {
                position()
                roof()
                delay(10)
                action {
                    if (bloodCoords == null) {
                        goingMid = true
                        tickerThing = br()
                    }
                }
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
        }
    }

    private fun TickerScope.position() {
        val spot = etherBlock
        await { player.onGround() }
        action {
            val swap = SwapManager.swapById("ASPECT_OF_THE_VOID")
            if (currentRoom?.name != "Entrance" || !swap.success) {
                cancel()
            }

            mc.options.keyShift.isDown = true
        }
        delay(1) // idk
        action {
            val dir = getEtherwarpDirection(spot) ?: cancel()
            player.useItem(dir)
        }
        await {
            if (player.at(spot)) {
                mc.options.keyShift.isDown = false
                return@await true
            }
            false
        }
    }

    private fun TickerScope.roof() {
        action {
            if (SwapManager.swapByName("pearl").success) {
                tpsReceived = 0
                tpsAmount = 4
                doneTeleporting = false
            } else cancel()
        }
        repeat(4) { // split otherwise it gets fucked
            action { PlayerUtils.interact() }
        }

        await { doneTeleporting() }

        action {
            tpsReceived = 0
            tpsAmount = 4
            doneTeleporting = false
        }

        repeat(4) {
            action { PlayerUtils.interact() }
        }

        await { doneTeleporting() }

        await {
            if (player.blockPosition().above(1).state.isAir) {
                SwapManager.swapById("ASPECT_OF_THE_VOID").success
                return@await true
            }

            if (tpsAmount == 0 || doneTeleporting()) { // todo fix
//                modMessage("I AM A NI")
                tpsReceived = 0
                tpsAmount = 1
                doneTeleporting = false
                PlayerUtils.interact()
            }

            false
        }
        delay(1) // dk
    }

    private fun br() = ticker {

//        await { Dungeon.deathTick > 30 }

        action {
            if (player.y < 95 || !SwapManager.swapById("ASPECT_OF_THE_VOID").success) {
                cancel()
            }
            mc.options.keyShift.isDown = false
        }

        action(1) {
            val yaw = getFreeDirection(currentRoom!!) ?: cancel()

            val target = bloodCoords ?: mid

            val edgeTimes = 4
            val moved = edgeTimes * 12.0
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
            val predictedPos = Vec3(px, player.y, pz)

            val bloodDir = getDirection(predictedPos, target)
            val bloodTimes = (predictedPos.distanceTo2D(target) / 12).roundToInt()

            tpsReceived = 0
            tpsAmount = 3
            doneTeleporting = false

            repeat(edgeTimes) { player.useItem(yaw, 0) }
            repeat(9) { player.useItem(0, 90) }
            repeat(bloodTimes) { player.useItem(bloodDir.yaw, 0) }

            if (bloodCoords != null) {
                repeat(8) { player.useItem(0, -90) }
            } else {
                cancel()
            }
        }

        await { doneTeleporting() }


        // todo find a way to make this part consistent
        action {
            SwapManager.swapByName("pearl")
            player.rotate(0, -90)
        }

        repeat(2) {
            action { PlayerUtils.interact() }
        }

        action {
            player.rotate(0, 45)
            PlayerUtils.interact()
        }
    }

    private fun doneTeleporting(): Boolean {
        if (doneTeleporting) {
            doneTeleporting = false
            return true
        }
        return false
    }

    private fun getFreeDirection(entrance: OdonRoom): Float? {
        val directions = mapOf(
            (0 to -32) to 180f,
            (0 to 32) to 0f,
            (-32 to 0) to 90f,
            (32 to 0) to -90f
        )

        val yaw = directions.entries.firstOrNull { (offset, _) ->
            val (dx, dz) = offset

            entrance.roomComponents.none { comp ->

                val vec = Vec2(comp.x + dx, comp.z + dz)

                ScanUtils.scannedRooms.any { room ->
                    room.roomComponents.any { it.vec2 == vec }
                }
            }
        }?.value ?: return null

        return yaw
    }
}