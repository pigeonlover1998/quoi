package quoi.api.autoroutes2

import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.phys.Vec3
import quoi.QuoiMod.mc
import quoi.QuoiMod.scope
import quoi.api.autoroutes2.RouteRegistry.nodeEntries
import quoi.api.autoroutes2.RouteRegistry.nodeTypes
import quoi.api.autoroutes2.awaits.RaycastAwait
import quoi.api.autoroutes2.awaits.SecretAwait
import quoi.api.autoroutes2.converters.LegacyConverter
import quoi.api.autoroutes2.converters.RsaConverter
import quoi.api.autoroutes2.nodes.BreakerNode
import quoi.api.commands.internal.BaseCommand
import quoi.api.commands.internal.GreedyString
import quoi.api.commands.internal.SubCommand
import quoi.api.commands.parsers.arg
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.EventBus
import quoi.api.events.core.EventBus.on
import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.api.skyblock.dungeon.Dungeon.inClear
import quoi.api.skyblock.dungeon.Dungeon.isProtectedBlock
import quoi.config.configPath
import quoi.config.typeName
import quoi.module.impl.dungeon.AutoRoutes
import quoi.module.impl.dungeon.AutoRoutes.breakerRing
import quoi.module.impl.dungeon.AutoRoutes.currentChain
import quoi.module.impl.dungeon.AutoRoutes.editMode
import quoi.module.impl.dungeon.AutoRoutes.routeNodes
import quoi.utils.ChatUtils
import quoi.utils.ChatUtils.command
import quoi.utils.ChatUtils.modMessage
import quoi.utils.component1
import quoi.utils.component2
import quoi.utils.component3
import quoi.utils.getEyeHeight
import java.io.File
import kotlin.math.floor

object AutoRoutesCommand {
    private val ar = BaseCommand("route")
        .requires("&cEnable the module and be in a dungeon!") { AutoRoutes.enabled && inClear && currentRoom != null }

    private inline val player get() = mc.player!!
    private inline val room get() = currentRoom!!

    private val removedNodes = mutableListOf<List<RemovedNode>>()

    private var interactListener: EventBus.EventListener? = null
    private var tickListener: EventBus.EventListener? = null
    private var lastClickedBlock: BlockPos? = null

