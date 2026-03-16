package quoi.module.impl.misc

import quoi.QuoiMod.scope
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.colour.Colour
import quoi.api.commands.internal.BaseCommand
import quoi.api.events.TickEvent
import quoi.module.Module
import quoi.module.settings.impl.*
import quoi.utils.ChatUtils.modMessage
import quoi.utils.EntityUtils
import quoi.utils.Scheduler.wait
import quoi.utils.StringUtils.toFixed
import quoi.utils.Ticker
import quoi.utils.rayCast
import quoi.utils.skyblock.player.AuraAction
import quoi.utils.skyblock.player.AuraManager
import quoi.utils.ticker
import quoi.utils.ui.hud.*
import quoi.utils.ui.textPair
import kotlinx.coroutines.launch
import net.minecraft.world.phys.BlockHitResult
import quoi.api.skyblock.Location
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.dungeon.DungeonClass
import quoi.api.skyblock.dungeon.map.utils.LegacyIdMapper.legacyBlockIdMap
import quoi.module.settings.impl.BooleanSetting
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.formatTime
import quoi.utils.WorldUtils
import quoi.utils.skyblock.player.ContainerUtils
import quoi.utils.skyblock.player.LeapManager
import quoi.utils.skyblock.player.PlayerUtils
import kotlin.text.replace

object Test : Module("Test", desc = "Dev module for testing.") {
    val selectedTheme2 by SelectorSetting("Theme2", "Light", listOf("Light", "Dark", "Custom"))
    val selectedTheme by SelectorSetting("Theme", "Light", listOf("Light", "Dark", "Custom"))

    val selectorTest by SelectorSetting("class", DungeonClass.Archer)

    val stringTest by StringSetting("String setting", "shit").suggests(legacyBlockIdMap.keys.map { it.replace("minecraft:", "") })

    private val showX by BooleanSetting("Show X", true)
    private val showY by BooleanSetting("Show Y", true)
    private val showZ by BooleanSetting("Show Z", true)

    private val coords = setOf(
        Data("x:", { (mc.player?.x ?: 0.0).toFixed() }, { showX }),
        Data("y:", { (mc.player?.y ?: 0.0).toFixed() }, { showY }),
        Data("z:", { (mc.player?.z ?: 0.0).toFixed() }, { showZ })
    )

    private val texthudtest by TextHud("coords") {
        column {
            coords.forEach { (name, coord, enabled) ->
                if (!enabled()) return@forEach
                textPair(
                    string = name,
                    supplier = { if (preview) 0.0 else coord() },
                    labelColour = Colour.WHITE,
                    valueColour = colour,
                    shadow = shadow
                )
            }
        }
    }.withSettings(::showX, ::showY, ::showZ)
    .setting()

    private val resizableTest by ResizableHud("resizable2", 75f, 7f, outline = Colour.RED) {
        ctxBlock(
            size(width, height),
            colour = Colour.WHITE
        ).outline(outline, thickness)
    }.setting()

    private val hypixel by BooleanSetting("Hypixel", true)
    private val inSB by BooleanSetting("In skyblock", true)
    private val lobby by BooleanSetting("Lobby", true)
//    private val inDung by BooleanSetting("In dungeon", true)
    private val area_ by BooleanSetting("Area", true)
    private val subarea_ by BooleanSetting("Subarea", true)
    private val boss by BooleanSetting("Boss", true)
    private val floor by BooleanSetting("Floor", true)
    private val p3Section by BooleanSetting("P3 section", true)
    private val p3Players by BooleanSetting("P3 players", true)
    private val container by BooleanSetting("Container", true)

