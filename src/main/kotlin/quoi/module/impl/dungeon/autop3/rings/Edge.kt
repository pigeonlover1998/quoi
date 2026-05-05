package quoi.module.impl.dungeon.autop3.rings

import net.minecraft.client.player.LocalPlayer
import quoi.api.colour.Colour
import quoi.config.TypeName
import quoi.module.impl.dungeon.autop3.AutoP3

@TypeName("edge")
class EdgeAction : P3Action {
    override val colour get() = Colour.BLACK
    @Transient
    override val priority = 60
    override suspend fun execute(player: LocalPlayer) {
        AutoP3.edgeActive = true
    }
    override fun feedbackMessage() = "Edge!"
}
