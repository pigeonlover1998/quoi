package quoi.module.impl.dungeon.autop3.rings

import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket
import net.minecraft.world.InteractionHand
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.config.TypeName
import quoi.utils.equalsOneOf
import quoi.utils.getEtherPos
import quoi.utils.getEyeHeight
import quoi.utils.skyblock.ItemUtils.skyblockId
import quoi.utils.skyblock.player.SwapManager
import quoi.utils.startPrediction

@TypeName("boom")
class BoomAction(val yaw: Float = 0f, val pitch: Float = 0f) : P3Action {
    override val colour get() = Colour.RED
    @Transient
    override val priority = 60
    
    override suspend fun execute(player: LocalPlayer) {
        val eyePos = player.position().add(0.0, getEyeHeight(true).toDouble(), 0.0)
        val block = eyePos.getEtherPos(yaw, pitch, 6.0).pos ?: return
        
        if (!player.mainHandItem.skyblockId.equalsOneOf("INFINITE_SUPERBOOM_TNT", "SUPERBOOM_TNT")) {
            SwapManager.swapById("INFINITE_SUPERBOOM_TNT", "SUPERBOOM_TNT")
            return
        }
        
        mc.gameMode?.startPrediction { sequence ->
            ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                block,
                Direction.UP,
                sequence
            )
        }
        player.swing(InteractionHand.MAIN_HAND)
    }
    
    override fun feedbackMessage() = "Boom ${yaw.toInt()} ${pitch.toInt()}!"
}
