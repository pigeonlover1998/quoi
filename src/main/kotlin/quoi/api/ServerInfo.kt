package quoi.api

import quoi.QuoiMod.mc
import quoi.api.events.PacketEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventBus
import quoi.utils.Scheduler.scheduleLoop
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import net.minecraft.Util
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentSkipListSet

// modified https://github.com/Synnerz/devonian/blob/1.21.10/src/main/kotlin/com/github/synnerz/devonian/api/Ping.kt
object ServerInfo {
    private var lastBeat = 0L

    private val samples = ConcurrentLinkedQueue<PingSample>()
    private var pingSum = atomic(0.0)
    private var weightSum = atomic(0)
    private var medianMax = ConcurrentSkipListSet<PingSample> { a, b -> b.value.compareTo(a.value).let { if (it == 0) a.time.compareTo(b.time) else it } }
    private var medianMin = ConcurrentSkipListSet<PingSample> { a, b -> a.value.compareTo(b.value).let { if (it == 0) a.time.compareTo(b.time) else it } }

    val currentPing: Double get() = samples.lastOrNull()?.value ?: 0.0
    val averagePing: Double get() = if (weightSum.value == 0) 0.0 else pingSum.value / weightSum.value
    val medianPing: Double get() = when {
        medianMax.size > medianMin.size -> medianMax.first.value
        medianMin.size > medianMax.size -> medianMin.first.value
        medianMax.isEmpty() -> 0.0
        else -> 0.5 * (medianMax.first.value + medianMin.first.value)
    }

    private var lastTickTime = System.currentTimeMillis()
    private val tickTimes = ArrayDeque<Long>()

    var currentTps: Float = 20.0f
        private set
    var averageTps: Float = 20.0f
        private set
    var minTps: Float = 20.0f
        private set
    var maxTps: Float = 20.0f
        private set

    init {
        //
        // TPS
        //
        scheduleLoop(server = true) {
            val now = System.currentTimeMillis()
            val dt = now - lastTickTime
            lastTickTime = now

            tickTimes.addLast(dt)
            if (tickTimes.size > 100) tickTimes.removeFirst()

            val total = tickTimes.sum()
            averageTps = if (total > 0) (tickTimes.size * 1000f / total).coerceAtMost(20f) else 0f

            val tpsValues = tickTimes.map { (1000f / it).coerceAtMost(20f) }
            currentTps = tpsValues.last()
            minTps = tpsValues.minOrNull() ?: 20f
            maxTps = tpsValues.maxOrNull() ?: 20f
        }

        EventBus.on<WorldEvent.Change> {
            tickTimes.clear()
            lastTickTime = System.currentTimeMillis()
        }

        //
        // PING
        //
        EventBus.on<PacketEvent.Sent> {
            if (packet is ServerboundPingRequestPacket) lastBeat = System.currentTimeMillis()
        }

        EventBus.on<PacketEvent.Received> {
            if (packet is ClientboundPongResponsePacket) {
                val t = System.currentTimeMillis()
                val a = (System.nanoTime() - packet.time) * 1e-6
                val b = (t - packet.time).toDouble()
                val c = (Util.getMillis() - packet.time).toDouble()

                val p = listOf(a, b, c).filter { it >= 0.0 }.minOrNull() ?: return@on
                addSample(p, 1, t)
            }
        }

        scheduleLoop {
            val t = System.currentTimeMillis()
            var deltaPing = 0.0
            var deltaWeight = 0

            while (samples.isNotEmpty() && samples.peek().time < t - 30_000L) {
                val sample = samples.poll()
                if (sample != null) {
                    if (!medianMax.remove(sample)) medianMin.remove(sample)
                    deltaPing += sample.value * sample.weight
                    deltaWeight += sample.weight
                }
            }

            if (deltaWeight > 0) {
                pingSum.update { it - deltaPing }
                weightSum.minusAssign(deltaWeight)
                rebalanceHeaps()
            }

            if (t - lastBeat > 2_000L) mc.connection?.send(ServerboundPingRequestPacket(System.nanoTime()))
        }
    }

    private fun rebalanceHeaps() {
        while (medianMax.size - medianMin.size > 1) {
            val s = medianMax.pollFirst() ?: break
            medianMin.add(s)
        }
        while (medianMin.size - medianMax.size > 1) {
            val s = medianMin.pollFirst() ?: break
            medianMax.add(s)
        }
    }

    fun addSample(ping: Double, weight: Int, t: Long) {
        if (ping > 1.0e6) return
        val sample = PingSample(t, ping, weight)

        pingSum.update { it + ping * weight }
        weightSum.plusAssign(weight)
        samples.add(sample)

        if (ping > medianPing) medianMin.add(sample)
        else medianMax.add(sample)
        rebalanceHeaps()
    }

    data class PingSample(val time: Long, val value: Double, val weight: Int)
}