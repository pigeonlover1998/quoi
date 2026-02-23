package quoi.api.autoroutes.actions

import quoi.QuoiMod.mc
import quoi.config.TypeName
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.wait
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.PlayerUtils.pitch
import quoi.utils.skyblock.player.PlayerUtils.rotate
import quoi.utils.skyblock.player.PlayerUtils.yaw
import quoi.utils.skyblock.player.SwapManager
import net.minecraft.client.player.LocalPlayer

@TypeName("use_item")
class UseItemAction(
    val yaw: Float = mc.player?.yaw ?: 0f,
    val pitch: Float = mc.player?.pitch ?: 0f,
    val itemName: String = "",
    val times: Int? = null,
) : RingAction {

    @Transient
    private val regex = Regex("[_\\-\\s]")

    override suspend fun execute(player: LocalPlayer) {
        val item = getName(player)

        if (item == null) {
            modMessage("imbecile you don't have $itemName in your hotbar")
            return
        }

        if (SwapManager.swapByName(item).success) {
            player.rotate(yaw, pitch)
            wait(1)
            repeat(times ?: 1) {
                PlayerUtils.interact()
            }
        }
    }

    private fun getName(player: LocalPlayer): String? {
        val cleaned = itemName.replace(regex, "")
        return player.inventory
            .take(9)
            .filter { !it.isEmpty }
            .mapNotNull { it.displayName.string }
            .firstOrNull { it.replace(regex, "").contains(cleaned, ignoreCase = true) }
    }
}