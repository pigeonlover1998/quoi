package quoi.utils.skyblock.player.interact

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import quoi.QuoiMod.mc
import quoi.annotations.Internal
import quoi.utils.WorldUtils.state
import quoi.utils.skyblock.player.PlayerUtils.eyePosition
import quoi.utils.getHitResult
import quoi.utils.startPrediction
import kotlin.math.ceil

@OptIn(Internal::class)
class MineTarget(
    val pos: BlockPos,
    private var direction: Direction,
    val custom: Boolean,
    private val swing: Boolean
) {
    private var started = false
    var finished = false
        private set
    private var doStop = false


    private var ticksMined = 0
    private var customDelta = -1f
    private var lastProgress = 0f

    var progress = 0f
        @Internal set(value) {
            if (custom) {
                val diff = value - lastProgress
                if (diff > 0 && ticksMined > 0) {
                    customDelta = value / ticksMined
                }
                lastProgress = value
            }
            field = value
        }

    val ticksRemaining: Int // todo maybe make actual calc based on mining speed for custom
        get() {
            if (finished || progress >= 1f) return 0

            val delta = if (custom) {
                if (customDelta <= 0f) return -1
                customDelta
            } else {
                val player = mc.player ?: return -1
                val level = mc.level ?: return -1
                pos.state.getDestroyProgress(player, level, pos)
            }

            if (delta <= 0f) return -1

            return ceil((1f - progress) / delta).toInt()
        }

    private var item: ItemStack = ItemStack.EMPTY

    private fun start() {
        mc.gameMode?.startPrediction { sequence ->
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                pos,
                direction,
                sequence,
            )
        }
        if (swing) mc.player?.swing(InteractionHand.MAIN_HAND)

        started = true
    }

    private fun stop() {
        mc.gameMode?.startPrediction { sequence ->
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                pos,
                direction,
                sequence,
            )
        }
        if (swing) mc.player?.swing(InteractionHand.MAIN_HAND)
        finished = true
    }

    private fun abort() {
        if (!finished && started) {
            mc.connection!!.send(
                ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    pos,
                    Direction.DOWN,
                )
            )
        }
        finished = true
    }

    fun onTick(cd: Int) {
        if (finished) return

        if (started) ticksMined++

        val level = mc.level ?: return
        val player = mc.player ?: return

        val hitResult = pos.getHitResult() ?: return abort()
        if (player.eyePosition().distanceToSqr(hitResult.location) > 20.25) return abort()

        direction = hitResult.direction

        val state = pos.state
        if (state.isAir) {
            finished = true
            return
        }

        if (!started) {
            if (cd <= 0) start()
            else if (swing) player.swing(InteractionHand.MAIN_HAND)
        } else {
            if (!custom) {
                progress += state.getDestroyProgress(player, level, pos)
                level.destroyBlockProgress(player.id, pos, (progress * 10).toInt())
            }

            level.addBreakingBlockEffect(pos, direction)

            if (swing) player.swing(InteractionHand.MAIN_HAND)

            if (progress >= 1.0f) {
                if (doStop) stop()
                else doStop = true
            }
        }
    }

    fun onBlockUpdate(pos: BlockPos, state: BlockState) =
        mc.execute { if (pos == this.pos && state.isAir) finished = true }


    fun onSlotChange(stack: ItemStack) {
        if (ItemStack.matches(item, stack)) return

        if (!finished && started) {
            mc.connection?.send(
                ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    pos,
                    Direction.DOWN,
                )
            )
            started = false
            progress = 0f
            doStop = false
        }
    }
}