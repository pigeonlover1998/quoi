package quoi.module.impl.dungeon.autop3

import quoi.api.commands.internal.GreedyString
import quoi.api.commands.internal.SubCommand
import quoi.module.impl.dungeon.autop3.AutoP3.add
import quoi.module.impl.dungeon.autop3.AutoP3.rings
import quoi.module.impl.dungeon.autop3.rings.AlignAction
import quoi.module.impl.dungeon.autop3.rings.BoomAction
import quoi.module.impl.dungeon.autop3.rings.ChatAction
import quoi.module.impl.dungeon.autop3.rings.EdgeAction
import quoi.module.impl.dungeon.autop3.rings.FastAlignAction
import quoi.module.impl.dungeon.autop3.rings.JumpAction
import quoi.module.impl.dungeon.autop3.rings.RotateAction
import quoi.module.impl.dungeon.autop3.rings.StopAction
import quoi.module.impl.dungeon.autop3.rings.StopwatchAction
import quoi.module.impl.dungeon.autop3.rings.SwapAction
import quoi.module.impl.dungeon.autop3.rings.UseAction
import quoi.module.impl.dungeon.autop3.rings.WalkAction
import quoi.utils.ChatUtils.modMessage
import quoi.utils.player
import quoi.config.typeName
import quoi.utils.skyblock.ItemUtils.skyblockId
import kotlin.math.floor

internal fun AutoP3.registerCommands() {
    cmd.sub("em") {
        editMode = !editMode
        modMessage("Edit mode ${if (editMode) "&aenabled" else "&cdisabled"}&r!")
    }.description("Toggles edit mode.")

    cmd.sub("edit") {
        editMode = !editMode
        modMessage("Edit mode ${if (editMode) "&aenabled" else "&cdisabled"}&r!")
    }.description("Toggles edit mode.")

    cmd.sub("load") { name: String ->
        loadConfig(name)
        modMessage("Loaded config &e$name&r!")
    }.description("Loads a config.").suggests("load", { listConfigs() })

    cmd.sub("remove") { range: Double? ->
        val r = range ?: 2.0
        val ringsInRange = rings.mapIndexedNotNull { i, ring ->
            if (player.boundingBox.inflate(r).intersects(ring.boundingBox())) i to ring
            else null
        }
        if (ringsInRange.isEmpty()) return@sub modMessage("&cNo rings in range")

        ringsInRange.reversed().forEach { (index, _) -> rings.removeAt(index) }
        undoStack.add(ringsInRange)
        rings.save()
        modMessage("Removed ${ringsInRange.joinToString(", ") { "&e${it.second.action.typeName}&r" }}")
    }.description("Removes rings within distance.")
    
    cmd.sub("undo") {
        if (undoStack.isEmpty()) return@sub modMessage("&cNothing to undo.")
        
        val last = undoStack.removeLast()
        last.sortedBy { it.first }.forEach { (i, ring) ->
            if (i in 0..rings.size) {
                rings.add(i, ring)
            } else {
                rings.add(ring)
            }
        }
        rings.save()
        modMessage("Restored &e${last.joinToString("&r, &e") { it.second.action.typeName }}&r!")
    }.description("Restores last removed ring(s).")

    add.sub("walk") { args: GreedyString? -> addRing(WalkAction(currentYaw, currentPitch), args) }.suggestArgs()
    add.sub("stop") { args: GreedyString? -> addRing(StopAction(), args) }.suggestArgs()
    add.sub("align") { args: GreedyString? -> addRing(AlignAction(), args) }.suggestArgs()
    add.sub("fastalign") { args: GreedyString? -> addRing(FastAlignAction(), args) }.suggestArgs()
    add.sub("jump") { args: GreedyString? -> addRing(JumpAction(), args) }.suggestArgs()
    add.sub("edge") { args: GreedyString? -> addRing(EdgeAction(), args) }.suggestArgs()
    add.sub("rotate") { args: GreedyString? -> addRing(RotateAction(currentYaw, currentPitch), args) }.suggestArgs()
    add.sub("stopwatch") { args: GreedyString? -> addRing(StopwatchAction(), args) }.suggestArgs()
    add.sub("swap") { args: GreedyString? -> 
        val (parsedArgs, item) = parseSwapArgs(args)
        val finalItem = item.ifEmpty { player.mainHandItem.skyblockId ?: "" }
        if (finalItem.isEmpty()) {
            return@sub modMessage("&cPlease specify an item with item:ITEM_ID or hold an item")
        }
        addRing(SwapAction(finalItem), GreedyString(parsedArgs))
    }.suggestArgs()
    add.sub("use") { args: GreedyString? -> 
        val (parsedArgs, item) = parseSwapArgs(args)
        val finalItem = item.ifEmpty { player.mainHandItem.skyblockId ?: "" }
        if (finalItem.isEmpty()) {
            return@sub modMessage("&cPlease specify an item with item:ITEM_ID or hold an item")
        }
        addRing(UseAction(currentYaw, currentPitch, finalItem), GreedyString(parsedArgs))
    }.suggestArgs()
    add.sub("boom") { args: GreedyString? -> addRing(BoomAction(currentYaw, currentPitch), args) }.suggestArgs()
    add.sub("chat") { args: GreedyString? -> 
        val (parsedArgs, message) = parseChatArgs(args)
        addRing(ChatAction(message), GreedyString(parsedArgs))
    }.suggestArgs()

    cmd.register()
}

