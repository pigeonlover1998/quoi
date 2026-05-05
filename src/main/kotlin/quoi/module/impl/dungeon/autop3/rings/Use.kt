package quoi.module.impl.dungeon.autop3.rings

import net.minecraft.client.player.LocalPlayer
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.config.TypeName
import quoi.utils.skyblock.player.PacketOrderManager
import quoi.utils.skyblock.player.PlayerUtils.useItem
import quoi.utils.skyblock.player.SwapManager

@TypeName("use")
class UseAction(val yaw: Float = 0f, val pitch: Float = 0f, val item: String = "") : P3Action {
    override val colour get() = Colour.MAGENTA
    @Transient
    override val priority = 50
    
    override fun execute(): Boolean {
        if (item.isEmpty()) return false
        
        val swapResult = SwapManager.swapById(item)
        if (!swapResult.success && !swapResult.already) return false
        
        PacketOrderManager.register(PacketOrderManager.State.ITEM_USE) {
            mc.player?.useItem(yaw, pitch)
        }
        return true
    }
    
    override suspend fun execute(player: LocalPlayer) {
    }
    
    override fun feedbackMessage() = "Use $item ${yaw.toInt()} ${pitch.toInt()}!"
}