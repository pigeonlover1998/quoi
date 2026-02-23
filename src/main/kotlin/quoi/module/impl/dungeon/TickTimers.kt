package quoi.module.impl.dungeon

import quoi.api.abobaui.elements.impl.Text.Companion.shadow
import quoi.api.abobaui.elements.impl.Text.Companion.textSupplied
import quoi.api.events.ChatEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.api.skyblock.invoke
import quoi.module.Module
import quoi.utils.ThemeManager.theme
import quoi.module.settings.Setting.Companion.withDependency
import quoi.module.settings.impl.BooleanSetting
import quoi.utils.StringUtils.toFixed
import quoi.utils.ui.hud.Hud
import quoi.utils.ui.hud.TextHud
import quoi.utils.ui.hud.setting
import quoi.utils.ui.rendering.NVGRenderer.minecraftFont

object TickTimers : Module(
    "Tick Timers",
    area = Island.Dungeon(7, inBoss = true)
) {
    private val showInTicks by BooleanSetting("Show in ticks")

    private val padHud by TextHud("Pad tick") {
        visibleIf { padTick >= 0 }
        textSupplied(
            supplier = { formatTime(if (preview) 15 else padTick, 20) },
            size = theme.textSize,
            font = minecraftFont,
            colour = colour
        ).shadow = shadow
    }.setting()

    private val goldorHud: Hud by TextHud("Goldor death tick") {
        visibleIf { goldorStart >= 0 || goldorTick >= 0 }
        textSupplied(
            supplier = { if (goldorStart >= 0 && startTimer) formatTime(goldorStart, 104) else formatTime(if (preview) 40 else goldorTick, 60) },
            size = theme.textSize,
            font = minecraftFont,
            colour = colour
        ).shadow = shadow
    }.setting()

    private val startTimer by BooleanSetting("Goldor start timer").withDependency { goldorHud.enabled }

    private var goldorTick = -1
    private var goldorStart = -1
    private var padTick = -1

    private val goldorRegex = Regex("^\\[BOSS] Goldor: Who dares trespass into my domain\\?$")
    private val coreOpeningRegex = Regex("^The Core entrance is opening!$")
    private val stormPadRegex = Regex("^\\[BOSS] Storm: Pathetic Maxor, just like expected\\.$")

    init {
        on<WorldEvent.Change> {
            goldorTick = -1
            goldorStart = -1
            padTick = -1
        }

        on<TickEvent.Server> {
            if (!Dungeon.inBoss) return@on
            if (goldorTick == 0 && goldorStart <= 0 && goldorHud.enabled) goldorTick = 60
            if (goldorTick >= 0 && goldorHud.enabled) goldorTick--
            if (goldorStart >= 0 && goldorHud.enabled) goldorStart--
            if (padTick == 0 && padHud.enabled) padTick = 20
            if (padTick >= 0 && padHud.enabled) padTick--
        }

        on<ChatEvent.Packet> {
            when {
                goldorHud.enabled && message.matches(goldorRegex) -> goldorTick = 60
                goldorHud.enabled && message.matches(coreOpeningRegex) -> {
                    goldorStart = -1
                    goldorTick = -1
                }
//                padHud.enabled && message.matches(stormStartRegex) -> padTick = -1
                padHud.enabled && message.matches(stormPadRegex) -> padTick = 20
            }
        }
    }


    private fun formatTime(time: Int, max: Int): String {
        val col = when {
            time.toFloat() >= max * 0.66 -> "§a"
            time.toFloat() >= max * 0.33 -> "§6"
            else -> "§c"
        }
        val display = if (showInTicks) "$time" else (time / 20f).toFixed()
        return "$col$display"
    }
}