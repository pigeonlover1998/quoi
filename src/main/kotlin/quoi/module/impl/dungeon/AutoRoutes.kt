package quoi.module.impl.dungeon

import quoi.QuoiMod.scope
import quoi.api.autoroutes.RouteRing
import quoi.api.autoroutes.actions.*
import quoi.api.autoroutes.arguments.AwaitArgument
import quoi.api.autoroutes.arguments.BlockArgument
import quoi.api.autoroutes.arguments.RingArgument
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.commands.internal.BaseCommand
import quoi.api.commands.internal.GreedyString
import quoi.api.commands.internal.SubCommand
import quoi.api.commands.parsers.arg
import quoi.api.events.*
import quoi.api.events.core.EventBus
import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.api.skyblock.dungeon.Dungeon.isProtectedBlock
import quoi.api.skyblock.dungeon.map.utils.LegacyIdMapper.legacyBlockIdMap
import quoi.config.ConfigMap
import quoi.config.configMap
import quoi.config.typeName
import quoi.config.typedEntries
import quoi.module.Module
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.ColourSetting
import quoi.module.settings.impl.NumberSetting
import quoi.module.settings.impl.SelectorSetting
import quoi.utils.*
import quoi.utils.ChatUtils.modMessage
import quoi.utils.WorldUtils.registryName
import quoi.utils.WorldUtils.state
import quoi.utils.render.drawCylinder
import quoi.utils.render.drawFilledBox
import quoi.utils.render.drawWireFrameBox
import quoi.utils.skyblock.player.PlayerUtils.pitch
import quoi.utils.skyblock.player.PlayerUtils.yaw
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.inClear
import quoi.api.skyblock.invoke
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.DropdownSetting
import quoi.utils.ChatUtils.literal
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.width
import quoi.utils.render.DrawContextUtils.drawString
import quoi.utils.render.drawLine
import quoi.utils.render.drawStyledBox
import quoi.utils.render.drawText
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.floor

