package quoi.module.impl.dungeon.autop3.rings

import net.minecraft.client.player.LocalPlayer
import quoi.api.colour.Colour
import quoi.config.TypeName

@TypeName("jump")
class JumpAction : P3Action {
    override val colour get() = Colour.GREEN
    @Transient
    override val priority = 60
    override suspend fun execute(player: LocalPlayer) {
        player.jumpFromGround()
    }
    override fun feedbackMessage() = "Jump!"
}
