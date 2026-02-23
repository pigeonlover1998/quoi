package quoi.module.impl.mining

import quoi.api.abobaui.dsl.*
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.colour.Colour
import quoi.api.commands.internal.BaseCommand
import quoi.api.commands.internal.GreedyString
import quoi.api.commands.parsers.arg
import quoi.api.events.AreaEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.Location.currentArea
import quoi.api.skyblock.Location.currentServer
import quoi.config.Config
import quoi.config.configList
import quoi.module.Module
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.ListSetting
import quoi.utils.ChatUtils.modMessage
import quoi.utils.StringUtils.formatNumber
import quoi.utils.StringUtils.formatTime
import quoi.utils.StringUtils.parseNumber
import quoi.utils.WorldUtils
import quoi.utils.ui.hud.HudManager
import quoi.utils.ui.hud.TextHud
import quoi.utils.ui.hud.setting
import quoi.utils.ui.rendering.NVGRenderer.minecraftFont
import quoi.utils.ui.textPair

object GrieferTracker : Module(
    "Griefer Tracker",
    area = Island.CrystalHollows
) {

    private val chatBorder by BooleanSetting("Use chat border", desc = "Enables the border around tracker messages.")

    private val extraInfo by BooleanSetting("Show extra info", desc = "Shows more information in the hud.")
    private val showMet by BooleanSetting("Show times met").withDependency { extraInfo }
    private val showTime by BooleanSetting("Show time").withDependency { extraInfo }
    private val showServer by BooleanSetting("Show server").withDependency { extraInfo }
    private val showMacro by BooleanSetting("Show macro").withDependency { extraInfo }
    private val showPaid by BooleanSetting("Show paid amount").withDependency { extraInfo }

    private val griefedPlayersTemp by ListSetting("Griefed players", mutableListOf<GriefedPlayer>()) // todo remove later
    private val griefedPlayers by configList<GriefedPlayer>("griefed_players.json")
    private val dontGrief by ListSetting("Do not grief", mutableListOf("Morph213", "UgduBugdu", "ShortNotice"))

    private val hud by TextHud("Tracker hud", toggleable = false) {
        visibleIf { currentArea == Island.CrystalHollows }
        column {
            stupid.forEach { (text, supplier) ->
                textPair(
                    string = "$text:",
                    supplier = supplier,
                    labelColour = Colour.WHITE,
                    valueColour = colour,
                    shadow = shadow
                )
            }

            if (!extraInfo) return@column

            val list = griefedPlayers.reversed().take(5)

            if (list.isEmpty()) text(
                string = "No griefed players yet.",
                font = minecraftFont,
                size = 18.px,
                colour = Colour.MINECRAFT_GRAY
            ).shadow = shadow
            if (list.isNotEmpty()) row(gap = 7.5.px) {

                val columns = listOfNotNull<Pair<Colour, (GriefedPlayer) -> String>>(
                    Colour.MINECRAFT_GREEN to { it.name },
                    if (showMet) Colour.MINECRAFT_AQUA to { "${it.timesMet}x" } else null,
                    if (showTime) Colour.MINECRAFT_YELLOW to { formatTime(System.currentTimeMillis() - it.lastMetTime, 0, showSeconds = false) } else null,
                    if (showServer) Colour.MINECRAFT_GRAY to { it.lastServer } else null,
                    if (showMacro) Colour.MINECRAFT_RED to { if (it.macro) "§a✔" else "§c✕" } else null,
                    if (showPaid) Colour.MINECRAFT_GOLD to { if (it.paid > 0) "$${formatNumber(it.paid.toString())}" else "§7Unpaid" } else null
                )

                val i = columns.size
                columns.forEachIndexed { index, (colour, text) ->
                    tableColumn(colour, shadow, list, text)
                    if (index < i - 1) separator(list.size)
                }
            }
        }
    }.withSettings(::extraInfo, ::showMet, ::showTime, ::showServer, ::showMacro, ::showPaid).setting()

    private val grieferPro = BaseCommand("grieferpro", "gp")

    init {
        grieferPro.sub("add") { name: String, paid: String, macro: Boolean, note: GreedyString? ->
            if (griefedPlayers.any { it.name.equals(name, true) }) return@sub modMessage("&cThis player is already added!")
            var uuid = WorldUtils.players.firstOrNull { it.profile.name.equals(name, true) }?.profile?.id?.toString()
            val now = System.currentTimeMillis()
            griefedPlayers.add(
                GriefedPlayer(
                    name = name,
                    uuid = uuid ?: "err getting",
                    note = note?.string,
                    dateAdded = now,
                    lastMetTime = now,
                    timesMet = 1,
                    lastServer = currentServer ?: "err getting",
                    macro = macro,
                    paid = parseNumber(paid),
                    paidDate = now
                )
            )
            Config.save()
            HudManager.reinit()

            val uuidMsg = if (uuid == null) " &cFailed to fetch player UUID!" else ""
            val serverMsg = if (currentServer == null) " &cFailed to fetch last server!" else ""

            modMessage("&aSuccessfully added &r$name&a to the list!$uuidMsg$serverMsg")
        }.description("Adds the player to the list.")
        .suggests("name") { WorldUtils.players.map { it.profile.name } }
        .suggests("paid", "0m", "5m", "10m", "15m")
        .suggests("macro", "true", "false")
        .suggests("note", "afk", "black")

        grieferPro.sub("update") { name: String, field: String, value: GreedyString ->
            val i = griefedPlayers.indexOfFirst { it.name.equals(name, true) }
            if (i == -1) return@sub modMessage("&cThis player is not on the list!")

            val old = griefedPlayers[i]

            val str = value.string

            when (field.lowercase()) {
                "paid" -> {
                    griefedPlayers[i] = old.copy(
                        paid = parseNumber(str),
                        paidDate = System.currentTimeMillis()
                    )
                }

                "note" -> griefedPlayers[i] = old.copy(note = str)

                "macro" -> griefedPlayers[i] = old.copy(
                    macro = str.toBooleanStrictOrNull()
                        ?: return@sub  modMessage("&cInvalid value! Use true or false.")
                )

                "timesmet" -> griefedPlayers[i] = old.copy(
                    timesMet = old.timesMet + (str.toIntOrNull()
                        ?: return@sub modMessage("&cInvalid value! Use a number."))
                )

                else -> return@sub modMessage("&cInvalid field!")
            }

            Config.save()
            HudManager.reinit()
            modMessage("&aUpdated &r$name&a's &e$field&a to &7$value&a!")
        }
        .suggests("name") { griefedPlayers.map { it.name } }
        .suggests("field", "paid", "note", "macro", "timesMet")
        .suggestsCtx("value") { ctx ->
            when (ctx.arg(1)) { // field
                "paid" -> listOf("0m", "5m", "10m", "15m")
                "macro" -> listOf("true", "false")
                "note" -> listOf("afk", "black")
                else -> emptyList()
            }
        }

        grieferPro.sub("remove") { name: String ->
            val index = griefedPlayers.indexOfFirst { it.name.equals(name, true) }
            if (index == -1) return@sub modMessage("&cThis player is not on the list!")

            griefedPlayers.removeAt(index)
            Config.save()
            HudManager.reinit()
            modMessage("&aSuccessfully removed &r$name&a from the list!")
        }.suggests("name") { griefedPlayers.map { it.name } }

        grieferPro.sub("get") { name: String ->
            val index = griefedPlayers.indexOfFirst { it.name.equals(name, true) }
            if (index == -1) return@sub modMessage("&cThis player is not on the list!")

            modMessage(griefedPlayers[index].info, prefix = "", id = "gp get".hashCode())
        }.suggests("name") { griefedPlayers.map { it.name } }

        grieferPro.sub("list") {
            if (griefedPlayers.isEmpty()) return@sub modMessage("&eNo players have been griefed yet.")

            griefedPlayers.forEach {
                modMessage(it.info, prefix = "", id = "gp list".hashCode())
            }
        }

        grieferPro.sub("donotgrief") { action: String, name: String ->
            val index = dontGrief.indexOfFirst { it.equals(name, true) }
            when(action.lowercase()) {
                "add" -> {
                    if (index != -1) return@sub modMessage("&cThis player is already on the list!")
                    dontGrief.add(name)
                }
                "remove" -> {
                    if (index == -1) return@sub modMessage("&cThis player is not on the list!")
                    dontGrief.remove(name)
                }
                else -> return@sub modMessage("&cInvalid action!")
            }
            Config.save()

            val (stupid1, stupid2) = if (action.equals("add", true)) "added" to "to" else "removed" to "from"
            modMessage("&aSuccessfully $stupid1 &r$name&a $stupid2 the do not grief list!")
        }
        .suggests("action", "add", "remove")
        .suggestsCtx("name") { ctx ->
            when(ctx.arg(0)) {
                "add" -> WorldUtils.players.map { it.profile.name }
                "remove" -> dontGrief
                else -> emptyList()
            }
        }

        grieferPro.register()

        on<AreaEvent.Main> {
            if (area?.isArea(Island.CrystalHollows) == false) return@on
            val found = WorldUtils.players.filter { p -> griefedPlayers.any { it.name.equals(p.profile.name, true) } }
            if (found.isNotEmpty()) {
                val stupid = found.joinToString { p ->
                    val g = griefedPlayers.first { it.name.equals(p.profile.name, true) }
                    val c =
                        if (g.paidDate > 7 * 24 * 60 * 60 * 1000 + System.currentTimeMillis() && g.paid > 0) "e"
                        else if (g.paid > 0) "a"
                        else "c"
                    "&$c${g.name}"
                }
                if (chatBorder) { modMessage("&7&m----------------------------------------&r", prefix = "", id = "gp border 1".hashCode()) }
                modMessage("&aGriefed players detected: $stupid")
                if (chatBorder) { modMessage("&7&m----------------------------------------&r", prefix = "", id = "gp border 2".hashCode()) }

                found.forEach { p ->
                    val index = griefedPlayers.indexOfFirst { it.name.equals(p.profile.name, true) } // it.uuid.equals(p.profile.id.toString(), true)
                    if (index != -1) {
                        val old = griefedPlayers[index]
                        griefedPlayers[index] = old.copy(
                            lastMetTime = System.currentTimeMillis(),
                            timesMet = old.timesMet + 1,
                            lastServer = currentServer ?: old.lastServer
                        )
                        Config.save()
                    }
                }
            }
            val found2 = WorldUtils.players.filter { p -> dontGrief.any { it.equals(p.profile.name, true) } }

            if (found2.isNotEmpty()) {
                val stupid2 = found2.joinToString { p -> "&7${p.profile.name}" }

                if (chatBorder) { modMessage("&7&m----------------------------------------&r", prefix = "", id = "gp border 3".hashCode()) }
                modMessage("&cDO NOT GRIEF: $stupid2")
                if (chatBorder) { modMessage("&7&m----------------------------------------&r", prefix = "", id = "gp border 4".hashCode()) }
            }
        }
    }

    private val GriefedPlayer.info get() = buildString {
        if (chatBorder) appendLine("&7&m----------------------------------------&r")
        appendLine("&a| Player: &r${name}")
        appendLine("&b| Met: &r${timesMet}x")
        appendLine("&e| Last: &r${formatTime(System.currentTimeMillis() - lastMetTime, 0)}")
        appendLine("&7| Server: &r${lastServer}")
        appendLine("&c| Macro: &r${if (macro) "&aYes" else "&cNo"}")
        appendLine("&6| Paid: &f$${formatNumber(paid.toString())}")
        appendLine("&d| Note: &r${note.toString()}")
        if (chatBorder) append("&7&m----------------------------------------&r")
    }


    private val stupid = mapOf(
        "§fTotal" to { griefedPlayers.size },
        "§bEncounters" to { griefedPlayers.sumOf { it.timesMet } },
        "§cMacroers" to { griefedPlayers.sumOf { if (it.macro) 1 else 0 } },
//        "§eLast" to { "${griefedPlayers.lastOrNull()?.name} (${griefedPlayers.maxByOrNull { it.lastMetTime }?.lastMetTime?.let { formatTime(System.currentTimeMillis() - it) }} ago)" },
        "§6Earnt" to { "§6$${formatNumber(griefedPlayers.sumOf { it.paid }.toString())}" },
    )

    data class GriefedPlayer(
        val name: String, // ign
        val uuid: String, // uuid
        val note: String?, // note about the player
        val dateAdded: Long, // when was it added
        val lastMetTime: Long,
        val timesMet: Int, // how many times was he met/griefed
        val lastServer: String, // the server he was seen last time
        val macro: Boolean, // was he macroing
        val paid: Double, // how much he paid
        val paidDate: Long // when did he pay
    )

    inline fun ElementScope<*>.tableColumn(
        colour: Colour,
        shadow: Boolean,
        players: List<GriefedPlayer>,
        crossinline stat: (GriefedPlayer) -> String
    ) = column {
        players.forEach { player ->
            textSupplied(
                supplier = { stat(player) },
                font = minecraftFont,
                size = 18.px,
                colour = colour,
            ).shadow = shadow
        }
    }

    fun ElementScope<*>.separator(i: Int) = column {
        repeat(i) {
            text(
                string = "|",
                font = minecraftFont,
                size = 18.px,
                colour = Colour.MINECRAFT_GRAY,
            )
        }
    }

    fun getPlayers(): List<GriefedPlayer> = griefedPlayers
}