    private val debugData = setOf(
        Data("Hypixel", { Location.onHypixel }, { hypixel }),
        Data("Skyblock", { Location.inSkyblock }, { inSB }),
        Data("Lobby", { Location.currentServer ?: "None" }, { lobby }),
//        Data("Dungeon", { Dungeon.inDungeons }, { inDung }),
        Data("Area", { Location.currentArea }, { area_ }),
        Data("Subarea", { Location.subarea ?: "None" }, { subarea_ }),
        Data("Boss", { Dungeon.inBoss }, { boss }),
        Data("Floor", { Dungeon.floor ?: "None" }, { floor }),
        Data("P3 Section", { "${Dungeon.p3Section.name} || ${Dungeon.getP3Section().name}" }, { p3Section }),
        Data("   Duration", { "${formatTime(Dungeon.p3Section.getDuration())} | ${formatTime(Dungeon.p3Section.getDurationTicks() * 50)}" }, { p3Section } ),
        Data("   Terminals", { "${Dungeon.p3Section.terminals}/${Dungeon.p3Section.reqTerminals}" }, { p3Section }),
        Data("   Levers", { "${Dungeon.p3Section.levers}/2" }, { p3Section }),
        Data("   Device", { "${Dungeon.p3Section.device}" }, { p3Section }),
        Data("   Gate", { Dungeon.p3Section.gate }, { p3Section }),
        Data("Container", { "${mc.screen != null} | ${ContainerUtils.containerId}" }, { container })
    )

    private val debug by TextHud("debug") {
        column {
            debugData.forEach { (name, value, enabled) ->
                if (!enabled()) return@forEach
                textPair(
                    string = "$name:",
                    supplier = { value() },
                    labelColour = Colour.WHITE,
                    valueColour = colour,
                    shadow = shadow
                )
                if (name == "   Gate" && p3Players) Dungeon.dungeonTeammates.forEach { player ->
                    textPair(
                        string = "   ${player.name}:",
                        supplier = { "${player.p3Stats.terminals}T | ${player.p3Stats.levers}L | ${player.p3Stats.devices}D" },
                        labelColour = player.colour,
                        valueColour = colour,
                        shadow = shadow
                    )
                }
            }
        }
    }.withSettings(::hypixel, ::inSB, /*::inDung,*/::lobby, ::area_, ::subarea_, ::boss, ::floor, ::p3Section, ::p3Players, ::container
    ).setting()

    private data class Data(val name: String, val value: () -> Any?, val enabled: () -> Boolean)

    private var ticker: Ticker? = null

    fun tickerExample() = ticker {
        action { mc.player?.jumpFromGround() } // jump
        await { mc.player?.onGround() == true } // wait till player is on ground
        action(20) { modMessage("This message sent exactly 20 ticks after landing.") } // send message 20 ticks later
    }

    init {
        val command = BaseCommand("quoitest")
//        command.sub("scheduletest") {
//            var start = 0
//            scheduleLoop(20) {
//                start += 20
//                modMessage("TEST $start")
//                if (start >= 100) it.cancel()
//            }
//        }

//        command.sub("tickertest") {
//            ticker = tickerExample() // set ticker
//        }

        command.sub("blockaura") {
            mc.hitResult?.let {
                if (it !is BlockHitResult) return@let
                AuraManager.auraBlock(it.blockPos)
            }
        }

        command.sub("entityaura") {
            val entity = EntityUtils.entities.filter { it != player }.minByOrNull { it.distanceTo(player) } ?: return@sub
            AuraManager.auraEntity(entity, action = AuraAction.INTERACT_AT)
        }


        command.sub("raycast") {
            val res = rayCast()
            modMessage(res)
        }

        command.sub("testshit") {
            stupid = 0
            scope.launch {
                modMessage("$stupid: shit")
                wait(5)
                modMessage("$stupid: shit2")
                wait(20)
                modMessage("$stupid: shit3")
            }
        }

        command.sub("testleap") { player: String ->
            scheduleTask(20) {
                LeapManager.leap(player)
            }
        }.suggests { WorldUtils.players.map { it.profile.name } }

        val upd = command.sub("testcommand")
        upd.sub("coords") { x: Int, y: Int, z: Int ->
            modMessage("coords: $x, $y, $z")
        }
        .suggests("x") { player.x.toInt() }
        .suggests("y") { player.y.toInt() }
        .suggests("z") { player.z.toInt() }

        upd.sub("health") { hp: Int? ->
            modMessage("health: $hp")
        }.suggests(26)

        on<TickEvent.End> {
            stupid++
            ticker?.let { // do ticker
                if (it.tick()) ticker = null
            }
        }

        command.register()
    }
    var stupid = 0
}