object AutoRoutes : Module( // todo maybe split it in two files // FIXME my scroll wheel is begging
    "Auto Routes",
    desc = "/route",
    area = Island.Dungeon(inClear = true)
) {
    val zeroTick by BooleanSetting("Zero tick").onValueChanged { _, new ->
        if (new) modMessage("&cVery buggy.", prefix = "[ZeroTick]")
    }
    private val style by SelectorSetting("Style", "Box", listOf("Box", "Filled box", "Cylinder"))
    private val multicolour by BooleanSetting("Multicolour")
    private val colour by ColourSetting("Colour", Colour.CYAN).withDependency { !multicolour }
    private val fillColour by ColourSetting("Fill colour", Colour.CYAN.withAlpha(0.5f), allowAlpha = true).withDependency { style.selected == "Filled box" && !multicolour }
    private val activeCol by ColourSetting("Active colour", Colour.WHITE)

    val actionEntries by lazy { typedEntries<RingAction>() }

    private val colourDropdown by DropdownSetting("Colours").collapsible().withDependency { multicolour }
    private val colours = actionEntries.associate { (name, action) ->
        val set = ColourSetting(name, action().colour).withDependency(colourDropdown) { multicolour }
        this.register(set)
        name to set
    }
    private val fillColourDropdown by DropdownSetting("Fill colours").collapsible().withDependency { style.selected == "Filled box" && multicolour }
    private val fillColours = actionEntries.associate { (name, action) ->
        val set = ColourSetting(name, action().colour.withAlpha(0.5f), allowAlpha = true).json("$name fill").withDependency(fillColourDropdown) { style.selected == "Filled box" && multicolour }
        this.register(set)
        name to set
    }
    private val thickness by NumberSetting("Thickness", 4f, 1f, 8f, 0.5f)
    val height by NumberSetting("Height", 0.1f, 0.1f, 1f, 0.1f)

    val routes: ConfigMap<String, MutableList<RouteRing>> by configMap("auto_routes.json")

    private var editMode = false
    private var currentChain: String? = null

    private val currentRings = hashSetOf<RouteRing>()
    val visitedRings = hashSetOf<RouteRing>()
    val completedChainNodes = hashSetOf<RouteRing>()
    val completedAwaits = hashSetOf<RouteRing>()

    private val awaitingRings = hashSetOf<RouteRing>()
    val batIds = hashSetOf<Int>()
    var secretsAwaited = 0

    private var currentJob: Job? = null
    private val removedRings = mutableMapOf<String, MutableList<List<Pair<Int, RouteRing>>>>()
    private val addHistory = mutableMapOf<String, MutableList<RouteRing>>()

    private val breakerCache = mutableMapOf<DungeonBreakerAction, List<Pair<BlockPos, AABB>>>()
    private var breakerRing: RouteRing? = null
    private var interactListener: EventBus.EventListener? = null
    private var lastClickedBlock: BlockPos? = null

    init {
        registerCommands()
        on<TickEvent.End> {
            val room = currentRoom ?: return@on
            val rings = routes[room.data.name] ?: return@on
            currentRings.clear()
            rings.filterTo(currentRings) { it.inside(room) }
            lastClickedBlock = null

            if (editMode || currentJob?.isActive == true) return@on

            visitedRings.retainAll(currentRings)
            awaitingRings.retainAll(currentRings)

            val prioritisedRings = rings.sortedByDescending {
                when (it.action) {
                    is DungeonBreakerAction -> 150
                    is BoomAction -> 100
                    is UseItemAction -> 50
                    else -> 0
                }
            }

            for (ring in prioritisedRings) {
                if (ring !in currentRings) continue
                if (ring in visitedRings) continue
                if (!ring.checkArgs()) continue

                if (!ring.chain.isNullOrEmpty()) {
                    val chainRings = rings.filter { it.chain == ring.chain }
                    val index = chainRings.indexOf(ring)

                    if (index == 0) {
                        val nextRings = chainRings.drop(1).toSet()
                        completedChainNodes.removeAll(nextRings)
                    } else {
                        if (ring in completedChainNodes) continue
                        val parent = chainRings[index - 1]
                        if (parent !in completedChainNodes) continue
                    }
                }

                visitedRings.add(ring)
                completedChainNodes.add(ring)
                awaitingRings.remove(ring)

                currentJob = scope.launch {
                    runCatching {
                        ring.delay?.let {
                            delay(it.toLong())
                            if (!ring.inside(room)) return@launch
                        }

                        ring.action.execute(player)
                    }.onFailure { error ->
                        if (error is CancellationException) return@launch
                        modMessage(
                            ChatUtils.button(
                                "&cError occurred while executing ring &e${ring.action.typeName} &7(click to copy)",
                                command = "/quoidev copy ${error.stackTraceToString()}",
                                hoverText = "Click to copy"
                            )
                        )
                        error.printStackTrace()
                    }
                }

                break
            }
        }

        on<RenderEvent.World> {
            val room = currentRoom ?: return@on
            val rings = routes[room.data.name] ?: return@on

            val chainPoints = mutableMapOf<String, Vec3>()
            val chainIndices = mutableMapOf<String, Int>()
            val isShitCylinder = style.selected == "Cylinder"

            rings.forEach { ring ->
                if (ring.action is StartAction) return@forEach

                val pos = room.getRealCoords(Vec3(ring.x, ring.y, ring.z))

                val col = if (ring in currentRings) activeCol else ring.colour()
                if (isShitCylinder) {
                    val r = ring.radius.toFloat() / 2
                    ctx.drawCylinder(pos, r, height, col, thickness = thickness, depth = true) // looks like fucking shit
                } else {
                    ctx.drawStyledBox(style.selected, ring.boundingBox(room), col, ring.fillColour(), thickness, depth = true)
                }

                val chain = ring.chain ?: return@forEach

                chainPoints[chain]?.let { prev ->
                    ctx.drawLine(
                        points = listOf(prev, pos),
                        colour = activeCol.withAlpha(100),
                        depth = true,
                        thickness = thickness / 2f
                    )
                }
                chainPoints[chain] = pos

                if (editMode) {
                    val index = chainIndices[chain] ?: 0
                    chainIndices[chain] = index + 1
                    ctx.drawText(
                        text = literal("$index"),
                        pos = pos.add(0.0, 0.3, 0.0),
                        scale = 1.2f,
                        depth = true
                    )
                }
            }

            rings.forEach { ring ->
                if (ring.action is StartAction) {
                    val aabb = ring.boundingBox(room).deflate(0.05)
                    ctx.drawStyledBox(style.selected, aabb, ring.colour(), ring.fillColour(), thickness, depth = false)
                    return@forEach
                }
            }


            val ringsToRender = currentRings.toMutableSet()
            breakerRing?.let { ringsToRender.add(it) }

            ringsToRender.forEach { ring ->
                val action = ring.action as? DungeonBreakerAction ?: return@forEach
                val blocks = breakerCache.getOrPut(action) {
                    action.blocks.map { rel ->
                        val realPos = room.getRealCoords(rel)
                        realPos to realPos.aabb
                    }
                }

                blocks.forEach { (pos, aabb) ->
                    if (pos.state?.isAir == true) {
                        ctx.drawWireFrameBox(aabb, Colour.RED.withAlpha(125), depth = true)
                    } else {
                        ctx.drawFilledBox(aabb, Colour.WHITE.withAlpha(125), depth = true)
                    }
                }
            }
        }

        on<RenderEvent.Overlay> {
            val lines = mutableListOf<String>()
            if (editMode) lines.add("Edit mode")
            if (breakerRing != null) lines.add("&6DB Editor")
            if (currentChain != null) lines.add("&bChain: $currentChain")
            lines.forEachIndexed { i, string ->
                val x = (scaledWidth - string.noControlCodes.width()) / 2f
                val y = scaledHeight / 2f + 10 + i * 10
                ctx.drawString(string, x, y)
            }
        }

        on<MouseEvent.Click> {
            if (button != 0) return@on

            val stupid = currentRings.filter { it in awaitingRings && it !in visitedRings }

            if (stupid.isNotEmpty()) {
                secretsAwaited = 999
                currentJob?.cancel()
            }
        }

        on<WorldEvent.Change> {
            visitedRings.clear()
            completedChainNodes.clear()
            completedAwaits.clear()
            breakerCache.clear()
            currentJob?.cancel()
            addHistory.clear()

            awaitingRings.clear()
            batIds.clear()
            secretsAwaited = 0
        }

        on<DungeonEvent.Secret.Interact> { if (awaitingRings.isNotEmpty()) secretsAwaited++ }
        on<DungeonEvent.Secret.Item> { if (awaitingRings.isNotEmpty()) secretsAwaited++ }
    }

    fun registerAwait(ring: RouteRing) {
        if (ring in awaitingRings) return

        if (awaitingRings.isEmpty()) {
            secretsAwaited = 0
            batIds.clear()
        }
        awaitingRings.add(ring)
    }

    private fun registerCommands() {
        val ar = BaseCommand("route").requires("&cEnable the module and be in a dungeon!") { enabled && inClear && currentRoom != null }

        ar.sub("em") {
            editMode = !editMode
            modMessage("Edit mode ${if (editMode) "&aenabled" else "&cdisabled"}&r!")
            unsubscribeDBEditor()
        }.description("Toggles edit mode.")

        ar.sub("chain") { name: String ->
            if (name.equals("none", ignoreCase = true)) {
                currentChain = null
                modMessage("Chaining &cdisabled&r.")
            } else {
                currentChain = name
                modMessage("Active chain set to &e$name&r!")
            }
        }.description("Sets the chain for newly placed rings. Use &7none&r to clear.")
        .suggests {
            val chains = currentRoom?.data?.name?.let {
                routes[it]?.mapNotNull { r -> r.chain }?.distinct()
            } ?: emptyList()
            listOf("none") + chains
        }

        ar.sub("remove") { range: Double? -> removeRings(range) }.description("Removes rings in range.")
        ar.sub("remove") { name: String, range: Double? -> removeRings(range, name) }
            .description("Removes rings in range by name.")
            .suggests("name", actionEntries.map { it.first })

        ar.sub("rmlast") {
            val roomName = currentRoom?.data?.name ?: return@sub modMessage("&cUnable to get current room.")
            val rings = routes[roomName] ?: return@sub modMessage("$roomName &chas no rings.")
            val history = addHistory[roomName] ?: return@sub modMessage("&cNo placement history for $roomName.")
            if (history.isEmpty()) return@sub modMessage("&cNo rings to remove.")

            val last = history.removeLast()
            val index = rings.indexOf(last)

            if (index != -1) {
                rings.removeAt(index)
                removedRings.getOrPut(roomName) { mutableListOf() }.add(listOf(index to last))

                routes.save()
                modMessage("Removed &e${last.action.typeName}&r!")
            }
        }.description("Removes last placed ring in current room.")

        ar.sub("undo") {
            val roomName = currentRoom?.data?.name ?: return@sub modMessage("&cUnable to get current room.")
            val undoStack = removedRings[roomName] ?: return@sub modMessage("&cNothing to undo.")
            if (undoStack.isEmpty()) return@sub modMessage("&cNothing to undo.")

            val last = undoStack.removeLast()

            val routeList = routes.getOrPut(roomName) { mutableListOf() }
            last.sortedBy { it.first }.forEach { (i, ring) ->
                if (i in 0..routeList.size) {
                    routeList.add(i, ring)
                } else {
                    routeList.add(ring)
                }
            }
            routes.save()
            modMessage("Restored &e${last.joinToString("&r,&e ") { it.second.action.typeName }}&r!")
        }.description("Restores last removed ring(-s).")

        ar.sub("clear") {
            val roomName = currentRoom?.data?.name ?: return@sub modMessage("&cUnable to get current room.")
            val rings = routes[roomName] ?: return@sub modMessage("$roomName &chas no routes.")

            if (rings.isEmpty()) return@sub modMessage("$roomName &chas no routes to clear.")

            val all = rings.mapIndexed { index, routeRing -> index to routeRing }
            removedRings.getOrPut(roomName) { mutableListOf() }.add(all)

            rings.clear()
            routes.save()

            modMessage("Cleared &c${all.size}&r rings in $roomName! &7(Use /route undo to restore)")
        }.description("Clears all routes in the current room.")

        ar.sub("edit") { args: GreedyString? ->
            val ring = currentRings.firstOrNull()
                ?: return@sub modMessage("&cYou need to stand in a ring!")
            editRing(ring, args)
        }.description("Modifies arguments of ring you're standing in.")
        .withEditMode().suggestArgs()

        ar.sub("edit") { name: String, args: GreedyString? ->
            val ring = currentRings.firstOrNull { it.action.typeName.equals(name, true) }
                ?: return@sub modMessage("&cNo &r$name&c ring found!")
            editRing(ring, args)
        }.description("Modifies arguments of ring you're standing in by name.")
        .withEditMode().suggestArgs()
        .suggests("name") { currentRings.map { it.action.typeName } }

        ar.sub("editdb") {
            if (breakerRing == null) {
                val ring = currentRings.firstOrNull { it.action is DungeonBreakerAction }
                    ?: return@sub modMessage("&cYou need to stand in a &rdungeon_breaker&c ring!")
                editDBRing(ring)
            } else {
                unsubscribeDBEditor()
                modMessage("Dungeon breaker editor &cdisabled&r.")
            }
        }.description("Toggles dungeon breaker ring editor.").withEditMode()

        val add = ar.sub("add")
            .description("Adds specified ring.")
            .suggests("add", actionEntries.map { it.first })

        add.sub("etherwarp") { args: GreedyString? ->
            addRing(EtherwarpAction(currentRoom!!.getRelativeYaw(player.yaw), player.pitch), args)
        }.suggestArgs()

        add.sub("rotate") { args: GreedyString? ->
            addRing(RotateAction(currentRoom!!.getRelativeYaw(player.yaw), player.pitch), args)
        }.suggestArgs()

        add.sub("boom") { args: GreedyString? ->
            addRing(BoomAction(currentRoom!!.getRelativeYaw(player.yaw), player.pitch), args)
        }.suggestArgs()

        add.sub("use_item") { name: String, args: GreedyString? ->
            addRing(UseItemAction(currentRoom!!.getRelativeYaw(player.yaw), player.pitch, name), args)
        }.suggests("name", "hyperion", "enderpearl", "aspectofthevoid").suggestArgs()

        add.sub("dungeon_breaker") { args: GreedyString? ->
            val ring = addRing(DungeonBreakerAction(), args)
            ring?.let {
                editMode = true
                editDBRing(it)
                modMessage("Do &7/route editdb&r to finish editing")
            }
        }.suggestArgs()

        add.sub("start") {
            addRing(StartAction(), null)
        }

        ar.register()
    }

    private fun editDBRing(ring: RouteRing) {
        if (interactListener != null) unsubscribeDBEditor()
        breakerRing = ring

        modMessage("Dungeon breaker editor &aenabled&r.")

        interactListener = EventBus.on<PacketEvent.Sent> {
            if (packet !is ServerboundUseItemOnPacket) return@on
            val room = currentRoom ?: return@on

            val editing = breakerRing ?: return@on
            val action = editing.action as? DungeonBreakerAction ?: return@on

            val pos = packet.hitResult.blockPos
            if (lastClickedBlock == pos || isProtectedBlock(pos)) return@on
            lastClickedBlock = pos

            val relativePos = room.getRelativeCoords(pos)

            if (relativePos.distToCenterSqr(editing.x, editing.y + player.eyeHeight, editing.z) > 25.0) // no idea if the user would be sneaking or not tbh...
                return@on modMessage("&cBlock is too far!")


            val blocks = action.blocks.toMutableList()
            val isRemoving = blocks.contains(relativePos)

            if (!isRemoving && blocks.size >= 20)
                return@on modMessage("&cMaximum of 20 blocks reached for this ring!")


            if (!blocks.remove(relativePos)) blocks.add(relativePos)

            val rings = routes[room.data.name] ?: return@on
            val index = rings.indexOf(editing)
            if (index == -1) return@on

            val updatedRing = editing.copy(action = DungeonBreakerAction(blocks))

            rings[index] = updatedRing
            routes.save()

            breakerRing = updatedRing
            currentRings.remove(editing)
            currentRings.add(updatedRing)
            breakerCache.clear()
        }
    }

    private fun unsubscribeDBEditor() {
        interactListener?.remove()
        interactListener = null
        breakerRing = null
        lastClickedBlock = null
    }

    private fun editRing(ring: RouteRing, input: GreedyString?) {
        val room = currentRoom ?: return modMessage("&cUnable to get current room")
        val rings = routes[room.data.name] ?: return modMessage("${room.data.name} &chas no rings.")

        val index = rings.indexOf(ring)
        if (index == -1) return modMessage("&cCouldn't find ring in the config.")

        val newValues = parseArgs(input)
        val chain = if (newValues.chain.equals("none", true)) null else (newValues.chain ?: ring.chain)

        val updatedRing = ring.copy(
            arguments = newValues.arguments.takeIf { it.isNotEmpty() } ?: ring.arguments,
            radius = if (input?.string?.contains("radius:") == true) newValues.radius else ring.radius,
            delay = if (input?.string?.contains("delay:") == true) newValues.delay else ring.delay,
            chain = chain
        )

        if (newValues.index != null && !chain.isNullOrEmpty()) {
            val chainRings = rings.filter { it.chain == chain }.toMutableList()
            chainRings.remove(ring)
            val targetPos = newValues.index

            rings.removeAt(index)
            if (targetPos in 0..chainRings.size) {
                val insertIndex =
                    if (targetPos == 0)
                        if (chainRings.isEmpty()) rings.size else rings.indexOf(chainRings.first())
                    else
                        rings.indexOf(chainRings[targetPos - 1]) + 1

                rings.add(insertIndex, updatedRing)
            } else {
                rings.add(updatedRing)
            }
        } else {
            rings[index] = updatedRing
        }

        addHistory[room.data.name]?.let { history ->
            val i = history.indexOf(ring)
            if (i != -1) history[i] = updatedRing
        }

        routes.save()
        currentRings.remove(ring)
        currentRings.add(updatedRing)

        modMessage("Updated &e${ring.action.typeName}&r!")
    }

    private fun removeRings(range: Double?, name: String? = null) {
        val room = currentRoom ?: return modMessage("&cUnable to get current room")
        val r = range ?: 2.0

        val current = routes[room.data.name] ?: return modMessage("${room.data.name} &chas no rings.")

        val ringsInRange = current.mapIndexedNotNull { i, ring ->
            if ((name?.let { ring.action.typeName == name } ?: true) &&
                player.boundingBox.inflate(r).intersects(ring.boundingBox(room))) {
                i to ring
            } else null
        }

        if (ringsInRange.isEmpty()) return modMessage("&cNo rings in range")

        ringsInRange.reversed().forEach { (index, _) -> current.removeAt(index) }
        removedRings.getOrPut(room.data.name) { mutableListOf() }.add(ringsInRange)

        routes.save()
        modMessage("Removed ${ringsInRange.joinToString(", ") { "&e${it.second.action.typeName}&r" }}")
    }

    private fun SubCommand.suggestArgs() = suggestsCtx("args") { ctx ->
        val input = ctx.input.removePrefix("/route add ${ctx.arg(0)} ")
        val args = input.split(" ")
        val currentArg = args.lastOrNull().orEmpty()

        val providers = mapOf(
            "delay" to { listOf("100", "500") },
            "radius" to { listOf("2", "3.5", "4") },
            "block" to { legacyBlockIdMap.keys.map { it.replace("minecraft:", "") } },
            "await" to { listOf("2", "3", "4") },
            "chain" to {
                val chains = currentRoom?.data?.name?.let {
                    routes[it]?.mapNotNull { r -> r.chain }?.distinct()
                } ?: emptyList()
                listOf("none") + chains
            },
            "index" to { listOf("0", "1", "2", "3") }
        )

        val parts = currentArg.split(":", limit = 2)

        if (parts.size == 2) {
            val (key, value) = parts
            val suggestions = providers[key]?.invoke() ?: emptyList()

            suggestions.filter { it.contains(value, ignoreCase = true) }.map { "$key:$it" }
        } else {
            val usedKeys = args.dropLast(1).map { it.substringBefore(":") }
            providers.keys.filter { it !in usedKeys && it.startsWith(currentArg, ignoreCase = true) }
        }
    }

    private fun SubCommand.withEditMode() = requires("&cEdit mode is disabled!") { editMode }

    private fun addRing(action: RingAction, input: GreedyString?): RouteRing? {
        val room = currentRoom ?: return null.also { modMessage("&cNo room detected") }
        var (x, y, z) = room.getRelativeCoords(player.position())
        val args = parseArgs(input)
        val chain = if (args.chain.equals("none", true)) null else args.chain ?: currentChain

        x = floor(x) + 0.5
        y = floor(y)
        z = floor(z) + 0.5

        val ring = RouteRing(
            x = x,
            y = y,
            z = z,
            action = action,
            arguments = args.arguments,
            radius = args.radius,
            delay = args.delay,
            chain = chain
        )

        val rings = routes.getOrPut(room.data.name) { mutableListOf() }

        if (args.index != null && !chain.isNullOrEmpty()) {
            val chainRings = rings.filter { it.chain == chain }
            val targetPos = args.index

            if (targetPos in 0..chainRings.size) {
                val insertIndex =
                    if (targetPos == 0)
                        if (chainRings.isEmpty()) rings.size else rings.indexOf(chainRings.first())
                    else
                        rings.indexOf(chainRings[targetPos - 1]) + 1

                rings.add(insertIndex, ring)
                modMessage("Inserted &e${action.typeName}&r as step &7#$targetPos&r in chain &b$chain&r!")
            } else {
                rings.add(ring)
                modMessage("Added &e${action.typeName}&r to chain &b$chain&r (index out of bounds)!")
            }
        } else {
            val standingInChainRing = currentRings.filter { it.chain != null && it.chain == chain }.maxByOrNull { rings.indexOf(it) }

            if (standingInChainRing != null) {
                val insertIndex = rings.indexOf(standingInChainRing) + 1
                rings.add(insertIndex, ring)
                modMessage("Inserted &e${action.typeName}&r after current step in chain '&b$chain&r'!")
            } else {
                rings.add(ring)
                if (chain != null) modMessage("Added &e${action.typeName}&r to chain '&b$chain&r'!")
                else modMessage("Added &e${action.typeName}&r!")
            }
        }

        addHistory.getOrPut(room.data.name) { mutableListOf() }.add(ring)

        routes.save()
        return ring
    }

    private fun parseArgs(input: GreedyString?): RingArgs {
        val str = input?.string?.lowercase()?.trim()
        if (str.isNullOrBlank()) return RingArgs()

        val arguments = mutableListOf<RingArgument>()
        var radius = 1.0
        var delay: Int? = null
        var chain: String? = null
        var index: Int? = null

        str.split(" ").forEach { arg ->
            val parts = arg.split(":", limit = 2)
            val key = parts[0]
            val value = parts.getOrNull(1) ?: ""

            when (key) {
                "radius" -> radius = value.toDoubleOrNull() ?: 1.0
                "delay" -> delay = value.toIntOrNull()
                "chain" -> chain = value
                "index" -> index = value.toIntOrNull()
                "await" -> arguments.add(AwaitArgument(value.toIntOrNull()))
                "block" -> {
                    val blockPos = rayCast(distance = 999.0) ?: return@forEach modMessage("&cFailed to get block")
                    val relative = currentRoom?.getRelativeCoords(blockPos) ?: return@forEach modMessage("&cFailed to get relative coords")
                    val name = value.ifEmpty { blockPos.state?.block?.registryName ?: return@forEach }
                    arguments.add(BlockArgument(name, relative))
                }
            }
        }
        return RingArgs(arguments, radius, delay, chain, index)
    }

    private data class RingArgs(
        val arguments: List<RingArgument> = emptyList(),
        val radius: Double = 1.0,
        val delay: Int? = null,
        val chain: String? = null,
        val index: Int? = null,
    )

    private fun RouteRing.colour() = if (multicolour) colours[this.action.typeName]?.value ?: Colour.WHITE else colour
    private fun RouteRing.fillColour() = if (multicolour) fillColours[this.action.typeName]?.value ?: Colour.WHITE else fillColour
}