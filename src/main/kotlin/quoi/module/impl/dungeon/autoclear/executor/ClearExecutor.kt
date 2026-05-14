package quoi.module.impl.dungeon.autoclear.executor

import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import quoi.QuoiMod.scope
import quoi.annotations.Init
import quoi.api.events.*
import quoi.api.events.core.EventBus.on
import quoi.api.pathfinding.PathConfig
import quoi.api.pathfinding.impl.EtherwarpPathfinder
import quoi.api.vec.MutableVec3
import quoi.api.world.Direction
import quoi.module.impl.dungeon.autoclear.executor.nodes.ClearEtherNode
import quoi.module.impl.dungeon.autoclear.executor.nodes.ClearHypeNode
import quoi.module.impl.dungeon.autoclear.toEther
import quoi.utils.ChatUtils.modMessage
import quoi.utils.player
import quoi.utils.skyblock.player.PlayerUtils.at
import quoi.utils.skyblock.player.PlayerUtils.useItem

@Init
object ClearExecutor {
    private var nodes: MutableList<ClearNode>? = null

    private var delay = 0
    private var postDelay = 0
    private var executeDelay = 0

    var active = false
        private set

    private var pendingInteract: Direction? = null
    private var position: MutableVec3? = null
    private var activeNode: ClearNode? = null

    init {
        on<PacketEvent.Received> {
            if (packet is ClientboundPlayerPositionPacket) if (delay == 1) delay = 2
        }

        on<TickEvent.Server> {
            if (delay < 2) return@on
            if (delay++ > 9) delay = 0
        }

        on<TickEvent.Start> {
            pendingInteract?.let {
                player.useItem(it.yaw, it.pitch)
                pendingInteract = null
            }

            if (postDelay > 0) {
                if (--postDelay == 0) active = false
            }

            if (executeDelay > 0) {
                if (--executeDelay > 0) return@on
            }

            if (delay != 0) return@on

            if (postDelay == 0) active = false

            if (nodes.isNullOrEmpty()) return@on

            if (position == null) {
                position = MutableVec3(player.position())
            }

            handleQueue(position!!, nodes!!)
        }

        on<RenderEvent.World> {
            if (nodes.isNullOrEmpty()) return@on
            nodes!!.forEach { it.render(ctx) }
        }

        on<KeyEvent.Input> {
            if (!active) return@on
            if (activeNode == null) return@on
            input.shift = activeNode is ClearEtherNode
        }

        on<WorldEvent.Change> {
            nodes = null
            delay = 0
            postDelay = 0
            executeDelay = 0
            position = null
            active = false
            activeNode = null
        }
    }

    private fun handleQueue(playerPos: MutableVec3, clearNodes: MutableList<ClearNode>) {
        val node = clearNodes.filter { it.inside(playerPos) }.maxByOrNull { it.priority } ?: run {
            activeNode = null
            position = null
            return
        }

        active = true
        activeNode = node

        if (node.execute(playerPos)) {
            clearNodes.remove(node)

            if (node is ClearHypeNode) {
                executeDelay = 3
            }

            if (clearNodes.isEmpty()) {
                cancel()
                activeNode = null
                delay = 1
            }
        }
    }

    fun etherPath(to: BlockPos, config: PathConfig = PathConfig()) {
        if (player.at(to)) return modMessage("Already there")

        scope.launch {
            val p = EtherwarpPathfinder.findDungeonPath(
                start = player.position(),
                goal = to,
                config = config,
            ) ?: return@launch

            nodes = p.map { it.toEther() }.toMutableList()
            position = null
            pendingInteract = null
        }
    }

    fun testPath(to: BlockPos, config: PathConfig = PathConfig()) {
//        if (player.at(to)) return modMessage("Already there")
//
//        scope.launch {
//            val p = TransmissionPathfinder.findPath(
//                start = player.position(),
//                goal = to,
//                config = config,
//                dist = 12.0,
//                ground = true,
//            ) ?: return@launch
//
//            nodes = p.map { it.toAotv() }.toMutableList()
//            position = null
//            pendingInteract = null
//        }
    }

    fun cancel() {
        nodes = null
        position = null
        postDelay = 2
    }
}