private fun AutoP3.addRing(action: quoi.module.impl.dungeon.autop3.rings.P3Action, input: GreedyString?) {
    val (args, subActionNames) = parseArgsAndSubActions(input)
    
    val subActions = subActionNames.mapNotNull { name ->
        actionEntries.find { it.first == name }?.second?.invoke()
    }

    val ring = P3Ring(
        x = if (args.exact) player.x else kotlin.math.round(player.x * 2) / 2.0,
        y = kotlin.math.round(player.y * 2) / 2.0,
        z = if (args.exact) player.z else kotlin.math.round(player.z * 2) / 2.0,
        action = action,
        subActions = subActions,
        radius = args.radius,
        height = args.height,
        delay = args.delay,
        trigger = args.trigger,
        ground = args.ground,
        term = args.term,
        termclose = args.termclose
    )
    
    ring.setTriggered()
    rings.add(ring)
    rings.save()
    val actionStr = if (subActions.isEmpty()) {
        "&e${action.typeName}&r"
    } else {
        "&e${action.typeName}&r + ${subActions.joinToString(" + ") { "&e${it.typeName}&r" }}"
    }
    val triggerStr = if (args.trigger) " &7(trigger)" else ""
    modMessage("Added $actionStr$triggerStr!")
}

private fun parseArgsAndSubActions(input: GreedyString?): Pair<P3Args, List<String>> {
    val str = input?.string?.lowercase()?.trim()
    if (str.isNullOrBlank()) return P3Args() to emptyList()

    var radius = 0.5
    var height: Double? = null
    var delay: Int? = null
    var exact = false
    var trigger = false
    var ground = false
    var term = false
    var termclose = false
    val subActions = mutableListOf<String>()

    str.split(" ").forEach { arg ->
        val parts = arg.split(":", limit = 2)
        val key = parts[0]
        val value = parts.getOrNull(1) ?: ""

        when (key) {
            "radius" -> radius = value.toDoubleOrNull() ?: 1.0
            "height" -> height = value.toDoubleOrNull()
            "delay" -> delay = value.toIntOrNull()
            "exact" -> exact = true
            "trigger" -> trigger = value.toBooleanStrictOrNull() ?: true
            "ground" -> ground = value.toBooleanStrictOrNull() ?: true
            "term" -> term = value.toBooleanStrictOrNull() ?: true
            "termclose" -> termclose = value.toBooleanStrictOrNull() ?: true
            else -> {
                if (AutoP3.actionEntries.any { it.first == key }) {
                    subActions.add(key)
                }
            }
        }
    }
    return P3Args(radius, height, delay, exact, trigger, ground, term, termclose) to subActions
}

private fun SubCommand.suggestArgs() = suggestsCtx("args") { ctx ->
    val input = ctx.input.split(" ").drop(3).joinToString(" ")
    val args = input.split(" ")
    val currentArg = args.lastOrNull().orEmpty()

    val providers = mapOf(
        "delay" to { listOf("100", "500") },
        "radius" to { listOf("2", "3.5", "4") },
        "height" to { listOf("0.1", "1", "4.1") },
        "exact" to { listOf("") },
        "trigger" to { listOf("true", "false") },
        "ground" to { listOf("true", "false") },
        "term" to { listOf("true", "false") },
        "termclose" to { listOf("true", "false") }
    )
    
    val actionNames = AutoP3.actionEntries.map { it.first }

    val parts = currentArg.split(":", limit = 2)

    if (parts.size == 2) {
        val (key, value) = parts
        val suggestions = providers[key]?.invoke() ?: emptyList()
        suggestions.filter { it.contains(value, ignoreCase = true) }.map { "$key:$it" }
    } else {
        val usedKeys = args.dropLast(1).map { it.substringBefore(":") }
        val paramKeys = providers.keys.filter { it !in usedKeys && it.startsWith(currentArg, ignoreCase = true) }
        val actionKeys = actionNames.filter { it !in usedKeys && it.startsWith(currentArg, ignoreCase = true) }
        paramKeys + actionKeys
    }
}

private data class P3Args(
    val radius: Double = 0.5,
    val height: Double? = null,
    val delay: Int? = null,
    val exact: Boolean = false,
    val trigger: Boolean = false,
    val ground: Boolean = false,
    val term: Boolean = false,
    val termclose: Boolean = false
)

private fun parseChatArgs(input: GreedyString?): Pair<String, String> {
    val str = input?.string?.trim()
    if (str.isNullOrBlank()) return "" to ""
    
    val msgIndex = str.indexOf("msg:")
    if (msgIndex == -1) return str to ""
    
    val beforeMsg = str.substring(0, msgIndex).trim()
    val afterMsgStart = str.substring(msgIndex + 4)
    
    val argPattern = Regex("""\s+(radius:|height:|delay:|exact|trigger:|ground:|term:|termclose:|\w+(?=\s|$))""")
    val nextArgMatch = argPattern.find(afterMsgStart)
    
    val message = if (nextArgMatch != null) {
        afterMsgStart.substring(0, nextArgMatch.range.first).trim()
    } else {
        afterMsgStart.trim()
    }
    
    val afterMsgArgs = if (nextArgMatch != null) {
        afterMsgStart.substring(nextArgMatch.range.first).trim()
    } else {
        ""
    }
    
    val allArgs = listOf(beforeMsg, afterMsgArgs).filter { it.isNotEmpty() }.joinToString(" ")
    
    return allArgs to message
}

private fun parseSwapArgs(input: GreedyString?): Pair<String, String> {
    val str = input?.string?.trim()
    if (str.isNullOrBlank()) return "" to ""
    
    val itemMatch = Regex("""item:([^\s]+)""").find(str)
    val item = itemMatch?.groupValues?.get(1)?.trim() ?: ""
    
    val argsWithoutItem = if (itemMatch != null) {
        str.replace(itemMatch.value, "").trim()
    } else {
        str
    }
    
    return argsWithoutItem to item
}
