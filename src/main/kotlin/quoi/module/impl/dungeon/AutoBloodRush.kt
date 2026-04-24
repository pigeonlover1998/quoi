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
import quoi.module.Module
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.utils.*
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.WorldUtils.state
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.PlayerUtils.useItem
import quoi.utils.skyblock.player.SwapManager
import kotlin.math.roundToInt

object AutoBloodRush : Module( // todo clean up some day (probably never)
    "Auto Blood Rush",
    desc = "Automatically blood rushes.",
    area = Island.Dungeon,
    tag = Tag.LEGACY
) {
    private val minTicksBeforeDeath by slider("Minimum ticks before death", 35, 15, 40, unit = "t", desc = "Triggers when remaining ticks until death are at least this value. Higher values make the macro slower (in some cases), but more consistent.")
    private val scanCorners by switch("Scan corners", desc = "Scans the corners when looking for blood.").open()
    private val cornersAmount by slider("Amount", 2, 1, 3, desc = "Amount of corners to go through.").childOf(::scanCorners)
    private val debug by switch("Debug").hide()

    private var bloodCoords: Vec3? = null
    private var tickerThing: Ticker? = null

    private var tpsReceived = 0
    private var tpsAmount = 0
    private var doneTeleporting = false

    private var firstScan = true
    private var goingMid = false
    private var runStarted = false

    private val mid = Vec3(-104.0, 0.0, -104.0)

    val corners = arrayOf(
        Vec3(-188.0, 0.0, -188.0), // nw
        Vec3(-28.0, 0.0, -188.0), // ne
        Vec3(-188.0, 0.0, -28.0), // sw
        Vec3(-28.0, 0.0, -28.0) // se
    )

//    val sides = arrayOf(
//        Vec3(-104.0, 0.0, -188.0), // n
//        Vec3(-104.0, 0.0, -28.0), // s
//        Vec3(-188.0, 0.0, -104.0), // w
//        Vec3(-28.0, 0.0, -104.0) // e
//    )

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
            if (bloodCoords == null) return@on
            if (message.noControlCodes == "[NPC] Mort: Here, I found this map when I first entered the dungeon.")
                runStarted = true
        }

        on<PacketEvent.Received> {
            if (goingMid && packet is ClientboundPlayerPositionPacket) {
                if (packet.change.position.y in 75.0..77.0) {
                    goingMid = false
                    tickerThing = null
                    scheduleTask(2) {
                        tickerThing = ticker {
                            position()
                            roof()
//                            delay(2)
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
                modMessage("Found blood at $bloodCoords")
            }

            if (debug || !firstScan) return@on

            if (currentRoom == null) return@on

            firstScan = false
            tickerThing = ticker {
                position()
                roof()
                delay(5)
                action {
                    if (bloodCoords == null) {
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
        delay(1) // idk
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
        delay(1) // dk
    }

    private fun findBlood() = ticker {
        var c1: Vec3? = null
        var c2: Vec3? = null

        action {
            if (player.y < 95 || !SwapManager.swapById("ASPECT_OF_THE_VOID").success) {
                cancel()
            }
            mc.options.keyShift.isDown = false
        }

        action {
            awaitTp(6)
            repeat(6) { player.useItem(0, -90) }
        }

        await { doneTeleporting() }

        action {
            teleport(to = mid)
        }

        if (!scanCorners) return@ticker

        await { doneTeleporting() }

        action {
            c1 = getUnexploredCorner()
            teleport(to = c1)
        }

        await { doneTeleporting() }

        action {
            if (cornersAmount < 2) cancel()
            c2 = getUnexploredCorner { it != c1 }
            teleport(to = c2)
        }

        await { doneTeleporting() }

        action {
            if (cornersAmount < 3) cancel()
            val c3 = getUnexploredCorner { it != c1 && it != c2 }
            teleport(to = c3)
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

        action(1) {
            val yaw = getFreeDirection(currentRoom!!) ?: cancel()
            awaitTp(4)
            repeat(4) { player.useItem(yaw, 0) }
        }

        await { doneTeleporting() }

        action {
            awaitTp(8)
            repeat(8) { player.useItem(0, 90) }
        }

        await { doneTeleporting() }

        action {
            val target = bloodCoords ?: cancel()
            teleport(to = target)
        }

        await { doneTeleporting() }

        action {
            awaitTp(8)
            repeat(8) { player.useItem(0, -90) }
        }

        await { doneTeleporting() }

        action {
            SwapManager.swapByName("pearl")
            awaitTp(2)
        }

        repeat(2) {
            action { player.useItem(0, -90) }
        }

        await { doneTeleporting() }

        await {
            if (player.y >= 67) {
                return@await true
            }

            if (tpsAmount == 0 || doneTeleporting()) {
                awaitTp(1)
                player.useItem(0, -90)
            }
            false
        }

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

    private fun teleport(to: Vec3) {
        val dir = getDirection(from = player.position(), to = to)
        val times = (player.position().distanceTo2D(to) / 12).roundToInt()

        awaitTp(times)
        repeat(times) { player.useItem(dir.yaw, 0) }
    }

    private fun getFreeDirection(entrance: OdonRoom): Float? {
        val directions = mapOf(
            (0 to -32) to 180f,
            (0 to 32) to 0f,
            (-32 to 0) to 90f,
            (32 to 0) to -90f
        )

//        val yaw = directions.entries.firstOrNull { (offset, _) ->
//            val (dx, dz) = offset
//
//            entrance.roomComponents.none { comp ->
//
//                val vec = Vec2i(comp.x + dx, comp.z + dz)
//
//                ScanUtils.scannedRooms.any { room ->
//                    room.roomComponents.any { it.vec2 == vec }
//                }
//            }
//        }?.value ?: return null

        val yaw = directions.entries.firstOrNull { (offset, _) ->
            val (dx, dz) = offset

            entrance.roomComponents.all { comp ->
                val x = comp.x + dx
                val z = comp.z + dz

                x < -200 || x > -8 || z < -200 || z > -8
            }
        }?.value ?: return null

        return yaw
    }

    private fun getUnexploredCorner(predicate: (Vec3) -> Boolean = { true }): Vec3 {
        return corners.filter(predicate).minByOrNull { corner ->
            ScanUtils.scannedRooms.count { room ->
                val inX = if (corner.x < -104) room.clayPos.x < -104 else room.clayPos.x > -104
                val inZ = if (corner.z < -104) room.clayPos.z < -104 else room.clayPos.z > -104
                inX && inZ
            }
        } ?: corners.firstOrNull(predicate) ?: corners[0]
    }
}