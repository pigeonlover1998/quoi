package quoi.module.impl.dungeon

import quoi.api.abobaui.dsl.bounds
import quoi.api.abobaui.elements.impl.RefreshableGroup
import quoi.api.abobaui.elements.impl.refreshableGroup
import quoi.api.colour.Colour
import quoi.api.colour.colour
import quoi.api.events.AreaEvent
import quoi.api.events.core.Priority
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon.inDungeons
import quoi.api.skyblock.dungeon.P3Section
import quoi.module.Module
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.formatTime
import quoi.utils.StringUtils.toFixed
import quoi.utils.skyblock.SplitsManager
import quoi.utils.skyblock.SplitsManager.Split
import quoi.utils.ui.hud.TextHud
import quoi.utils.ui.hud.setting
import quoi.utils.ui.textPair

object Splits : Module( // todo section split info hud, task (terms, levers, devices) times in chat
    "Splits",
    desc = "Shows timers for various phases",
    area = Island.Dungeon
) {
    private val timeElapsed by switch("Time Elapsed split") // todo
    private val bossEntry by switch("Boss Entry split")
    private val bossClear by switch("Boss Clear split")
    private val p3Sections by switch("Goldor sections")
    private val hideNotStarted by switch("Hide not started")
    private val numbersAfterDecimal by slider("Numbers after decimal", 2, 0, 5, 1, desc = "Numbers after decimal in time.")
    private val showTickTime by switch("Show tick time", desc = "Show tick-based time alongside real time.")

    private lateinit var refreshable: RefreshableGroup

    private val hud by TextHud("Splits hud", toggleable = false) {
        refreshable = refreshableGroup(bounds()) {
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
                val (time, ticks) = if (preview) {
                    val dummyTimes = listOf(69_000L, 67_000L, 5_000L, 90_000L, 0L, 0L, 420_000L)
                    val dummyTicks = listOf(6900L, 6700L, 500L, 9000L, 0L, 0L, 42000L)
                    dummyTimes.getOrElse(index) { 0L } to dummyTicks.getOrElse(index) { 0L }
                } else {
                    when (type) {
                        1 -> times.take(3).sum() to tickTimes.take(3).sum()
                        2 -> times.drop(3).dropLast(1).sum() to tickTimes.drop(3).dropLast(1).sum()
                        else -> times.getOrElse(index) { 0L } to tickTimes.getOrElse(index) { 0L }
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
                        shadow = shadow,
                        font = font
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
                        shadow = shadow,
                        font = font
                    )
                    if (i == 5 && p3Sections) {
                        P3Section.entries.forEach { section ->
                            if (section == P3Section.Unknown) return@forEach
                            textPair(
                                string = "  S${section.number}:",
                                supplier = {
                                    val time = if (preview) (12_000L * section.number) else section.getDuration()
                                    val ticks = if (preview) (1200L * section.number) else section.getDurationTicks()
                                    val formatted = formatTime(time, numbersAfterDecimal)
                                    if (showTickTime) "$formatted §7(§a${(ticks / 20f).toFixed()}§7)" else formatted
                                },
                                labelColour = Colour.MINECRAFT_YELLOW,
                                valueColour = colour { if (colour.rgb == Colour.WHITE.rgb) Colour.MINECRAFT_YELLOW.rgb else colour.rgb },
                                shadow = shadow,
                                font = font
                            ).apply {
                                operation {
                                    val terms = preview || splits.getOrNull(5)?.time != 0L
                                    element.enabled = terms && (preview || !hideNotStarted || section.startTime != 0L)
                                    false
                                }
                            }
                        }
                    }
                }

                if (bossClear) textPair(
                    string = "Boss Clear:",
                    supplier = { getTimeString(0, 2) },
                    labelColour = Colour.MINECRAFT_BLUE,
                    valueColour = colour { if (colour.rgb == Colour.WHITE.rgb) Colour.MINECRAFT_BLUE.rgb else colour.rgb },
                    shadow = shadow,
                    font = font
                ).apply {
                    operation {
                        element.enabled = !hideNotStarted || splits.getOrNull(3)?.time != 0L
                        false
                    }
                }
            }
        }
    }.withSettings(::timeElapsed, ::bossEntry, ::bossClear, ::p3Sections, ::hideNotStarted, ::numbersAfterDecimal, ::showTickTime).setting()

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
        on<AreaEvent.Main> (Priority.LOW) {
            scheduleTask(21) {
                if (::refreshable.isInitialized) refreshable.refresh()
            }
        }
    }
}