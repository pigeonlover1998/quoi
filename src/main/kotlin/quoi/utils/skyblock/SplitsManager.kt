package quoi.utils.skyblock

import quoi.api.colour.Colour
import quoi.api.events.AreaEvent
import quoi.api.events.ChatEvent
import quoi.api.events.TickEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventBus.on
import quoi.api.skyblock.Island
import quoi.api.skyblock.dungeon.Dungeon
import quoi.utils.Scheduler.scheduleTask
import quoi.utils.StringUtils.noControlCodes

/**
 * modified OdinFabric (BSD 3-Clause)
 * copyright (c) 2025-2026 odtheking
 * original: https://github.com/odtheking/OdinFabric/blob/main/src/main/kotlin/com/odtheking/odin/utils/skyblock/SplitsManager.kt
 */
object SplitsManager {

    var currentSplits: List<Split> = emptyList()
    private var tickCounter: Long = 0L

    fun init() {
        on<ChatEvent.Packet> {
            if (currentSplits.isEmpty()) return@on

            val split = currentSplits.firstOrNull { it.time == 0L && it.regex.matches(message.noControlCodes) } ?: return@on

            split.time = System.currentTimeMillis()
            split.ticks = tickCounter

            if (currentSplits.last() === split) {
                val (times, _) = getAndUpdateSplitsTimes(currentSplits)
                val capturedSplits = currentSplits.toList()

                scheduleTask(10) {
                    if (capturedSplits.isEmpty()) return@scheduleTask
                    // send splits here
                }
            }
        }

        on<TickEvent.Server> {
            tickCounter++
        }

        on<WorldEvent.Load.Start> {
            currentSplits = emptyList()
            tickCounter = 0L
        }

        on<AreaEvent.Main> {
            if (area != Island.Dungeon) {
                currentSplits = emptyList()
                return@on
            }
            scheduleTask(20) {
                val floor = Dungeon.floor?.floorNumber ?: return@scheduleTask
                val floorSplits = floorSplits.getOrNull(floor)?.toMutableList() ?: return@scheduleTask

                tickCounter = 0L

                val fullList = ArrayList<Split>(floorSplits.size + 4)
                fullList.addAll(startSplits.map { it.copy() })
                fullList.addAll(floorSplits)
                fullList.add(Split(TOTAL_REGEX, "Time Elapsed", Colour.MINECRAFT_GREEN))

                currentSplits = fullList
            }
        }
    }

    fun getAndUpdateSplitsTimes(splits: List<Split>): Pair<List<Long>, List<Long>> { // times, tickTimes
        if (splits.isEmpty() || splits[0].time == 0L)
            return Pair(emptyList(), emptyList())

        val size = splits.size
        val lastSplit = splits.last()

        val latestTime = if (lastSplit.time == 0L) System.currentTimeMillis() else lastSplit.time
        val latestTick = if (lastSplit.ticks == 0L) tickCounter else lastSplit.ticks

        val times = LongArray(size)
        val tickTimes = LongArray(size)

        times[size - 1] = latestTime - splits[0].time
        tickTimes[size - 1] = latestTick - splits[0].ticks


        for (i in 0 until size - 1) {
            val current = splits[i]
            val next = splits[i + 1]

            if (next.time != 0L) {
                times[i] = next.time - current.time
                tickTimes[i] = next.ticks - current.ticks
            } else {
                times[i] = latestTime - current.time
                tickTimes[i] = latestTick - current.ticks
                break
            }
        }
        return times.toList() to tickTimes.toList()
    }

    data class Split(val regex: Regex, val name: String, val colour: Colour, var time: Long = 0L, var ticks: Long = 0L)

    private val entryRegexes = listOf(
        Regex("^\\[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable\\.$"),
        Regex("^\\[BOSS] Scarf: This is where the journey ends for you, Adventurers\\.$"),
        Regex("^\\[BOSS] The Professor: I was burdened with terrible news recently\\.\\.\\.$"),
        Regex("^\\[BOSS] Thorn: Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!$"),
        Regex("^\\[BOSS] Livid: Welcome, you've arrived right on time\\. I am Livid, the Master of Shadows\\.$"),
        Regex("^\\[BOSS] Sadan: So you made it all the way here\\.\\.\\. Now you wish to defy me\\? Sadan\\?!$"),
        Regex("^\\[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!$")
    )

