package quoi.api.autoroutes.actions

import net.minecraft.client.player.LocalPlayer
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.config.TypeName
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.player.PlayerUtils.leftClick
import quoi.utils.skyblock.player.RotationUtils.rotate
import quoi.utils.skyblock.player.SwapManager

@TypeName("boom")
class BoomAction(val yaw: Float = 0f, val pitch: Float = 0f) : RingAction {
    override val colour: Colour
        get() = Colour.RED

    override suspend fun execute(player: LocalPlayer) {
        if (!SwapManager.swapById("INFINITE_SUPERBOOM_TNT", "SUPERBOOM_TNT").success) {
            modMessage("boom not found fucking retard")
            return
        }
        player.rotate(currentRoom!!.getRealYaw(yaw), pitch)
//        wait(1)
        player.leftClick()
    }
}