    fun register() {
        ar.sub("em") {
            editMode = !editMode
            modMessage("Edit mode ${if (editMode) "&aenabled" else "&cdisabled"}&r!")
            unsubscribeDBEditor()
        }.description("Toggles edit mode.")

        ar.sub("add") { type: String, args: GreedyString? ->
            val factory = nodeEntries.find { it.first.equals(type, true) }?.second
                ?: return@sub modMessage("&cUnknown type: $type. Options: $nodeTypes")

            var (x, y, z) = room.getRelativeCoords(player.position())
            x = floor(x) + 0.5
            z = floor(z) + 0.5

            val args = parseArgs(type, args)

            val chainName = args.chain ?: currentChain

            var routeChain: RouteChain? = null

            if (chainName != null) {
                val nodes = routeNodes[room.name]?.filter { it.chain?.name == chainName } ?: emptyList()

                val next = if (nodes.isEmpty()) 0 else nodes.maxOf { it.chain?.index ?: 0 } + 1
                val i = (args.index ?: next).coerceAtMost(next)

                shiftChain(room.name, chainName, i, 1)
                routeChain = RouteChain(chainName, i)
            }

            val base = factory().apply {
                relative = Vec3(x, y, z)
                radius = args.radius
                height = args.height
                start = args.start
                unsneak = args.unsneak
                awaits = if (args.awaits.isEmpty()) null else args.awaits.toMutableList()
                chain = routeChain
            }

            val node = base.create(player, room) ?: return@sub modMessage("Failed blah blah blah")

            val nodes = routeNodes.getOrPut(room.name) { mutableListOf() }
            nodes.add(node)

            modMessage("Added &e$type&r!")
            save()
        }.suggests("type", nodeTypes).suggestArgs()

        ar.sub("rm") { type: String? ->
            val nodes = routeNodes[room.name]

            if (nodes.isNullOrEmpty()) return@sub modMessage("&cNo nodes in this room!")

            val lower = type?.lowercase()
            if (type != null && lower != "last" && lower !in nodeTypes) {
                return@sub modMessage("&cUnknown type &e$type&c. Options: $nodeTypes")
            }

            if (lower == "last") {
                val removed = nodes.removeAt(nodes.size - 1)
                removedNodes.add(listOf(RemovedNode(room.name, removed)))
                modMessage("Removed last node &e${removed.typeName}&r!")
                routeNodes.save()
                return@sub
            }

            val toRm = nodes.filter {
                val intersects = player.boundingBox.inflate(5.0).intersects(it.aabb)
                val matches = type == null || it.typeName.equals(type, true)

                intersects && matches
            }.minByOrNull { it.pos.distanceToSqr(player.position()) } ?: return@sub modMessage("&cNo nodes around!")

            toRm.chain?.let { shiftChain(room.name, it.name, it.index + 1, -1) }

            nodes.remove(toRm)
            removedNodes.add(listOf(RemovedNode(room.name, toRm)))
            modMessage("Removed &e${toRm.typeName}&r!")
            save()
        }.suggests("type", listOf("last") + nodeTypes)

        ar.sub("restore") {
            val entries = removedNodes.removeLastOrNull() ?: return@sub modMessage("&cNo nodes to restore!")

            entries.forEach { entry ->
                val nodes = routeNodes.getOrPut(entry.room) { mutableListOf() }
                if (entries.size == 1) {
                    entry.node.chain?.let { shiftChain(entry.room, it.name, it.index, 1) }
                }
                nodes.add(entry.node)
            }
            save()
            modMessage("Restored &e${if (entries.size == 1) entries[0].node.typeName else "${entries.size} &rnodes"}&r!")
        }

        ar.sub("clear") {
            val nodes = routeNodes[room.name] ?: return@sub modMessage("${room.name}&c has no nodes!")
            if (nodes.isEmpty()) return@sub modMessage("${room.name} &chas no nodes to clear.")

            val toRm = nodes.toList()
            removedNodes.add(toRm.map { RemovedNode(room.name, it) })

            nodes.clear()
            save()
            modMessage("Cleared &e${toRm.size}&r rings in &e${room.name}! &7(Use /route restore to undo)")
        }.description("Clears all nodes in the current room.")

//        ar.sub("edit") { args: GreedyString -> // gets shit on with chains sometimes
//            val node = routeNodes[room.name]?.firstOrNull { it.inside(player) }
//                ?: return@sub modMessage("&cYou need to stand in a ring!")
//            editNode(node, args)
//        }.description("Modifies arguments of ring you're standing in.").suggestArgs(true)

        ar.sub("edit") { type: String, args: GreedyString ->
            val node = routeNodes[room.name]?.firstOrNull { it.inside(player) && it.typeName.equals(type, true) }
                ?: return@sub modMessage("&cNo &e$type&c ring found!")
            editNode(node, args)
        }.description("Modifies arguments of ring you're standing in.").suggestArgs(true)
        .suggests("type") { routeNodes[room.name]?.filter { it.inside(player) }?.map { it.typeName } ?: emptyList<String>() }

        ar.sub("editdb") {
            if (breakerRing == null) {
                val ring = routeNodes[room.name]?.firstOrNull { it.inside(player) && it is BreakerNode }
                    ?: return@sub modMessage("&cYou need to stand in a &edungeon_breaker&c ring!")
                editDBRing(ring as BreakerNode)
            } else {
                unsubscribeDBEditor()
                modMessage("Breaker editor &cdisabled&r.")
            }
        }

        ar.sub("chain") { name: String? ->
            if (name != null) {
                currentChain = name
                modMessage("Active chain set to &e$name&r!")
            } else if (currentChain != null) {
                currentChain = null
                modMessage("Chaining &cdisabled&r.")
            } else {
                modMessage("Usage: /chain <name>")
            }
        }.description("Sets the chain for newly placed rings.")
        .suggests { routeNodes[room.name]?.mapNotNull { it.chain?.name }?.distinct() ?: emptyList<String>() }

        ar.sub("convert") { from: String -> // todo add a warning that the routes will NOT be perfect at all
            val name = when (from) {
                "legacy" -> "auto_routes"
                "rsa"    -> "rsa"
                else -> return@sub modMessage("legacy or rsa only")
            }
            val file = File(configPath, "$name.json")
            if (!file.exists()) return@sub modMessage("&cFile not found!")
            modMessage("Converting..")
            scope.launch {
                try {
                    when (from) {
                        "legacy" -> LegacyConverter.convert(file)
                        "rsa"    -> RsaConverter.convert(file)
                    }
                    modMessage("Converted. Some nodes can be bugged.")
                    save()
                } catch (e: Exception) {
                    modMessage(
                        ChatUtils.button(
                            "&cError occurred while converting! &7(click to copy)",
                            command = "/quoidev copy ${e.stackTraceToString()}",
                            hoverText = "Click to copy"
                        )
                    )
                    e.printStackTrace()
                }
            }
        }.description("Converts configs. Must have auto_routes.json or rsa.json in the config folder.")
        .suggests("from", "legacy", "rsa")

        ar.sub("reload") {
            routeNodes.reload()
            AutoRoutes.updateCache(room)
            modMessage("Reloaded")
        }

        ar.register()
    }

