package quoi.utils.skyblock.player.simulation

import quoi.annotations.Init
import quoi.api.events.KeyEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.Priority
import quoi.api.events.core.on
import quoi.api.input.MutableInput
import quoi.utils.player

/**
 * modified LiquidBounce (GPL-3.0)
 * copyright (c) 2015-2026 CCBlueX
 * original: https://github.com/CCBlueX/LiquidBounce/blob/0f34808bf6954ff6126dde353ff9e896eb4a2ead/src/main/kotlin/net/ccbluex/liquidbounce/utils/entity/PlayerSimulationCache.kt#L38
 */
@Init
object PlayerSimulation : EventListener {
    private var playerCache: SimulatedCache? = null

    init {
        on<TickEvent.Start>(Priority.HIGHEST) {
            playerCache = null
        }

        on<KeyEvent.Input>(Priority.HIGH) {
            playerCache = null
            updateCache(input)
        }

        on<KeyEvent.Input> {
            updateCache(input, true)
        }

        on<KeyEvent.Input>(Priority.MEDIUM) {
            updateCache(input, true)
        }
    }

    private fun updateCache(input: MutableInput, verify: Boolean = false) {
        if (verify && playerCache?.simulatedPlayer?.input == input) return
        val simulatedPlayer = SimulatedPlayer.fromPlayer(input)
        playerCache = SimulatedCache(simulatedPlayer)
    }

    val simulation: SimulatedCache
        get() {
            val cached = playerCache
            if (cached != null) {
                return cached
            }

            val simulatedPlayer = SimulatedPlayer.fromPlayer(MutableInput(player.input.keyPresses))
            val cache = SimulatedCache(simulatedPlayer)
            playerCache = cache
            return cache
        }
}