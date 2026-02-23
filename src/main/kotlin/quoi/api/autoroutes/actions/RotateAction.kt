package quoi.api.autoroutes.actions

import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.config.TypeName
import quoi.utils.skyblock.player.PlayerUtils.rotate
import net.minecraft.client.player.LocalPlayer

@TypeName("rotate")
class RotateAction(val yaw: Float = 0f, val pitch: Float = 0f) : RingAction {
    override suspend fun execute(player: LocalPlayer) {
        player.rotate(currentRoom!!.getRealYaw(yaw), pitch)
    }
}