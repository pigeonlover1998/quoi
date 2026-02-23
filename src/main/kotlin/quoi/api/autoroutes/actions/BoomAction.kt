package quoi.api.autoroutes.actions

import quoi.config.TypeName
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.wait
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.PlayerUtils.rotate
import quoi.utils.skyblock.player.SwapManager
import net.minecraft.client.player.LocalPlayer

@TypeName("boom")
class BoomAction(val yaw: Float = 0f, val pitch: Float = 0f) : RingAction {
    override suspend fun execute(player: LocalPlayer) {
        val initial = player.inventory.selectedSlot
        if (!SwapManager.swapById("INFINITE_SUPERBOOM_TNT", "SUPERBOOM_TNT").success) {
            modMessage("boom not found fucking retard")
            return
        }
        player.rotate(yaw, pitch)
        wait(1)
        PlayerUtils.leftClick()
//        wait(1)
//        SwapManager.swapToSlot(initial)
    }
}