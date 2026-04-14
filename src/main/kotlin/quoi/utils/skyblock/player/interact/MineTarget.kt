package quoi.utils.skyblock.player.interact

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import quoi.QuoiMod.mc
import quoi.utils.WorldUtils.state
import quoi.utils.eyePosition
import quoi.utils.getHitResult
import quoi.utils.startPrediction

class MineTarget(
    val pos: BlockPos,
    private var direction: Direction,
    val custom: Boolean,
    private val swing: Boolean
) {
    var progress = 0f

    private var started = false
    var finished = false
        private set
    private var doStop = false

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
        } else {
            if (!custom) {
                progress += state.getDestroyProgress(player, level, pos)
            }

            level.destroyBlockProgress(player.id, pos, (progress * 10).toInt())

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