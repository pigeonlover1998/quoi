package quoi.api.autoroutes

import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import quoi.api.autoroutes.actions.*
import quoi.api.autoroutes.arguments.AwaitArgument
import quoi.api.autoroutes.arguments.BlockArgument
import quoi.api.autoroutes.arguments.RingArgument
import quoi.api.commands.internal.GreedyString
import quoi.api.commands.internal.SubCommand
import quoi.api.commands.parsers.arg
import quoi.api.events.PacketEvent
import quoi.api.events.core.EventBus
import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.api.skyblock.dungeon.Dungeon.isProtectedBlock
import quoi.utils.LegacyIdMapper.legacyBlockIdMap
import quoi.config.typeName
import quoi.module.impl.dungeon.AutoRoutesLegacy
import quoi.module.impl.dungeon.AutoRoutesLegacy.add
import quoi.module.impl.dungeon.AutoRoutesLegacy.editMode
import quoi.module.impl.dungeon.AutoRoutesLegacy.routes
import quoi.utils.ChatUtils.modMessage
import quoi.utils.WorldUtils.registryName
import quoi.utils.WorldUtils.state
import quoi.utils.component1
import quoi.utils.component2
import quoi.utils.component3
import quoi.utils.rayCast
import quoi.utils.rayCastVec
import kotlin.math.floor

internal fun AutoRoutesLegacy.registerCommands() {
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

    "etherwarp".action { EtherwarpAction(yaw, pitch, rayCastVec()?.let { currentRoom?.getRelativeCoords(it) }) }
    "rotate".action    { RotateAction(yaw, pitch) }
    "boom".action      { BoomAction(yaw, pitch) }
    "start".action     { StartAction() }
    "unsneak".action   { UnSneakAction() }

    "dungeon_breaker".action(::DungeonBreakerAction) { ring ->
        editMode = true
        editDBRing(ring)
        modMessage("Do &7/route editdb&r to finish editing")
    }

    add.sub("use_item") { name: String, args: GreedyString? ->
        addRing(UseItemAction(yaw, pitch, name), args)
    }.suggests("name", "hyperion", "enderpearl", "aspectofthevoid").suggestArgs()

    ar.register()
}