    private fun unsubscribeDBEditor() {
        interactListener?.remove()
        interactListener = null
        tickListener?.remove()
        tickListener = null
        breakerRing = null
        lastClickedBlock = null
    }

    private fun editDBRing(ring: BreakerNode) {
        if (interactListener != null) unsubscribeDBEditor()
        breakerRing = ring

        modMessage("Breaker editor &aenabled&r.")

        interactListener = on<PacketEvent.Sent> {
            val editing = breakerRing ?: return@on

            val (pos, adding) = when (packet) {
                is ServerboundUseItemOnPacket -> packet.hitResult.blockPos to !player.isCrouching
//                is ServerboundPlayerActionPacket if (packet.action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) -> {
//                    packet.pos to true
//                }
                else -> return@on
            }

            if (lastClickedBlock == pos || isProtectedBlock(pos)) return@on
            lastClickedBlock = pos

            val relativePos = room.getRelativeCoords(pos)

            val minY = editing.relative.y + getEyeHeight(true)
            val maxY = minY + (editing.height ?: 0.1f)
            val yPos = relativePos.y.toDouble().coerceIn(minY, maxY)

            if (relativePos.distToCenterSqr(editing.relative.x, yPos, editing.relative.z) > 30.0)
                return@on modMessage("&cBlock is too far!")


            val blocks = editing.blocks.toMutableList()

            if (adding) {
                if (blocks.contains(relativePos)) return@on
                if (blocks.size >= 20)
                    return@on modMessage("&cMaximum of 20 blocks reached for this ring!")

                blocks.add(relativePos)
            } else if (!blocks.remove(relativePos)) return@on

            editing.blocks = blocks
            breakerRing = editing
            save()
        }

        tickListener = on<TickEvent.Start> {
            lastClickedBlock = null
        }
    }

    private fun editNode(node: RouteNode, input: GreedyString?) {
        val str = input?.string?.lowercase()?.trim() ?: ""
        val new = parseArgs(node.typeName, input)

        if (str.contains("radius:")) node.radius = new.radius
        if (str.contains("height:")) node.height = new.height
        if (str.contains("start")) node.start = new.start
        if (str.contains("unsneak")) node.unsneak = new.unsneak
        if (new.awaits.isNotEmpty()) node.awaits = new.awaits.toMutableList()

        val newName = new.chain ?: node.chain?.name
        val index = str.contains("index:")
        val chain = str.contains("chain:")

        if (index || chain) { // I wanna kms
            val old = node.chain

            old?.let { shiftChain(room.name, it.name, it.index + 1, -1) }

            if (newName != null) {
                val nodes = routeNodes[room.name]?.filter { it.chain?.name == newName } ?: emptyList()

                val next = if (nodes.isEmpty()) 0 else nodes.maxOf { it.chain?.index ?: 0 } + 1

                val i = // new chain then force index to 0 else use curr/new index
                    if (chain && !index && nodes.isEmpty()) 0
                    else (new.index ?: node.chain?.index ?: next).coerceAtMost(next)

                shiftChain(room.name, newName, i, 1)
                node.chain = RouteChain(newName, i)
            } else {
                node.chain = null
            }
        }

        save()
        modMessage("Updated &e${node.typeName}&r!")
    }

