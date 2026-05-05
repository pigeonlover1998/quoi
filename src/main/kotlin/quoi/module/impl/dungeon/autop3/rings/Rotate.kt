package quoi.module.impl.dungeon.autop3.rings

import net.minecraft.client.player.LocalPlayer
import quoi.api.colour.Colour
import quoi.config.TypeName
import quoi.utils.rotation.ClientRotationHandler

@TypeName("rotate")
class RotateAction(val yaw: Float = 0f, val pitch: Float = 0f) : P3Action {
    override val colour get() = Colour.YELLOW
    @Transient
    override val priority = 50
    override suspend fun execute(player: LocalPlayer) {
        ClientRotationHandler.setYaw(yaw)
        ClientRotationHandler.setPitch(pitch)
    }
    override fun feedbackMessage() = "Rotate ${yaw.toInt()} ${pitch.toInt()}!"
}
