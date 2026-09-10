package quoi.api.skyblock.dungeon

import net.minecraft.client.player.AbstractClientPlayer
import quoi.annotations.Init
import quoi.api.events.ChatEvent
import quoi.api.events.DungeonEvent
import quoi.api.events.WorldEvent
import quoi.api.events.core.EventListener
import quoi.api.events.core.on
import quoi.api.skyblock.dungeon.enums.Phase
import quoi.api.skyblock.dungeon.enums.Stage
import quoi.utils.Shortcuts

// modified https://github.com/jcnlk/quoi/blob/26.1.x/src/main/kotlin/quoi/api/skyblock/dungeon/Floor7Utils.kt
@Init
object Floor7 : EventListener, Shortcuts {
    private var phase = Phase.Unknown
    private var stage = Stage.Unknown
    val inF7Boss: Boolean
        get() = Dungeon.inBoss && Dungeon.isFloor(7)

    init {
        on<ChatEvent.Packet> {
            if (!inF7Boss) return@on

            when (unformatted) {
                "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!" -> {
                    Stage.resetAll()
                    updateState(newPhase = Phase.P1)
                }
                "[BOSS] Storm: Pathetic Maxor, just like expected." -> {
                    updateState(newPhase = Phase.P2)
                }
                "[BOSS] Goldor: Who dares trespass into my domain?" -> {
                    Stage.resetAll()
                    Stage.S1.start()
                    updateState(newPhase = Phase.P3, newStage = Stage.S1)
                }
                // we don't use goldor's death message, since it could be dialogue skipped
                "[BOSS] Necron: Finally, I heard so much about you. The Eye likes you very much.", // first comp message
                "[BOSS] Necron: You went further than any human before, congratulations." -> {
                    updateState(newPhase = Phase.P4, newStage = Stage.Unknown)
                }
                "The Core entrance is opening!" -> {
                    updateState(newStage = Stage.S5)
                }
                "[BOSS] Necron: All this, for nothing..." -> {
                    updateState(newPhase = Phase.P5)
                }
            }

            if (phase == Phase.P3 && stage.number in 1..4) {
                val nextStage = stage.process(unformatted)

                if (nextStage != stage) {
                    updateState(newStage = nextStage)
                }
            }
        }

        on<WorldEvent.Change> {
            Stage.resetAll()
            phase = Phase.Unknown
            stage = Stage.Unknown
        }
    }

    fun getPhase(): Phase = if (inF7Boss) phase else Phase.Unknown

    @JvmOverloads
    fun getPhaseAt(player: AbstractClientPlayer? = mc.player): Phase {
        if (!inF7Boss || player == null) return Phase.Unknown

        return with(player.y) {
            when {
                this > 210 -> Phase.P1
                this > 155 -> Phase.P2
                this > 100 -> Phase.P3
                this > 45 -> Phase.P4
                else -> Phase.P5
            }
        }
    }

    fun getStage(): Stage = if (inF7Boss) stage else Stage.Unknown

    fun getStageAt(player: AbstractClientPlayer? = mc.player): Stage {
        if (!inF7Boss || player == null || getPhaseAt(player) != Phase.P3) return Stage.Unknown

        val x = player.x
        val z = player.z

        return when (x) {
            in 89.0..113.0 if z in 30.0..122.0  -> Stage.S1
            in 19.0..111.0 if z in 121.0..145.0 -> Stage.S2
            in -6.0..19.0  if z in 51.0..143.0  -> Stage.S3
            in -2.0..90.0  if z in 27.0..51.0   -> Stage.S4
            in 41.0..68.0  if z in 59.0..117.0  -> Stage.S5
            else -> Stage.Unknown
        }
    }

    fun inPhase(vararg phases: Phase): Boolean = getPhase() in phases

    fun inPhaseAt(vararg phases: Phase, player: AbstractClientPlayer? = mc.player): Boolean = player != null && getPhaseAt(player) in phases

    fun inStage(vararg stages: Stage): Boolean = getStage() in stages

    fun inStageAt(vararg stages: Stage, player: AbstractClientPlayer? = mc.player): Boolean = player != null && getStageAt(player) in stages

    private fun updateState(newPhase: Phase? = null, newStage: Stage? = null) {
        val oldPhase = phase
        val oldStage = stage

        phase = newPhase ?: phase
        stage = newStage ?: stage

        // explicitly setting the same phase/stage should still complete it, while
        // updating only one part of the state must not complete the other one
        if (newPhase != null && oldPhase != Phase.Unknown) {
            DungeonEvent.PhaseComplete(oldPhase).post()
        }

        if (newStage != null && oldStage != Stage.Unknown) {
            DungeonEvent.StageComplete.Full(oldStage).post()
        }
    }
}