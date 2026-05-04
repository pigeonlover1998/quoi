package quoi.module.impl.dungeon.autoclear.impl

import net.minecraft.world.entity.ambient.Bat
import quoi.api.autoroutes2.AutoRoutesCommand
import quoi.api.autoroutes2.RouteNode
import quoi.api.autoroutes2.RouteRegistry
import quoi.api.autoroutes2.nodes.BreakerNode
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.events.DungeonEvent
import quoi.api.events.KeyEvent
import quoi.api.events.MouseEvent
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.odonscanning.tiles.OdonRoom
import quoi.api.skyblock.invoke
import quoi.api.vec.MutableVec3
import quoi.config.ConfigMap
import quoi.config.configMap
import quoi.config.typeName
import quoi.module.Module
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.Direction
import quoi.utils.EntityUtils
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.width
import quoi.utils.equalsOneOf
import quoi.utils.mutable
import quoi.utils.render.DrawContextUtils.drawText
import quoi.utils.scaledHeight
import quoi.utils.scaledWidth
import quoi.utils.skyblock.player.EtherwarpManager
import quoi.utils.skyblock.player.PlayerUtils.useItem

object AutoRoutes : Module(
    "Auto Routes",
    area = Island.Dungeon(inClear = true),
    tag = Tag.BETA
) {
    val zeroTickDb by switch("Zero tick dungeon breaker")
    private val style by selector("Style", "Box", listOf("Box", "Filled box", "Cylinder"))
    private val thickness by slider("Thickness", 4f, 1f, 8f, 0.5f)

    private val multicolour by switch("Multicolour")
    private val colour by colourPicker("Colour", Colour.CYAN).visibleIf { !multicolour }
    private val fillColour by colourPicker("Fill colour", Colour.CYAN.withAlpha(0.5f), allowAlpha = true).visibleIf { style.selected == "Filled box" && !multicolour }
    private val activeCol by colourPicker("Active colour", Colour.WHITE)

    private val colourDropdown by text("Colours").visibleIf { multicolour }
    private val colours = RouteRegistry.nodeEntries.associate { (type, node) ->
        val set = colourPicker(type, node().colour).childOf(::colourDropdown)
        this.register(set)
        type to set
    }

    private val fillColourDropdown by text("Fill colours").visibleIf { style.selected == "Filled box" && multicolour }
    private val fillColours = RouteRegistry.nodeEntries.associate { (type, node) ->
        val set = colourPicker(type, node().colour.withAlpha(0.5f), allowAlpha = true).json("$type fill").childOf(::fillColourDropdown)
        this.register(set)
        type to set
    }

    var active = false
        private set
    var editMode = false
    var breakerRing: BreakerNode? = null
    var currentChain: String? = null

    val routeNodes: ConfigMap<String, MutableList<RouteNode>> by configMap("route_nodes.json")
    private var roomNodes: List<RouteNode> = emptyList()
    private var pendingInteract: Direction? = null
    private var position: MutableVec3? = null
    private val batIds = hashSetOf<Int>()
    private var interactDelay = 0

    private var activeNode: RouteNode? = null
    private var forcedNode: RouteNode? = null

    private var lastChainName: String? = null
    private var lastChainIndex: Int = -1

    init {
        AutoRoutesCommand.register()

        on<TickEvent.Start> {
            if (interactDelay > 0) interactDelay--

            pendingInteract?.let {
                player.useItem(it)
                pendingInteract = null
            }

            if (editMode || roomNodes.isEmpty() || EtherwarpManager.active) {
                position = null
                active = false
                return@on
            }

            if (activeNode?.hasSecretAwait == true) {
                EntityUtils.getEntities<Bat>(radius = 10.0) { it.maxHealth.equalsOneOf(100f, 200f, 400f, 800f) && it.id !in batIds }
                    .forEach { bat ->
                        batIds.add(bat.id)
                        activeNode?.onSecret()
                    }
            }

            if (position == null) {
                position = player.position().mutable()
            }

            val desynced = roomNodes.any { it.inside(player) && it.triggered }

            if (!desynced) {
                roomNodes.forEach { if (!it.inside(player)) it.triggered = false }
                position = player.position().mutable()
            }

            handleQueue(position!!)
        }

        on<KeyEvent.Input> {
            if (editMode || activeNode == null) return@on
            input.shift = activeNode!!.unsneak != true
        }

        on<MouseEvent.Click> {
            if (button != 0 || !state || active) return@on
            val pos = position ?: return@on

            val nodes = roomNodes.filter { it.inside(pos) }.sortedByDescending { it.priority }
            if (nodes.isEmpty()) return@on

            val next = (nodes.indexOf(forcedNode) + 1) % nodes.size
            val node = nodes[next]
            forcedNode = node
            activeNode = node

            if (node.execute(player, pos)) {
                node.triggered = true
                node.chain?.let {
                    lastChainName = it.name
                    lastChainIndex = it.index
                }
                position = pos
                active = false
            } else {
                node.triggered = false
                active = true
            }
        }

        on<DungeonEvent.Secret.Interact> {
            activeNode?.onSecret()
            interactDelay = 2
        }
        on<DungeonEvent.Secret.Item> { activeNode?.onSecret() }
        on<DungeonEvent.Secret.Bat> { activeNode?.onSecret() }

        on<DungeonEvent.Room.Enter> {
            batIds.clear()
            updateCache(room ?: return@on)
        }

        on<RenderEvent.World> {
            roomNodes.forEach { it.render(ctx, style.selected, it.colour(), it.fillColour(), activeCol, thickness) }
        }

        on<RenderEvent.Overlay> {
            val lines = mutableListOf<String>()
            if (editMode) lines.add("Edit mode")
            if (breakerRing != null) lines.add("&6DB Editor")
            if (currentChain != null) lines.add("&bChain: $currentChain")
            lines.forEachIndexed { i, string ->
                val x = (scaledWidth - string.noControlCodes.width()) / 2f
                val y = scaledHeight / 2f + 10 + i * 10
                ctx.drawText(string, x, y)
            }
        }

        on<WorldEvent.Change> {
            roomNodes = emptyList()
            activeNode = null
            forcedNode = null
            position = null
            pendingInteract = null
            batIds.clear()

            lastChainName = null
            lastChainIndex = -1
        }
    }

    fun handleQueue(playerPos: MutableVec3) {
        val node = roomNodes
            .filter { it.inside(playerPos) && !it.triggered }
            .filter { n ->
                val c = n.chain ?: return@filter true
                if (c.index == 0) return@filter true
                c.name == lastChainName && c.index == lastChainIndex + 1
            }
            .maxByOrNull { it.priority } ?: run {

            activeNode = null

            if (roomNodes.none { it.inside(player) && it.triggered }) {
                position = null
            }
            return
        }

        if (activeNode != node) {
            activeNode = node
        }

//        val c = node.chain
//        if (c != null && c.index > 0) {
//            if (lastChainName != c.name || lastChainIndex != c.index - 1) return
//        }


        if (interactDelay > 0) return

        if (!active && !node.checkAwaits(player)) return

        if (node.execute(player, playerPos)) {
            node.triggered = true
            node.chain?.let {
                lastChainName = it.name
                lastChainIndex = it.index
            }
            active = false
        } else {
            position = null
            active = true
        }
    }

    fun queueInteract(dir: Direction) {
        pendingInteract = dir
    }

    fun updateCache(room: OdonRoom) {
        val nodes = routeNodes[room.name]
        if (nodes.isNullOrEmpty()) return
        nodes.forEach { it.update(room) }
        roomNodes = nodes
    }

    private val RouteNode.hasSecretAwait: Boolean
        get() = this.awaits?.any { it.typeName.lowercase() == "secret" } == true

    private fun RouteNode.colour() = if (multicolour) colours[this.typeName]?.value ?: Colour.WHITE else AutoRoutes.colour
    private fun RouteNode.fillColour() = if (multicolour) fillColours[this.typeName]?.value ?: Colour.WHITE else fillColour
}