package quoi.module.impl.misc

import kotlinx.coroutines.launch
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.phys.BlockHitResult
import quoi.QuoiMod.scope
import quoi.api.abobaui.dsl.radius
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.colour.Colour
import quoi.api.colour.withAlpha
import quoi.api.commands.internal.BaseCommand
import quoi.api.events.RenderEvent
import quoi.api.events.TickEvent
import quoi.api.skyblock.Location
import quoi.api.skyblock.dungeon.Dungeon
import quoi.module.Module
import quoi.module.impl.render.ClickGui
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.utils.*
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.Scheduler.wait
import quoi.utils.StringUtils.formatTime
import quoi.utils.StringUtils.toFixed
import quoi.utils.render.DrawContextUtils.drawText
import quoi.utils.skyblock.player.AuraAction
import quoi.utils.skyblock.player.AuraManager
import quoi.utils.skyblock.player.ContainerUtils
import quoi.utils.skyblock.player.LeapManager
import quoi.utils.ui.hud.ResizableHud
import quoi.utils.ui.hud.TextHud
import quoi.utils.ui.hud.impl.TextHud
import quoi.utils.ui.hud.setting
import quoi.utils.ui.rendering.NVGRenderer
import quoi.utils.ui.rendering.NVGSpecialRenderer
import quoi.utils.ui.textPair

object Test : Module("Test", desc = "Dev module for testing.") {

//    val boolBigP by switch("Bool big P")
//
//    val boolGranny by switch("Bool granny").visibleIf { boolBigP }
//    val bool1 by switch("Bool papa").childOf(::boolGranny)
//    val boolMama by switch("Bool mama").childOf(::boolGranny)
//    val bool2 by switch("Bool kid").childOf(::boolMama)
//
//    val style by selector("Style", "Box", listOf("Box", "Filled box"))
//    val outline by switch("Outline").childOf(::style) { it.index == 1 }
//    val colour by colourPicker("Colour2", Colour.WHITE, true).childOf(::style) { it.index == 0 || (outline && it.index != 0) }
//    val fillColour by colourPicker("Fill colour", Colour.WHITE, true).childOf(::style) { it.index == 1 }

    private val segmented by segmented("Segmented", "1", listOf("1", "2", "3"))
    private val segmented2 by segmented("Segmented enum", TextHud.HudFont.Minecraft)

    val uiDebug by switch("UI debug").onValueChanged { old, new -> ClickGui.reopen() }
    val reopen by button("Reopen") { ClickGui.reopen() }



//    val selectedTheme2 by SelectorSetting("Theme2", "Light", listOf("Light", "Dark", "Custom"))
//    val selectedTheme by SelectorSetting("Theme", "Light", listOf("Light", "Dark", "Custom"))
//
//    val selectorTest by SelectorSetting("class", DungeonClass.Archer)
//
//    val stringTest by StringSetting("String setting", "shit").suggests(legacyBlockIdMap.keys.map { it.replace("minecraft:", "") })

    private val showX by switch("Show X", true)
    private val showY by switch("Show Y", true)
    private val showZ by switch("Show Z", true)

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
                    shadow = shadow,
                    font = font,
                )
            }
        }
    }.withSettings(::showX, ::showY, ::showZ)
    .setting()

    private val resizableTest by ResizableHud("resizable2", 75f, 7f, outline = Colour.RED) {
        block(
            size(width, height),
            colour = colour,
            5.radius()
        ).outline(outline, thickness)
    }.setting()

    private val hypixel by switch("Hypixel", true)
    private val inSB by switch("In skyblock", true)
    private val lobby by switch("Lobby", true)
//    private val inDung by BooleanSetting("In dungeon", true)
    private val area_ by switch("Area", true)
    private val subarea_ by switch("Subarea", true)
    private val boss by switch("Boss", true)
    private val floor by switch("Floor", true)
    private val p3Section by switch("P3 section", true)
    private val p3Players by switch("P3 players", true)
    private val container by switch("Container", true)

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
                    shadow = shadow,
                    font = font
                )
                if (name == "   Gate" && p3Players) Dungeon.dungeonTeammates.forEach { player ->
                    textPair(
                        string = "   ${player.name}:",
                        supplier = { "${player.p3Stats.terminals}T | ${player.p3Stats.levers}L | ${player.p3Stats.devices}D" },
                        labelColour = player.colour,
                        valueColour = colour,
                        shadow = shadow,
                        font = font
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