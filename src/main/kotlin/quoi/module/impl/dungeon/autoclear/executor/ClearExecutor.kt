package quoi.module.impl.dungeon.autoclear.executor

import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import quoi.QuoiMod.scope
import quoi.annotations.Init
import quoi.api.events.*
import quoi.api.events.core.on
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

    private var syncDelay = 0
    private var compDelay = 0
    private var globalDelay = 0
    private var hypeDelay = 0

    var active = false
        private set

    private var pendingInteract: Direction? = null
    private var position: MutableVec3? = null
    private var activeNode: ClearNode? = null

    init {
        on<PacketEvent.Received> {
            if (packet is ClientboundPlayerPositionPacket) if (syncDelay == 1) syncDelay = 2
        }

        on<TickEvent.Server> {
            if (syncDelay < 2) return@on
            if (syncDelay++ > 9) syncDelay = 0
        }

        on<TickEvent.Start> {
            doInteract()
            updateDelays()

            if (!canNext()) return@on

            if (position == null) position = MutableVec3(player.position())

            handleQueue(position!!, nodes!!)
        }

        on<RenderEvent.World> {
            if (nodes.isNullOrEmpty()) return@on
            nodes!!.forEach { it.render(ctx) }
        }

        on<KeyEvent.Input> {
            if (!active) return@on
            val curr = activeNode is ClearEtherNode
            val next = nodes?.firstOrNull() is ClearEtherNode

//            println(curr || next)

            if (curr || next) input.shift = true
        }

        on<WorldEvent.Change> {
            nodes = null
            syncDelay = 0
            compDelay = 0
            globalDelay = 0
            hypeDelay = 0
            position = null
            active = false
            activeNode = null
        }
    }

    fun etherPath(to: BlockPos, config: PathConfig = PathConfig()) {
        if (player.at(to)) return modMessage("Already there")

        scope.launch {
            val p = EtherwarpPathfinder.findDungeonPath(
                from = player.position(),
                to = to,
                config = config,
            ) ?: return@launch

            clearPath(p.map { it.toEther() })
        }
    }

    fun testPath(to: BlockPos, config: PathConfig = PathConfig()) {

    }

    fun clearPath(path: List<ClearNode>) {
        nodes = path.toMutableList()
        position = null
        pendingInteract = null
    }

    fun queueInteract(yaw: Float, pitch: Float) {
        pendingInteract = Direction(yaw, pitch)
    }

    fun cancel() {
        nodes = null
        position = null
        compDelay = 2
    }

    private fun handleQueue(playerPos: MutableVec3, clearNodes: MutableList<ClearNode>) {
        val node = clearNodes.filter { it.inside(playerPos) }.maxByOrNull { it.priority } ?: run {
            position = null
            return
        }

        active = true
        activeNode = node

        if (node is ClearHypeNode && hypeDelay > 0) return

        if (node.execute(playerPos)) {
            clearNodes.remove(node)

            if (node is ClearHypeNode) {
                hypeDelay = 3
            }

            if (clearNodes.isEmpty()) {
                cancel()
                syncDelay = 1
            }
        }
    }

    private fun doInteract() {
        pendingInteract?.let {
            player.useItem(it.yaw, it.pitch)
            pendingInteract = null
        }
    }

    private fun updateDelays() {
        if (compDelay > 0 && --compDelay == 0) {
            active = false
            activeNode = null
        }
        if (globalDelay > 0) globalDelay--
        if (hypeDelay > 0) hypeDelay--
    }

    private fun canNext(): Boolean {
        if (syncDelay != 0) return false
        if (globalDelay > 0) return false
        if (nodes.isNullOrEmpty()) return false

        if (compDelay == 0) active = false

        return true
    }
}
