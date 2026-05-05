package quoi.utils.skyblock.player

import net.minecraft.network.protocol.Packet
import java.util.concurrent.ConcurrentHashMap

object PacketOrderManager {
    private val packets = ConcurrentHashMap<State, MutableList<StateRunnable>>()
    private val receiveListeners = mutableListOf<(Packet<*>) -> Boolean>()

    enum class State {
        START,
        ITEM_USE,
        ATTACK
    }

    fun onPreTickStart() {
        execute(State.START)
    }

    fun register(state: State, runnable: Runnable) {
        register(state, StateRunnable(true, runnable))
    }

    fun register(state: State, runnable: StateRunnable) {
        synchronized(packets) {
            if (!packets.containsKey(state)) packets[state] = mutableListOf()
        }

        val list = packets[state]!!
        synchronized(list) {
            list.add(runnable)
        }
    }

    fun registerReceiveListener(listener: (Packet<*>) -> Boolean) {
        synchronized(receiveListeners) {
            receiveListeners.add(listener)
        }
    }

    fun onPreReceivePacket(packet: Packet<*>) {
        synchronized(receiveListeners) {
            if (receiveListeners.isEmpty()) return
            receiveListeners.removeIf { it(packet) }
        }
    }

    fun execute(state: State) {
        if (!packets.containsKey(state)) return

        val runnables = packets[state]!!
        synchronized(runnables) {
            if (runnables.isEmpty()) return
            var i = 0
            while (i < runnables.size) {
                val r = runnables[i]
                val bl2 = i == 0 || r.canMultiRun
                if (!bl2) {
                    i++
                    continue
                }
                r.runnable.run()
                runnables.removeAt(i)
            }
        }
    }

    data class StateRunnable(val canMultiRun: Boolean, val runnable: Runnable)
}
