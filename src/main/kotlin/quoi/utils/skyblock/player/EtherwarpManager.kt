package quoi.utils.skyblock.player

import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.scope
import quoi.annotations.Init
import quoi.api.events.KeyEvent
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventBus.on
import quoi.api.pathfinding.impl.EtherwarpPathfinder
import quoi.api.vec.MutableVec3
import quoi.module.impl.render.ClickGui
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Direction
import quoi.utils.WorldUtils.etherwarpable
import quoi.utils.WorldUtils.nearbyBlocks
import quoi.utils.addVec
import quoi.utils.eyePosition
import quoi.utils.floorPos
import quoi.utils.getEyeHeight
import quoi.utils.player
import quoi.utils.skyblock.item.TeleportUtils.getEtherPos
import quoi.utils.skyblock.item.TeleportUtils.getEtherwarpDirection
import quoi.utils.skyblock.item.ItemUtils.skyblockId
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.PlayerUtils.useItem
import quoi.utils.sq
import kotlin.collections.indexOfFirst

@Init
object EtherwarpManager {
    private var nodes: MutableList<EtherNode>? = null

    private var delay = 0
    private var postDelay = 0
    var active = false
        private set

    private var pending: Direction? = null
    private var position: MutableVec3? = null

    init {
        on<PacketEvent.Received> {
            if (packet is ClientboundPlayerPositionPacket) if (delay == 1) delay = 2
        }

        on<TickEvent.Server> {
            if (delay < 2) return@on
            if (delay++ > 9) delay = 0
        }

        on<TickEvent.Start> {
            pending?.let {
                player.useItem(it.yaw, it.pitch)
                pending = null
            }

            if (postDelay > 0) {
                if (--postDelay == 0) active = false
            }

            if (delay != 0) return@on

            if (postDelay == 0) active = false

            if (nodes.isNullOrEmpty()) return@on

            if (position == null) {
                position = MutableVec3(player.position())
            }

            handleQueue(position!!, nodes!!)
        }

        on<KeyEvent.Input> {
            if (!active) return@on
            input.shift = true
        }

        on<WorldEvent.Change> {
            nodes = null
            delay = 0
            position = null
            active = false
            postDelay = 0
        }
    }

    private fun handleQueue(stupid: MutableVec3, etherNodes: MutableList<EtherNode>): Boolean {
        val index = etherNodes.indexOfFirst { it.inside(stupid) }

        if (index < 0) return false

        if (player.mainHandItem.skyblockId != "ASPECT_OF_THE_VOID") {
            if (!SwapManager.swapById("ASPECT_OF_THE_VOID").success) {
                nodes = null
                position = null
            }
//            return false
        }

        active = true
        val node = etherNodes[index]

        if (node.execute(stupid)) {
            etherNodes.removeAt(index)
            if (etherNodes.isEmpty()) {
                nodes = null
                position = null
                delay = 1
                postDelay = 2
            }
            return true
        }

        return false
    }

    fun path(
        to: BlockPos,
        yawStep: Float = ClickGui.yawStep,
        pitchStep: Float = ClickGui.pitchStep,
        hWeight: Double = ClickGui.hWeight,
        threads: Int = ClickGui.threads,
        timeout: Long = ClickGui.timeout,
    ) {
        if (player.at(to)) return modMessage("Already there")

        val start = getStart() ?: return modMessage("Could not find a valid etherwarpable block nearby.")

        scope.launch {
            val p = EtherwarpPathfinder.findDungeonPath(
                start = start,
                goal = to,
                yawStep = yawStep,
                pitchStep = pitchStep,
                hWeight = hWeight,
                threads = threads,
                timeout = timeout,
                offset = true,
                dist = 60.0
            ) ?: return@launch

            if (p.size < 2) return@launch

            val new = mutableListOf<EtherNode>()

            val dir = getEtherwarpDirection(player.eyePosition(true), p[1].pos, 60.0)
            val yaw = dir?.yaw ?: p[0].yaw
            val pitch = dir?.pitch ?: p[0].pitch

            new.add(EtherNode(player.position(), yaw, pitch))

            new.addAll(p.drop(1).dropLast(1).map { node ->
                val pos = node.pos.center.addVec(y = 0.5)
                EtherNode(pos, node.yaw, node.pitch)
            }.toMutableList())

            nodes = new

            position = null
            pending = null
        }
    }

    private fun getStart(): BlockPos? {
        val playerPos = player.position().floorPos

        if (getEtherwarpDirection(playerPos) != null) return playerPos

        return playerPos.nearbyBlocks(4f) { it.etherwarpable }
            .firstOrNull { pos -> getEtherwarpDirection(pos) != null }
    }

    private data class EtherNode(val pos: Vec3, val yaw: Float, val pitch: Float) {

        fun inside(playerPos: MutableVec3): Boolean {
            val dx = pos.x - playerPos.x
            val dy = pos.y - playerPos.y
            val dz = pos.z - playerPos.z
            return dx.sq + dy.sq + dz.sq <= 0.1
        }

        fun execute(playerPos: MutableVec3): Boolean {
            if (player.mainHandItem.skyblockId != "ASPECT_OF_THE_VOID") return false
            if (!player.lastSentInput.shift) return false

            val from = Vec3(playerPos.x, playerPos.y + getEyeHeight(true), playerPos.z)
            val ether = from.getEtherPos(yaw, pitch)

            if (!ether.succeeded || ether.pos == null) {
                val next = nodes?.getOrNull((nodes?.indexOf(this) ?: -1) + 1)
                nodes = null
                position = null
                postDelay = 2
                modMessage("""
                    
                    &eFrom&7:&f $from
                    &eTo&7:&r ${next?.pos}
                    &eYaw&7:&r $yaw
                    &ePitch&7:&r $pitch
                    &eEther&7:&r $ether
                """.trimIndent(), prefix = "[&cEtherwarp&r]")
                return false
            }

            pending = Direction(yaw, pitch)

            playerPos.x = ether.pos.x + 0.5
            playerPos.y = ether.pos.y + 1.05
            playerPos.z = ether.pos.z + 0.5
            return true
        }
    }
}