package quoi.api.autoroutes.actions

import quoi.QuoiMod.mc
import quoi.api.autoroutes.arguments.AwaitArgument
import quoi.api.skyblock.dungeon.Dungeon.currentRoom
import quoi.config.TypeName
import quoi.module.impl.dungeon.AutoRoutes
import quoi.utils.ChatUtils.modMessage
import quoi.utils.Scheduler.wait
import quoi.utils.skyblock.player.PlayerUtils
import quoi.utils.skyblock.player.PlayerUtils.rotate
import quoi.utils.skyblock.player.SwapManager
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundUseItemPacket
import net.minecraft.world.InteractionHand

@TypeName("etherwarp")
class EtherwarpAction(val yaw: Float = 0f, val pitch: Float = 0f) : RingAction {
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
        if (AutoRoutes.zeroTick) {
            val rings = AutoRoutes.routes[room.name] ?: return

            val index = rings.indexOfFirst { it.action === this }
            if (index == -1) return

            for (i in index until rings.size) {
                val ring = rings[i]
                val action = ring.action

                if (action !is EtherwarpAction) break

                if (i > index) {
                    if (ring.arguments?.any { it is AwaitArgument } == true || !ring.checkArgs()) {
                        break
                    }
                }

                mc.connection!!.send(
                    ServerboundUseItemPacket(
                        InteractionHand.MAIN_HAND,
                        0,
                        room.getRealYaw(action.yaw),
                        action.pitch
                    )
                )

                if (i > index) {
                    AutoRoutes.visitedRings.add(ring)
                }
            }

        } else {
            player.rotate(room.getRealYaw(yaw), pitch)
//            wait(1)
            PlayerUtils.interact()
        }
    }
}