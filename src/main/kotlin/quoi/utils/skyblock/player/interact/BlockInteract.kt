package quoi.utils.skyblock.player.interact

import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket
import net.minecraft.world.InteractionHand
import quoi.QuoiMod.mc
import quoi.utils.getHitResult
import quoi.utils.skyblock.player.interact.AuraManager.debugBox
import quoi.utils.startPrediction

class BlockInteract(private val pos: BlockPos, private val force: Boolean) {
    fun execute() {
        val hitResult = pos.getHitResult(force) ?: return

        mc.gameMode?.startPrediction { sequence ->
            ServerboundUseItemOnPacket(
                InteractionHand.MAIN_HAND,
                hitResult,
                sequence
            )
        }

        debugBox(hitResult.location)
    }
}