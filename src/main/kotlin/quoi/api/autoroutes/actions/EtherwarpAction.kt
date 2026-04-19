package quoi.api.autoroutes.actions

import kotlinx.coroutines.withTimeoutOrNull
import quoi.QuoiMod.mc
import quoi.api.autoroutes.arguments.AwaitArgument
import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.config.TypeName
import quoi.module.impl.dungeon.AutoRoutes
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.wait
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.RotationUtils.rotate
import quoi.utils.skyblock.player.SwapManager
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.Vec3
import quoi.api.colour.Colour
import quoi.utils.rayCast

@TypeName("etherwarp")
class EtherwarpAction(val yaw: Float = 0f, val pitch: Float = 0f, val vec: Vec3? = null) : RingAction {

    override val colour: Colour
        get() = Colour.CYAN

    override suspend fun execute(player: LocalPlayer) {
        val room = currentRoom ?: return
        if (!mc.options.keyShift.isDown) {
            mc.options.keyShift.isDown = true
            wait(2)
        }
        if (!SwapManager.swapById("ASPECT_OF_THE_VOID", "ASPECT_OF_THE_END").success) {
            modMessage("Aotv not found retard")
            return
        }
        player.rotate(room.getRealYaw(yaw), pitch)

        AutoRoutes.interactDelay()
        withTimeoutOrNull(500) {
            while (rayCast() == null) {
                wait(1)
            }
        }
        PlayerUtils.interact()
    }
}