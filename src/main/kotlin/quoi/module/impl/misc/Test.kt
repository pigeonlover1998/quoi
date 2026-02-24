package quoi.module.impl.misc

import quoi.QuoiMod.scope
import quoi.api.abobaui.constraints.impl.measurements.Pixel
import quoi.api.abobaui.dsl.px
import quoi.api.abobaui.dsl.size
import quoi.api.abobaui.elements.ElementScope
import quoi.api.abobaui.elements.impl.Block.Companion.outline
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.colour.multiply
import quoi.api.colour.toHSB
import quoi.api.commands.internal.BaseCommand
import quoi.api.events.TickEvent
import quoi.module.Category
import quoi.module.Module
import quoi.module.impl.render.ClickGui
import quoi.module.settings.impl.*
import quoi.utils.ChatUtils.modMessage
import quoi.utils.EntityUtils
import quoi.utils.Scheduler.wait
import quoi.utils.StringUtils.toFixed
import quoi.utils.Ticker
import quoi.utils.rayCast
import quoi.utils.skyblock.player.Action
import quoi.utils.skyblock.player.AuraManager
import quoi.utils.ticker
import quoi.utils.ui.hud.*
import quoi.utils.ui.textPair
import kotlinx.coroutines.launch
import net.minecraft.world.phys.BlockHitResult

object Test : Module("Test", desc = "Dev module for testing.") {
    val selectedTheme2 by SelectorSetting("Theme2", "Light", listOf("Light", "Dark", "Custom"))
    val selectedTheme by SelectorSetting("Theme", "Light", listOf("Light", "Dark", "Custom"))

    private val showX by BooleanSetting("Show X", true)
    private val showY by BooleanSetting("Show Y", true)
    private val showZ by BooleanSetting("Show Z", true)

    private val coords = listOf(
        CoordsData("x:", { mc.player?.x ?: 0.0 }, { showX }),
        CoordsData("y:", { mc.player?.y ?: 0.0 }, { showY }),
        CoordsData("z:", { mc.player?.z ?: 0.0 }, { showZ })
    )

    private val texthudtest by TextHud("coords") {
        column {
            coords.forEach { (name, coord, enabled) ->
                if (!enabled()) return@forEach
                textPair(
                    string = name,
                    supplier = { if (preview) 0.0 else coord().toFixed() },
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

    private data class CoordsData(val name: String, val coord: () -> Double, val enabled: () -> Boolean)

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
            AuraManager.auraEntity(entity, action = Action.INTERACT_AT)
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