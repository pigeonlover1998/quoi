package quoi.utils.skyblock.player.simulation

/**
 * modified LiquidBounce (GPL-3.0)
 * copyright (c) 2015-2026 CCBlueX
 * original: https://github.com/CCBlueX/LiquidBounce/blob/0f34808bf6954ff6126dde353ff9e896eb4a2ead/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerSimulationCache.kt#L117
 */
class SimulatedCache(val simulatedPlayer: SimulatedPlayer) {
    private var currentSimulationStep = 0
    private val simulationSteps = ArrayList<SimulatedSnapshot>().apply {
        add(SimulatedSnapshot(simulatedPlayer))
    }

    fun simulateUntil(ticks: Int) {
        check(ticks >= 0) { "ticks may not be negative" }

        if (currentSimulationStep >= ticks) {
            return
        }

        while (currentSimulationStep < ticks) {
            simulatedPlayer.tick()
            simulationSteps.add(SimulatedSnapshot(simulatedPlayer))
            this.currentSimulationStep++
        }
    }

    fun getSnapshotAt(ticks: Int): SimulatedSnapshot {
        simulateUntil(ticks)
        return simulationSteps[ticks]
    }

    fun getSnapshotAt(ticks: Double): SimulatedSnapshot {
        val tick = ticks.toInt()
        val partialTick = ticks - tick
        return getSnapshotAt(tick, partialTick)
    }

    fun getSnapshotAt(ticks: Int, partialTicks: Double): SimulatedSnapshot {
        val snapshot = getSnapshotAt(ticks)
        if (partialTicks <= 0.0) return snapshot

        val nextSnapshot = getSnapshotAt(ticks + 1)

        val interpolatedPos = snapshot.pos.add(
            nextSnapshot.pos.subtract(snapshot.pos).scale(partialTicks)
        )

        return SimulatedSnapshot(
            pos = interpolatedPos,
            fallDistance = snapshot.fallDistance,
            velocity = snapshot.velocity,
            onGround = snapshot.onGround
        )
    }

    fun getSnapshotsBetween(tickRange: IntRange): List<SimulatedSnapshot> {
        check(tickRange.first >= 0) { "start tick may not be negative" }
        check(tickRange.last < 60 * 20) { "tried to simulate a player for more than a minute!" }

        simulateUntil(tickRange.last + 1)

        return ArrayList(simulationSteps.subList(tickRange.first, tickRange.last + 1))
    }

    fun getSnapshotsBetween(tickRange: ClosedFloatingPointRange<Double>, step: Double = 1.0): List<SimulatedSnapshot> {
        require(step > 0.0) { "ticks may not be negative" }
        check(tickRange.start >= 0.0) { "start tick may not be negative" }
        check(tickRange.endInclusive < 60 * 20) { "tried to simulate a player for more than a minute!" }

        simulateUntil(tickRange.endInclusive.toInt() + 2)

        val snapshots = ArrayList<SimulatedSnapshot>()
        val totalSteps = ((tickRange.endInclusive - tickRange.start) / step).toInt()

        for (i in 0..totalSteps) {
            val currentTick = tickRange.start + i * step
            if (currentTick <= tickRange.endInclusive + 1e-9) {
                snapshots.add(getSnapshotAt(currentTick))
            }
        }

        return snapshots
    }
}