    private fun parseArgs(type: String, input: GreedyString?): NodeArgs {
        val str = input?.string?.lowercase()?.trim()
        if (str.isNullOrBlank()) return NodeArgs()
        val ether = type == "etherwarp"

        var radius: Float? = null
        var height: Float? = null
        var start: Boolean? = null
        var unsneak: Boolean? = null
        var chain: String? = null
        var index: Int? = null
        val awaits = mutableListOf<RouteAwait>()

        str.split(" ").forEach { arg ->
            val parts = arg.split(":", limit = 2)
            val key = parts[0]
            val value = parts.getOrNull(1) ?: ""

            when (key) {
                "radius" ->  radius = value.toFloatOrNull()
                "height" ->  height = value.toFloatOrNull()
                "start" ->   start = true
                "unsneak" -> unsneak = !ether
                "chain" ->   chain = value
                "index" ->   index = value.toIntOrNull()
                "await" -> {
                    when (value) {
                        "raycast" if (ether) -> awaits.add(RaycastAwait())
                        else -> awaits.add(SecretAwait(value.toIntOrNull() ?: 1))
                    }
                }
            }
        }
        return NodeArgs(radius, height, start, unsneak, chain, index, awaits)
    }

    private fun SubCommand.suggestArgs(edit: Boolean = false) = suggestsCtx("args") { ctx ->
        val type = if (edit) {
            if (ctx.arg(1) in nodeTypes) ctx.arg(1) else "edit"
        } else ctx.arg(0)

        val input = ctx.input.removePrefix("/route add $type ")
        val args = input.split(" ")
        val currentArg = args.lastOrNull().orEmpty()

        val providers = mapOf(
            "radius" to { listOf("1", "0.5", "4") },
            "height" to { listOf("0.1, 1, 4.1") },
            "await" to {
                val a = listOf("2", "3", "4")
                if (type == "etherwarp") a + "raycast" else a
            },
            "chain" to { routeNodes[room.name]?.mapNotNull { it.chain?.name }?.distinct() ?: emptyList() },
            "index" to { listOf("0", "1", "2", "3") }
        )

        val flags = listOf("start")

        val parts = currentArg.split(":", limit = 2)

        if (parts.size == 2) {
            val (key, value) = parts
            val suggestions = providers[key]?.invoke() ?: emptyList()

            suggestions.filter { it.contains(value, ignoreCase = true) }.map { "$key:$it" }
        } else {
            val usedAgrs = args.dropLast(1).map { it.substringBefore(":") }
            val available = (providers.keys + flags)
            available.filter { it !in usedAgrs && it.startsWith(currentArg, ignoreCase = true) }
        }
    }

    private fun save() {
        AutoRoutes.updateCache(room)
        routeNodes.save()
    }

    private fun shiftChain(room: String, chain: String, from: Int, amount: Int) {
        routeNodes[room]?.forEach {
            val c = it.chain
            if (c != null && c.name == chain && c.index >= from) {
                c.index += amount
            }
        }
    }

    private data class RemovedNode(val room: String, val node: RouteNode)

    private data class NodeArgs(
        val radius: Float? = null,
        val height: Float? = null,
        val start: Boolean? = null,
        val unsneak: Boolean? = null,
        val chain: String? = null,
        val index: Int? = null,
        val awaits: List<RouteAwait> = emptyList()
    )

}