    private val BLOOD_OPEN_REGEX = Regex("^\\[BOSS] The Watcher: (Congratulations, you made it through the Entrance\\.|Ah, you've finally arrived\\.|Ah, we meet again\\.\\.\\.|So you made it this far\\.\\.\\. interesting\\.|You've managed to scratch and claw your way here, eh\\?|I'm starting to get tired of seeing you around here\\.\\.\\.|Oh\\.\\. hello\\?|Things feel a little more roomy now, eh\\?)$|^The BLOOD DOOR has been opened!$")
    private val MORT_REGEX = Regex("\\[NPC] Mort: Here, I found this map when I first entered the dungeon\\.|\\[NPC] Mort: Right-click the Orb for spells, and Left-click \\(or Drop\\) to use your Ultimate!")
    private val PORTAL_REGEX = Regex("\\[BOSS] The Watcher: You have proven yourself\\. You may pass\\.")
    private val TOTAL_REGEX = Regex("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+?)\\s*(\\(NEW RECORD!\\))?$")

    private val startSplits = listOf(
        Split(MORT_REGEX, "Blood Open", Colour.MINECRAFT_DARK_RED),
        Split(BLOOD_OPEN_REGEX, "Watcher Clear", Colour.MINECRAFT_RED),
        Split(PORTAL_REGEX, "Portal", Colour.MINECRAFT_LIGHT_PURPLE)
    )

    private val floorSplits = listOf(
        // Entrance
        listOf(),

        // F1
        listOf(
            Split(entryRegexes[0], "Bonzo's Sike", Colour.MINECRAFT_GREEN),
            Split(Regex("\\[BOSS] Bonzo: Oh I'm dead!"), "Bonzo's Last Act", Colour.MINECRAFT_RED)
        ),

        // F2
        listOf(
            Split(entryRegexes[1], "Scarf's minions", Colour.MINECRAFT_GREEN),
            Split(Regex("^\\[BOSS] Scarf: Did you forget\\? I was taught by the best! Let's dance\\.$"), "Scarf", Colour.MINECRAFT_RED)
        ),

        // F3
        listOf(
            Split(entryRegexes[2], "Guardians", Colour.MINECRAFT_GREEN),
            Split(Regex("^\\[BOSS] The Professor: Oh\\? You found my Guardians' one weakness\\?$"), "The Professor", Colour.MINECRAFT_RED),
            Split(Regex("^\\[BOSS] The Professor: What\\?! My Guardian power is unbeatable!$"), "Cleared", Colour.MINECRAFT_DARK_GREEN)
        ),

        // F4
        listOf(
            Split(entryRegexes[3], "Cleared", Colour.MINECRAFT_DARK_GREEN)
        ),

        // F5
        listOf(
            Split(entryRegexes[4], "Cleared", Colour.MINECRAFT_DARK_GREEN)
        ),

        // F6
        listOf(
            Split(entryRegexes[5], "Terracottas", Colour.MINECRAFT_LIGHT_PURPLE),
            Split(Regex("^\\[BOSS] Sadan: ENOUGH!$"), "Giants", Colour.MINECRAFT_GREEN),
            Split(Regex("^\\[BOSS] Sadan: You did it\\. I understand now, you have earned my respect\\.$"), "Sadan", Colour.MINECRAFT_RED)
        ),

        // F7
        listOf(
            Split(entryRegexes[6], "Maxor", Colour.MINECRAFT_AQUA),
            Split(Regex("\\[BOSS] Storm: Pathetic Maxor, just like expected\\."), "Storm", Colour.MINECRAFT_RED),
            Split(Regex("\\[BOSS] Goldor: Who dares trespass into my domain\\?"), "Terminals", Colour.MINECRAFT_YELLOW),
            Split(Regex("The Core entrance is opening!"), "Goldor", Colour.MINECRAFT_GOLD),
            Split(Regex("\\[BOSS] Necron: You went further than any human before, congratulations\\."), "Necron", Colour.MINECRAFT_DARK_RED),
            Split(Regex("\\[BOSS] Necron: All this, for nothing\\.\\.\\."), "Cleared", Colour.MINECRAFT_DARK_GREEN)
        )
    )
}