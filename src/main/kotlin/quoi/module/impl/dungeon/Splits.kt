package quoi.module.impl.dungeon

import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.events.AreaEvent
import quoi.api.events.core.EventPriority
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.module.Module
import quoi.module.settings.impl.BooleanSetting
import quoi.module.settings.impl.NumberSetting
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.formatTime
import quoi.utils.StringUtils.toFixed
import quoi.utils.skyblock.SplitsManager
import quoi.utils.skyblock.SplitsManager.Split
import quoi.utils.ui.hud.HudManager
import quoi.utils.ui.hud.TextHud
import quoi.utils.ui.hud.setting
import quoi.utils.ui.textPair

object Splits : Module(
    "Splits",
    area = Island.Dungeon
) {
    private val timeElapsed by BooleanSetting("Time Elapsed split") // todo
    private val bossEntry by BooleanSetting("Boss Entry split")
    private val bossClear by BooleanSetting("Boss Clear split")
    private val hideNotStarted by BooleanSetting("Hide not started")
    private val numbersAfterDecimal by NumberSetting("Numbers after decimal", 2, 0, 5, 1, desc = "Numbers after decimal in time.")
    private val showTickTime by BooleanSetting("Show tick time", desc = "Show tick-based time alongside real time.")

    private val hud by TextHud("Splits hud", toggleable = false) {
        visibleIf { inDungeons }

        val splits = (if (preview) previewSplits else SplitsManager.currentSplits)

        var times = listOf<Long>()
        var tickTimes = listOf<Long>()


        if (!preview) operation {
            if (splits.isEmpty()) return@operation false
            val (t, tt) = SplitsManager.getAndUpdateSplitsTimes(splits)
            times = t
            tickTimes = tt
            false
        }

        fun getTimeString(index: Int, type: Int = 0): String {
            val time: Long
            val ticks: Long

            if (preview) {
                val dummyTimes = listOf(69_000L, 67_000L, 5_000L, 90_000L, 0L, 0L, 420_000L)
                val dummyTicks = listOf(6900L, 6700L, 500L, 9000L, 0L, 0L, 42000L)
                time = dummyTimes.getOrElse(index) { 0L }
                ticks = dummyTicks.getOrElse(index) { 0L }
            } else {
                when (type) {
                    1 -> { // boss entry
                        time = times.take(3).sum()
                        ticks = tickTimes.take(3).sum()
                    }
                    2 -> { // boss clear
                        val bossPassageTimes = times.drop(3).dropLast(1)
                        val bossPassageTicks = tickTimes.drop(3).dropLast(1)
                        time = bossPassageTimes.sum()
                        ticks = bossPassageTicks.sum()
                    }
                    else -> { // normal
                        time = times.getOrElse(index) { 0L }
                        ticks = tickTimes.getOrElse(index) { 0L }
                    }
                }
            }

            val formatted = formatTime(time, numbersAfterDecimal)
            return if (showTickTime) "$formatted §7(§a${(ticks / 20f).toFixed()}§7)" else formatted
        }

        column {
            splits.dropLast(1).forEachIndexed { i, split ->
                textPair(
                    string = "${split.name}:",
                    supplier = { getTimeString(i) },
                    labelColour = split.colour,
                    valueColour = colour { if (colour.rgb == Colour.WHITE.rgb) split.colour.rgb else colour.rgb },
                    shadow = shadow
                ).apply {
                    operation {
                        element.enabled = !hideNotStarted || split.time != 0L
                        false
                    }
                }

                if (i == 2 && bossEntry) textPair(
                    string = "Boss Entry:",
                    supplier = { getTimeString(0, 1) },
                    labelColour = Colour.MINECRAFT_BLUE,
                    valueColour = colour { if (colour.rgb == Colour.WHITE.rgb) Colour.MINECRAFT_BLUE.rgb else colour.rgb },
                    shadow = shadow
                )
            }

            if (bossClear) textPair(
                string = "Boss Clear:",
                supplier = { getTimeString(0, 2) },
                labelColour = Colour.MINECRAFT_BLUE,
                valueColour = colour { if (colour.rgb == Colour.WHITE.rgb) Colour.MINECRAFT_BLUE.rgb else colour.rgb },
                shadow = shadow
            ).apply {
                operation {
                    val firstBossSplit = splits.getOrNull(3)
                    val hasStarted = firstBossSplit != null && firstBossSplit.time != 0L
                    element.enabled = !hideNotStarted || hasStarted
                    false
                }
            }
        }
    }.withSettings(::timeElapsed, ::bossEntry, ::bossClear, ::hideNotStarted, ::numbersAfterDecimal, ::showTickTime).setting()

    private val previewSplits = listOf(
        Split(Regex(""), "Blood Open", Colour.MINECRAFT_DARK_RED, 1L),
        Split(Regex(""), "Watcher Clear", Colour.MINECRAFT_RED, 1L),
        Split(Regex(""), "Portal", Colour.MINECRAFT_LIGHT_PURPLE, 1L),
        Split(Regex(""), "Maxor", Colour.MINECRAFT_AQUA, 1L),
        Split(Regex(""), "Storm", Colour.MINECRAFT_RED, 0L),
        Split(Regex(""), "Terminals", Colour.MINECRAFT_YELLOW, 0L),
        Split(Regex(""), "Time Elapsed", Colour.MINECRAFT_GREEN, 1L)
    )

    init {
        on<AreaEvent.Main> (EventPriority.LOW) {
            scheduleTask(21) {
                HudManager.reinit()
            }
        }
    }


}