package quoi.module.impl.dungeon.autop3.rings

import net.minecraft.client.player.LocalPlayer
import quoi.api.colour.Colour
import quoi.config.TypeName
import quoi.utils.skyblock.player.SwapManager

@TypeName("swap")
class SwapAction(val item: String = "") : P3Action {
    override val colour get() = Colour.ORANGE
    @Transient
    override val priority = 50
    override suspend fun execute(player: LocalPlayer) {
        if (item.isNotEmpty()) {
            SwapManager.swapById(item)
        }
    }
    override fun feedbackMessage() = "Swapping to $item!"
}
