package quoi.utils.skyblock.player

import net.minecraft.network.protocol.Packet
import quoi.annotations.Init
import quoi.api.events.PacketEvent
import quoi.api.events.TickEvent
import quoi.api.events.core.EventBus.on
import quoi.api.events.core.Priority
import java.util.concurrent.ConcurrentHashMap

@Init
object PacketOrderManager {
    private val packets = ConcurrentHashMap<State, MutableList<StateRunnable>>()
    private val receiveListeners = mutableListOf<(Packet<*>) -> Boolean>()

    enum class State {
        START,
        ITEM_USE,
        ATTACK
    }

    data class StateRunnable(
        val canMultiRun: Boolean,
        val runnable: () -> Unit
    )

    init {
        on<TickEvent.Start>(Priority.HIGHEST) {
            execute(State.START)
        }

        on<PacketEvent.Received>(Priority.HIGHEST) {
            synchronized(receiveListeners) {
                if (receiveListeners.isEmpty()) return@on
                receiveListeners.removeIf { it(packet) }
            }
        }
    }

    fun register(state: State, runnable: () -> Unit) {
        register(state, StateRunnable(canMultiRun = true, runnable))
    }

    fun register(state: State, stateRunnable: StateRunnable) {
        synchronized(packets) {
            packets.putIfAbsent(state, mutableListOf())
        }

        val list = packets[state]!!
        synchronized(list) {
            list.add(stateRunnable)
        }
    }

    fun registerReceiveListener(listener: (Packet<*>) -> Boolean) {
        synchronized(receiveListeners) {
            receiveListeners.add(listener)
        }
    }

    fun execute(state: State) {
        val runnables = packets[state] ?: return

        synchronized(runnables) {
            if (runnables.isEmpty()) return

            val iterator = runnables.iterator()
            var isFirst = true
            while (iterator.hasNext()) {
                val r = iterator.next()
                if (isFirst || r.canMultiRun) {
                    r.runnable()
                    iterator.remove()
                }
                isFirst = false
            }
        }
    }
}
