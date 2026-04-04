package quoi.api.autoroutes.actions

import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.config.TypeName
import quoi.utils.skyblock.player.RotationUtils.rotate
import net.minecraft.client.player.LocalPlayer
import quoi.api.colour.Colour

@TypeName("rotate")
class RotateAction(val yaw: Float = 0f, val pitch: Float = 0f) : RingAction {
    override val colour: Colour
        get() = Colour.YELLOW

    override suspend fun execute(player: LocalPlayer) {
        player.rotate(currentRoom!!.getRealYaw(yaw), pitch)
    }
}