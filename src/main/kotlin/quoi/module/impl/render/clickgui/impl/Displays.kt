package quoi.module.impl.render.clickgui.impl

import quoi.api.ServerInfo.averagePing
import quoi.api.ServerInfo.averageTps
import quoi.api.ServerInfo.currentPing
import quoi.api.ServerInfo.currentTps
import quoi.api.ServerInfo.medianPing
import quoi.api.colour.Colour
import quoi.api.commands.QuoiCommand.command
import quoi.module.impl.render.clickgui.ClickGui
import quoi.module.settings.Setting
import quoi.module.settings.Setting.Companion.json
import quoi.module.settings.UIComponent
import quoi.module.settings.UIComponent.Companion.childOf
import quoi.module.settings.UIComponent.Companion.visibleIf
import quoi.module.settings.group.SettingGroup
import quoi.utils.ChatUtils
import quoi.utils.StringUtils.percentColour
import quoi.utils.StringUtils.toFixed
import quoi.utils.WorldUtils.day
import quoi.utils.ui.data.Anchor
import quoi.utils.ui.elements.clock
import quoi.utils.ui.hud.impl.TextHud
import quoi.utils.ui.textPair
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Suppress("unused")
object Displays : SettingGroup(ClickGui, "Displays") {

    private val t12 = DateTimeFormatter.ofPattern("hh:mm a")
    private val t24 = DateTimeFormatter.ofPattern("HH:mm")
    private val clockType by segmented("Type", "Text", listOf("Text", "Clock²"))
    private val clockFormat by switch("Twelve hours")
    private val clockCol by colourPicker("Colour", Colour.WHITE).visibleIf { clockType.selected == "Text" }
    private val clockShadow by switch("Shadow", true).visibleIf { clockType.selected == "Text" }
    private val clockFont by segmented("Font", TextHud.HudFont.Minecraft).visibleIf { clockType.selected == "Text" }
    private val clockAnchor by selector("Anchor", Anchor.TopLeft).visibleIf { clockType.selected == "Text" }
    private val clockSpeed by slider("Speed", 0.4, 0.1, 1.0, 0.1, unit = "s").visibleIf { clockType.selected != "Text" }
    private val timeHud by hud("Time display") {
        if (clockType.selected == "Text") {
            textPair(
                string = "Time:",
                supplier = { LocalTime.now().format(if (clockFormat) t12 else t24) },
                labelColour = clockCol,
                shadow = clockShadow,
                font = clockFont.selected.get()
            )
        } else clock(clockFormat, clockSpeed)
    }.withSettings(::clockType, ::clockFormat, ::clockCol, ::clockShadow, ::clockFont, ::clockAnchor, ::clockSpeed).setting()

    private val fpsHud by textHud("Fps display") {
        textPair(
            string = "Fps:",
            supplier = { mc.fps },
            labelColour = colour,
            shadow = shadow,
            font = font
        )
    }.setting()

    private val pingType by selector("Ping type", PingType.Average)
    private val pingHud by textHud("Ping display") {
        visibleIf { !mc.isSingleplayer }
        textPair(
            string = "Ping:",
            supplier = { (if (preview) 69.420 else pingType.selected.value()).formatPing },
            labelColour = colour,
            shadow = shadow,
            font = font
        )
    }.withSettings(::pingType).setting()

    private val tpsType by selector("Tps type", TpsType.Average)
    private val tpsHud by textHud("Tps display") {
        visibleIf { !mc.isSingleplayer }
        textPair(
            string = "Tps:",
            supplier = { if (preview) 17.56f.formatTps(2) else tpsType.selected.value().formatTps(2) },
            labelColour = colour,
            shadow = shadow,
            font = font
        )
    }.withSettings(::tpsType).setting()

    private val dayHud by textHud("Day display") {
        textPair(
            string = "Day:",
            supplier = { level.day },
            labelColour = colour,
            shadow = shadow,
            font = font
        )
    }.setting()

    init {
        command.sub("tps") {
            ChatUtils.modMessage(
                "Tps: ${currentTps.formatTps()}&r, Average: ${averageTps.formatTps(2)}"
            )
        }.description("Shows tps.")

        command.sub("ping") {
            ChatUtils.modMessage("Ping: ${currentPing.formatPing}&r, Average: ${averagePing.formatPing}")
        }.description("Shows ping.")
    }

    override fun <K : Setting<T>, T> register(setting: K): K {
        if (setting.jsonName == setting.name) {
            setting.json("${component.jsonName}.${setting.name}")
        }

        module.register(setting)

        if (setting is UIComponent<*> && setting.parent == null) {
            setting.childOf(component).asParent()
        }

        return setting
    }

    private val Double.formatPing get() = "§${
        when {
            this < 50.0 -> "a"
            this < 100.0 -> "2"
            this < 150.0 -> "e"
            this < 200.0 -> "6"
            else -> "c"
        }
    }%.2f §7ms".format(this)

    private fun Float.formatTps(decimals: Int = 0) = (this - 15).percentColour(5.0) + this.toFixed(decimals)

    private enum class PingType(val value: () -> Double) {
        Average({ averagePing }),
        Current({ currentPing }),
        Median({ medianPing })
    }

    private enum class TpsType(val value: () -> Float) {
        Average({ averageTps }),
        Current({ currentTps })
    }
}