private fun AutoRoutesLegacy.editDBRing(ring: RouteRing) {
    if (interactListener != null) unsubscribeDBEditor()
    breakerRing = ring

    modMessage("Dungeon breaker editor &aenabled&r.")

    interactListener = EventBus.on<PacketEvent.Sent> {
        //if (packet !is ServerboundUseItemOnPacket) return@on
        val room = currentRoom ?: return@on

        val editing = breakerRing ?: return@on
        val action = editing.action as? DungeonBreakerAction ?: return@on

        val (pos, adding) = when (packet) {
            is ServerboundUseItemOnPacket -> packet.hitResult.blockPos to false
            is ServerboundPlayerActionPacket -> {
                if (packet.action != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) return@on
                packet.pos to true
            }
            else -> return@on
        }

        if (lastClickedBlock == pos || isProtectedBlock(pos)) return@on
        lastClickedBlock = pos

        val relativePos = room.getRelativeCoords(pos)

        val minY = editing.y + player.eyeHeight
        val maxY = minY + (editing.height ?: 0.1)
        val yPos = relativePos.y.toDouble().coerceIn(minY, maxY)

        if (relativePos.distToCenterSqr(editing.x, yPos, editing.z) > 30.0)
            return@on modMessage("&cBlock is too far!")


        val blocks = action.blocks.toMutableList()

        if (adding) {
            if (blocks.contains(relativePos)) return@on
            if (blocks.size >= 20)
                return@on modMessage("&cMaximum of 20 blocks reached for this ring!")

            blocks.add(relativePos)
        } else {
            if (!blocks.remove(relativePos)) return@on
        }

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

private fun AutoRoutesLegacy.unsubscribeDBEditor() {
    interactListener?.remove()
    interactListener = null
    breakerRing = null
    lastClickedBlock = null
}

private fun AutoRoutesLegacy.addRing(action: RingAction, input: GreedyString?): RouteRing? {
    val room = currentRoom ?: return null.also { modMessage("&cNo room detected") }
    var (x, y, z) = room.getRelativeCoords(player.position())
    val args = parseArgs(input)
    val chain = if (args.chain.equals("none", true)) null else args.chain ?: currentChain

    x = floor(x) + 0.5
    z = floor(z) + 0.5

    val ring = RouteRing(
        x = x,
        y = y,
        z = z,
        action = action,
        arguments = args.arguments,
        radius = args.radius,
        height = args.height,
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

private fun AutoRoutesLegacy.editRing(ring: RouteRing, input: GreedyString?) {
    val room = currentRoom ?: return modMessage("&cUnable to get current room")
    val rings = routes[room.data.name] ?: return modMessage("${room.data.name} &chas no rings.")

    val index = rings.indexOf(ring)
    if (index == -1) return modMessage("&cCouldn't find ring in the config.")

    val newValues = parseArgs(input)
    val chain = if (newValues.chain.equals("none", true)) null else (newValues.chain ?: ring.chain)

    val updatedRing = ring.copy(
        arguments = newValues.arguments.takeIf { it.isNotEmpty() } ?: ring.arguments,
        radius = if (input?.string?.contains("radius:") == true) newValues.radius else ring.radius,
        height = if (input?.string?.contains("height:") == true) newValues.height else ring.height,
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

private fun AutoRoutesLegacy.removeRings(range: Double?, name: String? = null) {
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
        "height" to { listOf("0.1, 1, 4.1") },
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

private fun parseArgs(input: GreedyString?): RingArgs {
    val str = input?.string?.lowercase()?.trim()
    if (str.isNullOrBlank()) return RingArgs()

    val arguments = mutableListOf<RingArgument>()
    var radius = 1.0
    var height: Double? = null
    var delay: Int? = null
    var chain: String? = null
    var index: Int? = null

    str.split(" ").forEach { arg ->
        val parts = arg.split(":", limit = 2)
        val key = parts[0]
        val value = parts.getOrNull(1) ?: ""

        when (key) {
            "radius" -> radius = value.toDoubleOrNull() ?: 1.0
            "height" -> height = value.toDoubleOrNull()
            "delay" -> delay = value.toIntOrNull()
            "chain" -> chain = value
            "index" -> index = value.toIntOrNull()
            "await" -> arguments.add(AwaitArgument(value.toIntOrNull()))
            "block" -> {
                val blockPos = rayCast(distance = 999.0) ?: return@forEach modMessage("&cFailed to get block")
                val relative = currentRoom?.getRelativeCoords(blockPos) ?: return@forEach modMessage("&cFailed to get relative coords")
                val name = value.ifEmpty { blockPos.state.block.registryName }
                arguments.add(BlockArgument(name, relative))
            }
        }
    }
    return RingArgs(arguments, radius, height, delay, chain, index)
}

private fun SubCommand.withEditMode() = requires("&cEdit mode is disabled!") { editMode }

private fun String.action(action: () -> RingAction) {
    add.sub(this) { args: GreedyString? -> AutoRoutesLegacy.addRing(action(), args) }.suggestArgs()
}

private fun String.action(action: () -> RingAction, block: (RouteRing) -> Unit) {
    add.sub(this) { args: GreedyString? ->
        val ring = AutoRoutesLegacy.addRing(action(), args)
        ring?.let { block(it) }
    }.suggestArgs()
}

private data class RingArgs(
    val arguments: List<RingArgument> = emptyList(),
    val radius: Double = 1.0,
    val height: Double? = null,
    val delay: Int? = null,
    val chain: String? = null,
    val index: Int? = null,
)
