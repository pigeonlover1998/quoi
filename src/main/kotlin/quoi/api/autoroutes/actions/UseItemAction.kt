package quoi.api.autoroutes.actions

import net.minecraft.client.player.LocalPlayer
import quoi.QuoiMod.mc
import quoi.api.colour.Colour
import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.config.TypeName
import quoi.module.impl.dungeon.AutoRoutes
import quoi.utils.ChatUtils.modMessage
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.RotationUtils.pitch
import quoi.utils.skyblock.player.RotationUtils.rotate
import quoi.utils.skyblock.player.RotationUtils.yaw
import quoi.utils.skyblock.player.SwapManager

@TypeName("use_item")
class UseItemAction(
    val yaw: Float = mc.player?.yaw ?: 0f,
    val pitch: Float = mc.player?.pitch ?: 0f,
    val itemName: String = "",
    val times: Int? = null,
) : RingAction {

    @Transient
    private val regex = Regex("[_\\-\\s]")

    override val colour: Colour
        get() = Colour.BROWN

    override suspend fun execute(player: LocalPlayer) {
        val item = getName(player)

        if (item == null) {
            modMessage("imbecile you don't have $itemName in your hotbar")
            return
        }

        if (SwapManager.swapByName(item).success) {
            player.rotate(currentRoom!!.getRealYaw(yaw), pitch)
            AutoRoutes.interactDelay()
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