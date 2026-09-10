package quoi.api.skyblock.dungeon.enums

import quoi.api.events.DungeonEvent
import quoi.api.events.core.EventDispatcher
import quoi.api.skyblock.dungeon.Dungeon

// https://github.com/jcnlk/quoi/blob/546fa869582965e8385b4950b8fb08aa8953f23c/src/main/kotlin/quoi/api/skyblock/dungeon/Floor7Utils.kt#L132
// https://github.com/pigeonlover1998/quoi/blob/a55c960d17f136d9b67a252d5e892ad9f2e99a78/src/main/kotlin/quoi/api/skyblock/dungeon/DungeonEnums.kt#L206
enum class Stage(val number: Int, val reqTerminals: Int) {
    Unknown(0, 0), S1(1, 4), S2(2, 5), S3(3, 4), S4(4, 4), S5(5, 0);

    var terminals = 0
        private set

    var levers = 0
        private set

    var device = false
        private set

    private var _gate = false

    val gate: Boolean
        get() = this == S4 || _gate

    var startTime = 0L
        private set

    var startTicks = 0
        private set

    var endTime = 0L
        private set

    var endTicks = 0
        private set

    private var current = 0
    private var total = 0

    val objectivesCompleted: Boolean
        get() = total > 0 && current == total

    fun process(message: String): Stage {
        REGEX_TERM_COMPLETED.find(message)?.destructured?.let { (playerName, _, type, currentStr, totalStr) ->
            current = currentStr.toIntOrNull() ?: 0
            total = totalStr.toIntOrNull() ?: 0

            val dungeonPlayer = Dungeon.dungeonTeammates.find {
                it.name.equals(playerName, ignoreCase = true)
            }

            when (type) {
                "terminal" -> {
                    terminals++
                    dungeonPlayer?.p3Stats?.let { it.terminals++ }
                }

                "lever" -> {
                    levers++
                    dungeonPlayer?.p3Stats?.let { it.levers++ }
                }

                "device" -> {
                    device = true
                    dungeonPlayer?.p3Stats?.let { it.devices++ }
                }
            }

            if (objectivesCompleted) {
                DungeonEvent.StageComplete(this).post()
            }
        }

        if (message == "The gate has been destroyed!") {
            _gate = true
        }

        if (!objectivesCompleted || !gate || endTime != 0L) {
            return this
        }

        endTime = System.currentTimeMillis()
        endTicks = EventDispatcher.totalTicks

        val next = when (this) {
            S1 -> S2
            S2 -> S3
            S3 -> S4

            // S5 starts on core opening
            S4, S5, Unknown -> return this
        }

        next.start()
        return next
    }

    fun getDuration(): Long {
        if (startTime == 0L) return 0L
        if (endTime != 0L) return endTime - startTime
        return System.currentTimeMillis() - startTime
    }

    fun getDurationTicks(): Long {
        if (startTicks == 0) return 0L
        if (endTicks != 0) {
            return (endTicks - startTicks).toLong()
        }

        return (EventDispatcher.totalTicks - startTicks).toLong()
    }

    fun start() {
        startTime = System.currentTimeMillis()
        startTicks = EventDispatcher.totalTicks
    }

    fun reset() {
        current = 0
        total = 0

        terminals = 0
        levers = 0
        device = false
        _gate = false

        startTime = 0L
        startTicks = 0

        endTime = 0L
        endTicks = 0
    }

    companion object {
        private val REGEX_TERM_COMPLETED = Regex("^(.{1,16}) (activated|completed) a (terminal|lever|device)! \\((\\d)/(\\d)\\)$")

        fun resetAll() {
            entries.forEach(Stage::reset)
            Dungeon.dungeonTeammates.forEach { it.p3Stats.reset() }
        }
    }
}