package quoi.module.impl.dungeon.autop3.rings

import net.minecraft.client.player.LocalPlayer
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.config.TypeName

@TypeName("chat")
class ChatAction(val message: String = "") : P3Action {
    override val colour get() = Colour.YELLOW
    @Transient
    override val priority = 50
    
    override fun execute(): Boolean {
        mc.connection?.sendChat(message)
        return true
    }
    
    override suspend fun execute(player: LocalPlayer) {
    }
    
    override fun feedbackMessage() = "Chat: $message"
}
