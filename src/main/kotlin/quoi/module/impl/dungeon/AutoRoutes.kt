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
import quoi.utils.StringUtils.noControlCodes
import quoi.utils.StringUtils.width
import quoi.utils.render.DrawContextUtils.drawString
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

object AutoRoutes : Module( // todo maybe split it in two files
    "Auto Routes",
    desc = "/route",
    area = Island.Dungeon(inClear = true)
) {
    val zeroTick by BooleanSetting("Zero tick")
    private val style by SelectorSetting("Style", "Box", listOf("Box", "Ellipse"))
    private val colour by ColourSetting("Colour (inactive)", Colour.MINECRAFT_AQUA)
    private val colour2 by ColourSetting("Colour (active)", Colour.WHITE)
    private val thickness by NumberSetting("Thickness", 4f, 1f, 8f, 0.5f)

    val routes: ConfigMap<String, MutableList<RouteRing>> by configMap("auto_routes.json")
    val actionEntries by lazy { typedEntries<RingAction>() }

    var editMode = false

    val currentRings = hashSetOf<RouteRing>()
    val visitedRings = hashSetOf<RouteRing>()
    val completedAwaits = hashSetOf<RouteRing>()

    val awaitingRings = hashSetOf<RouteRing>()
    val batIds = hashSetOf<Int>()
    var secretsAwaited = 0

    private var currentJob: Job? = null
    private val removedRings: MutableMap<String, MutableList<MutableList<RouteRing>>> = mutableMapOf()

    private val breakerCache = mutableMapOf<DungeonBreakerAction, List<Pair<BlockPos, AABB>>>()
    private var breakerRing: RouteRing? = null
    private var interactListener: EventBus.EventListener? = null
    private var lastClickedBlock: BlockPos? = null

    init {
        registerCommands()
        on<TickEvent.End> {
            val room = currentRoom ?: return@on
            val rings = routes[room.name] ?: return@on
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

                visitedRings.add(ring)
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
            val rings = routes[room.name] ?: return@on
            rings.forEach { ring ->
                val col = if (ring in currentRings) colour2 else colour
                val vec = room.getRealCoords(Vec3(ring.x, ring.y, ring.z))
                val r = ring.radius.toFloat() / 2
                if (style.selected == "Ellipse") ctx.drawCylinder(vec, r, 0.1f, col, thickness = thickness, depth = true) // looks like fucking shit
                else ctx.drawWireFrameBox(ring.boundingBox(room), col, thickness, depth = true)
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
            if (!editMode) return@on
            val lines = listOfNotNull("Edit mode", breakerRing?.let { "&6DB Editor" })
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
            completedAwaits.clear()
            breakerCache.clear()
            currentJob?.cancel()

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
        val ar = BaseCommand("route").requires("&cYou have to be in a dungeon!") { enabled && inClear && currentRoom != null }

        ar.sub("em") {
            editMode = !editMode
            modMessage("Edit mode ${if (editMode) "&aenabled" else "&cdisabled"}&r!")
            unsubscribeDBEditor()
        }.description("Toggles edit mode.")

        ar.sub("remove") { range: Double? -> removeRings(range) }.description("Removes rings in range.")
        ar.sub("remove") { name: String, range: Double? -> removeRings(range, name) }
            .description("Removes rings in range by name.")
            .suggests("name", actionEntries.map { it.first })

        ar.sub("rmlast") {
            val roomName = currentRoom?.name ?: return@sub modMessage("&cUnable to get current room.")
            val rings = routes[roomName] ?: return@sub modMessage("$roomName &chas no rings.")
            if (rings.isEmpty()) return@sub modMessage("$roomName &chas no rings.")

            val last = rings.removeLast()
            removedRings.getOrPut(roomName) { mutableListOf() }.add(mutableListOf(last))

            routes.save()
            modMessage("Removed &e${last.action.typeName}&r!")
        }.description("Removes last placed ring in current room.")

        ar.sub("undo") {
            val roomName = currentRoom?.name ?: return@sub modMessage("&cUnable to get current room.")
            val undoStack = removedRings[roomName] ?: return@sub modMessage("&cNothing to undo.")
            if (undoStack.isEmpty()) return@sub modMessage("&cNothing to undo.")

            val last = undoStack.removeLast()

            val routeList = routes.getOrPut(roomName) { mutableListOf() }
            routeList.addAll(last)

            routes.save()
            modMessage("Restored &e${last.joinToString("&r,&e ") { it.action.typeName }}&r!")
        }.description("Restores last removed ring(-s).")

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

        ar.register()
    }

    private fun editDBRing(ring: RouteRing) {
        if (interactListener != null) unsubscribeDBEditor()
        breakerRing = ring

        modMessage("Dungeon baker editor &aenabled&r.")

        interactListener = EventBus.on<PacketEvent.Sent> {
            if (packet !is ServerboundUseItemOnPacket) return@on
            val room = currentRoom ?: return@on

            val editing = breakerRing ?: return@on
            val action = editing.action as? DungeonBreakerAction ?: return@on

            val pos = packet.hitResult.blockPos
            if (lastClickedBlock == pos || isProtectedBlock(pos)) return@on
            lastClickedBlock = pos

            val relativePos = room.getRelativeCoords(pos)

            if (relativePos.distToCenterSqr(editing.x, editing.y, editing.z) > 25.0)
                return@on modMessage("&cBlock is too far!")


            val blocks = action.blocks.toMutableList()
            val isRemoving = blocks.contains(relativePos)

            if (!isRemoving && blocks.size >= 20)
                return@on modMessage("&cMaximum of 20 blocks reached for this ring!")


            if (!blocks.remove(relativePos)) blocks.add(relativePos)

            val rings = routes[room.name] ?: return@on
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
        val room = currentRoom ?: return modMessage("Unable to get current room")
        val rings = routes[room.name] ?: return modMessage("${room.name} has no rings")

        val index = rings.indexOf(ring)
        if (index == -1) return modMessage("&cCouldn't find ring in the config.")

        val newValues = parseArgs(input)

        val updatedRing = ring.copy(
            arguments = newValues.arguments.takeIf { it.isNotEmpty() } ?: ring.arguments,
            radius = if (input?.string?.contains("radius:") == true) newValues.radius else ring.radius,
            delay = if (input?.string?.contains("delay:") == true) newValues.delay else ring.delay
        )

        rings[index] = updatedRing
        routes.save()

        currentRings.remove(ring)
        currentRings.add(updatedRing)

        modMessage("Updated &e${ring.action.typeName}&r!")
    }

    private fun removeRings(range: Double?, name: String? = null) {
        val room = currentRoom ?: return modMessage("Unable to get current room")
        val r = range ?: 2.0

        val current = routes[room.name] ?: return modMessage("${room.name} has no rings")

        val ringsInRange = current.filter { ring ->
            (name?.let { ring.action.typeName == name } ?: true) &&
            player.boundingBox.inflate(r).intersects(ring.boundingBox(room))
        }

        if (ringsInRange.isEmpty()) return modMessage("No rings in range")

        current.removeAll(ringsInRange)
        removedRings.getOrPut(room.name) { mutableListOf() }.add(ringsInRange.toMutableList())
        routes.save()
        modMessage("Removed ${ringsInRange.joinToString(", ") { "&e${it.action.typeName}&r" }}")
    }

    private fun SubCommand.suggestArgs() = suggestsCtx("args") { ctx ->
        val input = ctx.input.removePrefix("/route add ${ctx.arg(0)} ")
        val args = input.split(" ")
        val currentArg = args.lastOrNull().orEmpty()

        val providers = mapOf(
            "delay" to { listOf("100", "500") },
            "radius" to { listOf("2", "3.5", "4") },
            "block" to { legacyBlockIdMap.keys.map { it.replace("minecraft:", "") } },
            "await" to { listOf("2", "3", "4") }
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

        x = (x * 2).roundToInt() / 2.0
        y = (y * 2).roundToInt() / 2.0
        z = (z * 2).roundToInt() / 2.0

        val ring = RouteRing(
            x = x,
            y = y,
            z = z,
            action = action,
            arguments = args.arguments,
            radius = args.radius,
            delay = args.delay
        )

        routes.getOrPut(room.name) { mutableListOf() }.add(ring)
        routes.save()
        modMessage("Added &e${action.typeName}&r!")
        return ring
    }

    private fun parseArgs(input: GreedyString?): RingArgs {
        val str = input?.string?.lowercase()?.trim()
        if (str.isNullOrBlank()) return RingArgs()

        val arguments = mutableListOf<RingArgument>()
        var radius = 1.0
        var delay: Int? = null

        str.split(" ").forEach { arg ->
            val parts = arg.split(":", limit = 2)
            val key = parts[0]
            val value = parts.getOrNull(1) ?: ""

            when (key) {
                "radius" -> radius = value.toDoubleOrNull() ?: 1.0
                "delay" -> delay = value.toIntOrNull()
                "await" -> arguments.add(AwaitArgument(value.toIntOrNull()))
                "block" -> {
                    val blockPos = rayCast(distance = 999.0) ?: return@forEach modMessage("Failed to get block")
                    val relative = currentRoom?.getRelativeCoords(blockPos) ?: return@forEach modMessage("Failed to get relative coords")
                    val name = value.ifEmpty { blockPos.state?.block?.registryName ?: return@forEach }
                    arguments.add(BlockArgument(name, relative))
                }
            }
        }
        return RingArgs(arguments, radius, delay)
    }

    private data class RingArgs(
        val arguments: List<RingArgument> = emptyList(),
        val radius: Double = 1.0,
        val delay: Int? = null
    )
}