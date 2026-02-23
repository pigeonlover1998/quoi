package quoi.module.impl.dungeon

import quoi.api.events.*
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.api.skyblock.dungeon.Dungeon.isDead
import quoi.api.skyblock.dungeon.Dungeon.uniqueRooms
import quoi.api.skyblock.dungeon.RoomType
import quoi.api.skyblock.dungeon.components.Room
import quoi.module.Module
import quoi.module.settings.impl.BooleanSetting
import quoi.utils.ChatUtils.modMessage
import quoi.utils.EntityUtils.distanceTo
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.Ticker
import quoi.utils.WorldUtils.state
import quoi.utils.etherwarpRotateTo
import quoi.utils.getDirection
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.PlayerUtils.rotate
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.ticker
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

object AutoBloodRush : Module(
    "Auto Blood Rush",
    area = Island.Dungeon
) {
    private val debug by BooleanSetting("Debug")
    private val mid = Vec3(-130.0, 0.0, -130.0) // kinda in the middle
    private var bloodCoords: Vec3? = null
    private var tickerThing: Ticker? = null
    private var clipped = false
    private var tpsReceived = 0
    private var tpsAmount = 0
    private var doneTeleporting = false

    private val leaf1 = BlockPos(7, 88, 13)
    private val leaf2 = BlockPos(20, 88, 11)

    private val leafCoords: BlockPos
        get() {
            val room = currentRoom!!
            val real1 = room.getRealCoords(leaf1)
            val real2 = room.getRealCoords(leaf2)
            return if (real1.state?.block == Blocks.OAK_LEAVES) real1 else real2 // I am a retarded cunt, I've been doing leaf1.state?.block == Blocks.OAK_LEAVES..... FUCKING RELATIVE COORDS. I AM A RETARD. I SPENT 40 MINS DEBUGGING????
        }

    init {
//        command.sub("br") {
////            modMessage(leafCoords)
////            var (_, yaw, pitch) = etherwarpRotateTo(leafCoords) ?: return@sub modMessage("NULL")
////            player.rotate(yaw, pitch)
//            tickerThing = doBr()
////            tpsReceived = 0
////            tpsAmount = 2
//        }
        on<TickEvent.End> {
            if (isDead) return@on
            if (clipped && player.y != 99.0) tickerThing = null

            tickerThing?.let {
                if (it.tick()) tickerThing = null
            }
        }

        on<ChatEvent.Packet> {
            if (debug) return@on
            if (currentRoom?.name != "Entrance") return@on
            when (message.noControlCodes) {
                "Starting in 4 seconds." -> tickerThing = leaf()
                "[NPC] Mort: Here, I found this map when I first entered the dungeon." -> tickerThing = doBr()
            }
        }

        on<PacketEvent.Received> {
//            if (tickerThing == null) return@on
            if (tpsAmount == 0) return@on
            if (!player.mainHandItem.displayName.string.noControlCodes.contains("aspect of the void", true)) return@on

            val flag = when (packet) {
                is ClientboundSystemChatPacket -> packet.content.string.noControlCodes == "There are blocks in the way!" // if it goes in the bedrock
                is ClientboundPlayerPositionPacket -> true
                else -> false
            }
            if (!flag) return@on
            if (++tpsReceived == tpsAmount) {
                doneTeleporting = true
                tpsReceived = 0
                tpsAmount = 0
            }
        }

        on<DungeonEvent.Room.Scan> {
            if (room.type == RoomType.BLOOD) {
                val (x, y) = room.realComponents.first()
                modMessage("Found blood at $x $y")
                bloodCoords = Vec3(x.toDouble(), 70.0, y.toDouble())
            }
        }

        on<WorldEvent.Change> {
            tickerThing = null
            bloodCoords = null
            clipped = false
            doneTeleporting = false
            tpsReceived = 0
            tpsAmount = 0
        }
    }

    private fun leaf() = ticker {
        action {
            mc.options.keyShift.isDown = true
            val (_, yaw, pitch) = etherwarpRotateTo(leafCoords)
                ?: run { tickerThing = null; return@action } // todo do something about this
            player.rotate(yaw, pitch)
            SwapManager.swapByName("aspect of the void")
        }
        action {
            PlayerUtils.rightClick()
        }
        await { player.y == 89.0 }
        action {
            player.rotate(0, -90)
            mc.options.keyShift.isDown = false
        }
        action {
            SwapManager.swapByName("pearl")
        }
        repeat(5) {
            action { PlayerUtils.rightClick() }
        }
        action {
            val yaw = getFreeDirection(currentRoom!!)
                ?: run { tickerThing = null; return@action } // todo do something about this
            player.rotate(yaw, 0)
        }
        action {
            SwapManager.swapByName("aspect of the void")
//            clipped = true
        }
    }

    private fun doBr() = ticker {
        action {
            if (player.blockPosition().above(1).state?.isAir != true) {
                tickerThing = null
                return@action
            }
            doTeleport(2)
        }
        await { doneTeleporting() }
        action {
            player.rotate(0, 90)
        }
        action {
            doTeleport(8)
        }
        await { doneTeleporting() }
        action {
            val (_, yaw, _) = getDirection(player.eyePosition, bloodCoords ?: mid)
            player.rotate(yaw, 0)
        }
        action {
            val bloodCoords = bloodCoords ?: run {
                tickerThing = null
                return@action
            }
            val dist = player.distanceTo(bloodCoords).toInt()
            val times = dist / 12 - 1
            doTeleport(times)
        }
        await { doneTeleporting() }
        action {
            player.rotate(0, -90)
        }
        action {
            doTeleport(4, true)
        }
        await { doneTeleporting() }
        action {
            SwapManager.swapByName("pearl")
        }
        repeat(2) {
            action { PlayerUtils.rightClick() }
        }
    }

    private fun doTeleport(times: Int, counting: Boolean = true) {
        if (counting) {
            tpsReceived = 0
            tpsAmount = times
        }
        repeat(times) {
            PlayerUtils.rightClick()
        }
    }

    private fun doneTeleporting(): Boolean {
        if (doneTeleporting) {
            doneTeleporting = false
            return true
        }
        return false
    }

    private fun getFreeDirection(entrance: Room): Float? {
        val directions = listOf(
            Pair(0, -1) to 180f,
            Pair(0, 1) to 0f,
            Pair(-1, 0) to 90f,
            Pair(1, 0) to -90f
        )

        return directions.firstOrNull { (off, _) ->
            val (dx,dz) = off
            uniqueRooms.none {
                it != entrance &&
                it.components.any { (x,z) ->
                    entrance.components.contains(Pair(x - dx,z - dz))
                }
            }
        }?.second
    }
}