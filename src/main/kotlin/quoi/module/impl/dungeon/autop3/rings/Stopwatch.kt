package quoi.module.impl.dungeon.autop3.rings

import net.minecraft.client.player.LocalPlayer
import quoi.api.colour.Colour
import quoi.config.TypeName
import quoi.utils.ChatUtils.modMessage

@TypeName("stopwatch")
class StopwatchAction : P3Action {
    override val colour get() = Colour.YELLOW
    @Transient
    override val priority = 50
    
    override suspend fun execute(player: LocalPlayer) {
        val millis = StopwatchUtil.auto()
        if (millis != -1L) {
            modMessage("Elapsed: ${formatTime(millis)}")
        }
    }
    
    private fun formatTime(millis: Long): String {
        val seconds = millis / 1000.0
        return String.format("%.3fs", seconds)
    }
    
    override fun feedbackMessage() = ""
}

object StopwatchUtil {
    private var start = -1L
    
    fun start() {
        start = System.currentTimeMillis()
    }
    
    fun stop(): Long {
        if (start == -1L) return -1L
        val elapsed = System.currentTimeMillis() - start
        start = -1L
        return elapsed
    }
    
    fun auto(): Long {
        if (start == -1L) {
            start()
            return -1L
        }
        return stop()
    }
}
